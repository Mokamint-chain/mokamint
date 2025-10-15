/*
Copyright 2025 Fausto Spoto

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package io.mokamint.miner.service.internal;

import java.math.BigInteger;
import java.net.URI;
import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.closeables.AbstractAutoCloseableWithLockAndOnCloseHandlers;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.mokamint.miner.api.ClosedMinerException;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.service.api.MinerService;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;

/**
 * Implementation of a client that connects to a remote miner.
 * It is an adapter of a miner into a web service client.
 * It tries to keep the connection active, by keeping an internal service
 * that gets recreated whenever its connection seems lost.
 */
public class ReconnectingMinerServiceImpl extends AbstractAutoCloseableWithLockAndOnCloseHandlers<ClosedMinerException> implements MinerService {
	
	/**
	 * The adapted miner. This might be missing, in which case the service is just a proxy for calling the
	 * methods of the remote miner, but won't provide any deadline to that remote.
	 */
	private final Optional<Miner> miner;

	/**
	 * The websockets URI of the remote miner.
	 */
	private final URI uri;

	/**
	 * The time (in milliseconds) allowed for a call to the remote miner;
	 * beyond that threshold, a timeout exception is thrown.
	 */
	private final int timeout;

	/**
	 * A thread that tries to keep the connection active.
	 */
	private final Reconnector reconnector;

	/**
	 * Used to wait until this service gets closed.
	 */
	private final CountDownLatch closedLatch = new CountDownLatch(1);

	/**
	 * The time, in milliseconds, between a failed connection attempt and the subsequent one.
	 */
	private final int retryInterval;

	/**
	 * The time after which a silent connection is considered as inactive.
	 */
	// TODO: this should be derived from the mining specification of the remote miner in the future
	private final static long INACTIVITY_THRESHOLD = 180_000L;

	/**
	 * The prefix used in the log messages;
	 */
	private final String logPrefix;

	private final static Logger LOGGER = Logger.getLogger(ReconnectingMinerServiceImpl.class.getName());

	/**
	 * Creates a miner service by adapting the given miner.
	 * 
	 * @param miner the adapted miner; if this is missing, the service won't provide deadlines but
	 *              will anyway connect to the remote miner
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @param timeout the time (in milliseconds) allowed for a call to the remote miner;
	 *                beyond that threshold, a timeout exception is thrown
	 * @param retryInterval the time. in milliseconds, between a failed connection attempt and the subsequent one
	 */
	public ReconnectingMinerServiceImpl(Optional<Miner> miner, URI uri, int timeout, int retryInterval) {
		super(ClosedMinerException::new);

		this.miner = miner;
		this.uri = uri;
		this.timeout = timeout;
		this.retryInterval = retryInterval;
		this.logPrefix = "reconnecting miner service working for " + uri + ": ";
		this.reconnector = new Reconnector();
		reconnector.start();
	}

	/**
	 * A thread that checks if the connection has been silent for too long:
	 * after that threshold, it tries to reconnect to the remote miner.
	 */
	private class Reconnector extends Thread {

		/**
		 * The last service that has been connected: this is missing at the beginning.
		 */
		private volatile MinerService service;

		/**
		 * The last moment when {@code service} has shown activity> milliseconds from Unix epoch.
		 */
		private final AtomicLong lastContactTime = new AtomicLong(0L);

		/**
		 * True if and only if the service seems currently connected.
		 */
		private volatile AtomicBoolean connected = new AtomicBoolean(false);

		@Override
		public void run() {
			try {
				while (true) {
					long millisecondsSinceLastContact;

					// this thread sleeps as long as the connected service seems active;
					// note that lastContactTime gets updated at each activity sign from the service
					do {
						millisecondsSinceLastContact = System.currentTimeMillis() - lastContactTime.get();
						if (millisecondsSinceLastContact < INACTIVITY_THRESHOLD)
							Thread.sleep(INACTIVITY_THRESHOLD - millisecondsSinceLastContact);
					}
					while (millisecondsSinceLastContact < INACTIVITY_THRESHOLD);

					// the service seems inactive (or it has not been created up to now): we try to recreate it
					try {
						if (connected.getAndSet(false))
							onDisconnected();

						var oldService = service;
						if (oldService == null)
							LOGGER.info(logPrefix + "trying to connect to " + uri);
						else
							LOGGER.info(logPrefix + "trying to reconnect to " + uri);

						service = new MinerServiceImpl(miner, uri, timeout) {

							@Override
							protected void onDeadlineRequested(Challenge challenge) {
								super.onDeadlineRequested(challenge);

								serviceIsAlive();
								ReconnectingMinerServiceImpl.this.onDeadlineRequested(challenge);
							};

							@Override
							protected void onDeadlineComputed(Deadline deadline) {
								super.onDeadlineComputed(deadline);

								ReconnectingMinerServiceImpl.this.onDeadlineComputed(deadline);
							}
						};

						// a successful reconnection is a sign of activity by the service
						serviceIsAlive();

						// we close the previous instance of the service, if any
						if (oldService != null)
							oldService.close();
					}
					catch (FailedDeploymentException e) {
						// the glassfish websocket library catches the InterruptedException without
						// even setting the interruption flag! As a result, an early interruption becomes
						// a FailedDeploymentException and we check, below, if this seems actually due to
						// the service being closed already
						if (closedLatch.getCount() == 0L)
							return;

						LOGGER.warning(logPrefix + "failed connection attempt to " + uri + ": " + e.getMessage());
						onConnectionFailed(e);
						// we wait before trying to reconnect again, to avoid quick continuous reconnection attempts
						LOGGER.warning(logPrefix + "reconnection will be attempted after " + retryInterval + "ms from now");
						Thread.sleep(retryInterval);
					}
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			catch (RuntimeException e) {
				LOGGER.log(Level.SEVERE, "unexpected exception", e);
			}
			finally {
				if (service != null)
					service.close();
			}
		}

		/**
		 * Whenever the service gives a sign of activity, this method updates the moment of the last activity.
		 */				
		private void serviceIsAlive() {
			lastContactTime.set(System.currentTimeMillis());

			if (!connected.getAndSet(true))
				onConnected();
		}
	}

	@Override
	public String waitUntilClosed() throws InterruptedException {
		closedLatch.await();

		// this service can only be closed by calling its close() method, there is
		// no abnormal way of being closed
		return "closed normally";
	}

	@Override
	public MiningSpecification getMiningSpecification() throws ClosedMinerException, TimeoutException, InterruptedException {
		try (var scope = mkScope()) {
			var service = reconnector.service;
			if (service != null) {
				try {
					return service.getMiningSpecification();
				}
				catch (ClosedMinerException e) {
					// the internal service is currently closed, we wait a bit, maybe it will be open later
				}
			}

			Thread.sleep(timeout);

			service = reconnector.service;
			if (service != null) {
				try {
					return service.getMiningSpecification();
				}
				catch (ClosedMinerException e) {
					// the internal service is still closed: time out
				}
			}

			throw new TimeoutException("Miner service time-out");
		}
	}

	@Override
	public Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey publicKey) throws ClosedMinerException, TimeoutException, InterruptedException {
		try (var scope = mkScope()) {
			var service = reconnector.service;
			if (service != null) {
				try {
					return service.getBalance(signature, publicKey);
				}
				catch (ClosedMinerException e) {
					// the internal service is currently closed, we wait a bit, maybe it will be open later
				}
			}

			Thread.sleep(timeout);

			service = reconnector.service;
			if (service != null) {
				try {
					return service.getBalance(signature, publicKey);
				}
				catch (ClosedMinerException e) {
					// the internal service is still closed: time out
				}
			}

			throw new TimeoutException("Miner service time-out");
		}
	}

	/**
	 * Called when the connection to {@code uri} has been accomplished.
	 */
	protected void onConnected() {
	}

	/**
	 * Called when the connection to {@code uri} seems lost.
	 */
	protected void onDisconnected() {
	}

	/**
	 * Called when a connection attempt to {@code uri} fails.
	 * 
	 * @param cause the cause of the failed connection
	 */
	protected void onConnectionFailed(FailedDeploymentException cause) {
	}

	/**
	 * Called when a challenge arrives, requesting this service to provide the corresponding deadline.
	 * 
	 * @param challenge the challenge that has arrived
	 */
	protected void onDeadlineRequested(Challenge challenge) {
	}

	/**
	 * Called when {@code miner} has computed a deadline for a challenge.
	 * 
	 * @param deadline the deadline that has been computed
	 */
	protected void onDeadlineComputed(Deadline deadline) {
	}

	@Override
	public void close() {
		try {
			if (stopNewCalls()) {
				try {
					reconnector.interrupt();
				}
				finally {
					callCloseHandlers();
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			closedLatch.countDown();
		}
	}
}
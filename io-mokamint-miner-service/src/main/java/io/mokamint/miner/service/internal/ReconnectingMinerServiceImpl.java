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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.closeables.AbstractAutoCloseableWithOnCloseHandlers;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.mokamint.miner.api.ClosedMinerException;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.service.api.MinerService;
import io.mokamint.miner.service.api.ReconnectingMinerService;
import io.mokamint.nonce.api.Challenge;

public class ReconnectingMinerServiceImpl extends AbstractAutoCloseableWithOnCloseHandlers implements ReconnectingMinerService {
	private final Optional<Miner> miner;
	private final URI uri;
	private final int timeout;
	private final Consumer<FailedDeploymentException> onConnectionFailed;
	private volatile Reconnector reconnector;
	private volatile boolean isOpen;
	private final static long RETRY_INTERVAL = 180_000L;
	private final static Logger LOGGER = Logger.getLogger(ReconnectingMinerServiceImpl.class.getName());

	public ReconnectingMinerServiceImpl(Optional<Miner> miner, URI uri, int timeout, Consumer<FailedDeploymentException> onConnectionFailed) {
		this.miner = miner;
		this.uri = uri;
		this.timeout = timeout;
		this.onConnectionFailed = onConnectionFailed;

		open();
	}

	private class Reconnector extends Thread {
		private volatile MinerService service;
		private final AtomicLong lastContactTime = new AtomicLong(0L);

		/**
		 * Used to wait until the remote gets closed.
		 */
		private final CountDownLatch latch = new CountDownLatch(1);

		@Override
		public void run() {
			try {
				while (true) {
					long millisecondsSinceLastContact;

					do {
						millisecondsSinceLastContact = System.currentTimeMillis() - lastContactTime.get();
						if (millisecondsSinceLastContact < RETRY_INTERVAL)
							Thread.sleep(RETRY_INTERVAL - millisecondsSinceLastContact);
					}
					while (millisecondsSinceLastContact < RETRY_INTERVAL);

					try {
						var oldService = service;

						if (oldService == null)
							LOGGER.info("trying to connect to " + uri);
						else
							LOGGER.info("trying to reconnect to " + uri);

						service = new MinerServiceImpl(miner, uri, timeout) {

							@Override
							protected void onDeadlineRequested(Challenge challenge) {
								lastContactTime.set(System.currentTimeMillis());
							};
						};

						lastContactTime.set(System.currentTimeMillis());

						if (oldService != null)
							oldService.close();
					}
					catch (FailedDeploymentException e) {
						LOGGER.warning("failed connection attempt to " + uri + ": " + e.getMessage());
						onConnectionFailed.accept(e);
						Thread.sleep(RETRY_INTERVAL);
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
				try {
					if (service != null)
						service.close();
				}
				finally {
					latch.countDown();
				}
			}
		}
	}

	@Override
	public String waitUntilClosed() throws InterruptedException {
		reconnector.latch.await();

		// this service can only be closed by calling its close() method, there is
		// no abnormal way of being closed
		return "closed normally";
	}

	@Override
	public MiningSpecification getMiningSpecification() throws ClosedMinerException, TimeoutException, InterruptedException {
		if (!isOpen)
			throw new ClosedMinerException();

		var service = reconnector.service;
		if (service != null) {
			try {
				return service.getMiningSpecification();
			}
			catch (ClosedMinerException e) {
			}
		}

		Thread.sleep(timeout);

		if (!isOpen)
			throw new ClosedMinerException();

		service = reconnector.service;
		if (service != null) {
			try {
				return service.getMiningSpecification();
			}
			catch (ClosedMinerException e) {
			}
		}

		throw new TimeoutException("Miner service time-out");
	}

	@Override
	public Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey publicKey) throws ClosedMinerException, TimeoutException, InterruptedException {
		if (!isOpen)
			throw new ClosedMinerException();

		var service = reconnector.service;
		if (service != null) {
			try {
				return service.getBalance(signature, publicKey);
			}
			catch (ClosedMinerException e) {
			}
		}

		Thread.sleep(timeout);

		if (!isOpen)
			throw new ClosedMinerException();

		service = reconnector.service;
		if (service != null) {
			try {
				return service.getBalance(signature, publicKey);
			}
			catch (ClosedMinerException e) {
			}
		}

		throw new TimeoutException("Miner service time-out");
	}

	public void open() {
		if (!isOpen) {
			this.reconnector = new Reconnector();
			reconnector.start();
			isOpen = true;
		}
	}

	@Override
	public void close() {
		if (isOpen) {
			isOpen = false;

			try {
				reconnector.interrupt();
			}
			finally {
				callCloseHandlers();
			}
		}
	}
}
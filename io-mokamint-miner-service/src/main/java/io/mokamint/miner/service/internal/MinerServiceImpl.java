/*
Copyright 2023 Fausto Spoto

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

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.websockets.client.AbstractClientEndpoint;
import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.service.api.MinerService;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * Implementation of a websocket client that connects to a remote miner exported
 * by some Mokamint node. It is an adapter of a miner into a web service client.
 */
public class MinerServiceImpl extends AbstractWebSocketClient implements MinerService {

	/**
	 * The adapted miner.
	 */
	private final Miner miner;

	/**
	 * The websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}.
	 */
	private final URI uri;

	/**
	 * The session connected to the remote miner.
	 */
	private final Session session;

	/**
	 * Used to wait until the service gets disconnected.
	 */
	private final CountDownLatch latch = new CountDownLatch(1);

	private final static Logger LOGGER = Logger.getLogger(MinerServiceImpl.class.getName());

	/**
	 * Creates an miner service by adapting the given miner.
	 * 
	 * @param miner the adapted miner
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @throws IOException if an I/O error occurs
	 * @throws DeploymentException if the service cannot be deployed
	 */
	public MinerServiceImpl(Miner miner, URI uri) throws DeploymentException, IOException {
		this.miner = miner;
		this.uri = uri;
		this.session = new MinerServiceEndpoint().deployAt(uri);
		LOGGER.info("miner service bound to " + uri);
	}

	@Override
	public void waitUntilDisconnected() throws InterruptedException {
		latch.await();
	}

	@Override
	public void close() throws IOException {
		try {
			session.close();
		}
		catch (IOException e) {
			LOGGER.log(Level.WARNING, "cannot close the session", e);
			disconnect();
			throw e;
		}
	}

	/**
	 * The endpoint calls this when it gets closed, to wake-up who was waiting for disconnection.
	 */
	private void disconnect() {
		latch.countDown();
	}

	/**
	 * The endpoint calls this when a new deadline request arrives.
	 * It forwards the request to the miner.
	 * 
	 * @param description the description of the requested deadline
	 */
	private void requestDeadline(DeadlineDescription description) {
		LOGGER.info("received deadline request: " + description + " from " + uri);
		miner.requestDeadline(description, this::onDeadlineComputed);
	}

	/**
	 * Called by {@link #miner} when it finds a deadline. It forwards it
	 * to the remote miner.
	 * 
	 * @param deadline the deadline that the miner has just computed
	 */
	private void onDeadlineComputed(Deadline deadline) {
		if (session.isOpen()) {
			LOGGER.info("sending " + deadline + " to " + uri);
			session.getAsyncRemote().sendObject(deadline);
		}
	}

	private class MinerServiceEndpoint extends AbstractClientEndpoint<MinerServiceImpl> {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, DeadlineDescriptions.Decoder.class, Deadlines.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, MinerServiceImpl.this::requestDeadline);
		}

		@Override
		public void onClose(Session session, CloseReason closeReason) {
			disconnect();
		}
	}
}
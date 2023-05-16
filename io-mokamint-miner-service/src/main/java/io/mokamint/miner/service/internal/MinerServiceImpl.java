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
import java.net.URISyntaxException;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.service.api.MinerService;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.DeploymentException;
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
	private final Semaphore semaphore = new Semaphore(0);

	private final static Logger LOGGER = Logger.getLogger(MinerServiceImpl.class.getName());

	/**
	 * Creates an miner service by adapting the given miner.
	 * 
	 * @param miner the adapted miner
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @throws URISyntaxException if the {@code uri} syntax is wrong
	 * @throws IOException if an I/O error occurs
	 * @throws DeploymentException if the service cannot be deployed
	 */
	public MinerServiceImpl(Miner miner, URI uri) throws DeploymentException, IOException, URISyntaxException {
		this.miner = miner;
		this.uri = uri;
		this.session = new MinerServiceEndpoint(this).deployAt(uri);
		LOGGER.info("miner service bound to " + uri);
	}

	@Override
	public void waitUntilDisconnected() throws InterruptedException {
		semaphore.acquire();
	}

	/**
	 * The endpoint calls this when it gets closed, to wake-up who was waiting for disconnection.
	 */
	void disconnect() {
		semaphore.release();
	}

	@Override
	public void close() {
		disconnect();
		super.close();
	}

	/**
	 * The endpoint calls this when a new deadline request arrives.
	 * It forwards the request to the miner.
	 * 
	 * @param description the description of the requested deadline
	 */
	void requestDeadline(DeadlineDescription description) {
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
		LOGGER.info("sending " + deadline + " to " + uri);

		if (session.isOpen())
			session.getAsyncRemote().sendObject(deadline);
	}
}
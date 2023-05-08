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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.service.api.MinerService;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Session;

/**
 */
public class MinerServiceImpl extends AbstractWebSocketClient implements MinerService {

	/**
	 * The adapted miner.
	 */
	private final Miner miner;

	private final URI uri;

	/**
	 * The session connected to the remote miner.
	 */
	private final Session session;

	private final Semaphore semaphore = new Semaphore(0);

	private final static Logger LOGGER = Logger.getLogger(MinerServiceImpl.class.getName());

	/**
	 * Creates an web service by adapting the given miner.
	 * 
	 * @param miner the adapted miner
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @throws URISyntaxException if the {@code uri} syntax is wrong
	 * @throws IOException 
	 * @throws DeploymentException 
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

	@Override
	public void close() {
		super.close();
	}

	/**
	 * 
	 */
	void disconnect() {
		semaphore.release();
	}

	/**
	 * @param description
	 */
	void computeDeadline(DeadlineDescription description) {
		LOGGER.info("received request for " + description + " from " + uri);

		try {
			miner.requestDeadline(description, this::onDeadlineComputed);
		} catch (RejectedExecutionException | InterruptedException | TimeoutException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Called by {@link #miner} when it finds a deadline.
	 * 
	 * @param deadline the deadline that has just been computed
	 * @param miner the miner that found the deadline
	 */
	private void onDeadlineComputed(Deadline deadline, Miner miner) {
		LOGGER.info("sending " + deadline + " to " + uri);

		if (session.isOpen())
			try {
				session.getBasicRemote().sendObject(deadline);
			} catch (IOException | EncodeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
}
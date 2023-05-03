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

import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.service.api.MinerService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

/**
 */
public class MinerServiceImpl extends AbstractWebSocketClient implements MinerService {

	/**
	 * The adapted miner.
	 */
	private final Miner miner;

	/**
	 * The session connected to the remote miner.
	 */
	private final Session session;

	private final Semaphore semaphore = new Semaphore(0);

	/**
	 * Creates an web service by adapting the given miner.
	 * 
	 * @param miner the adapted miner
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @throws URISyntaxException 
	 * @throws IOException 
	 * @throws DeploymentException 
	 */
	public MinerServiceImpl(Miner miner, URI url) throws DeploymentException, IOException, URISyntaxException {
		this.miner = miner;
		this.session = new MinerServiceEndpoint(this).deployAt(url);
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
}
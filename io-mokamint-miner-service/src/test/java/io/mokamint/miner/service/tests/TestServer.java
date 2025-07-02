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

package io.mokamint.miner.service.tests;

import static io.mokamint.miner.remote.api.RemoteMiner.GET_MINING_SPECIFICATION_ENDPOINT;
import static io.mokamint.miner.remote.api.RemoteMiner.MINING_ENDPOINT;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import io.hotmoka.websockets.api.FailedDeploymentException;
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.miner.messages.GetMiningSpecificationMessages;
import io.mokamint.miner.messages.GetMiningSpecificationResultMessages;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * The implementation of a test websocket server that forwards deadline descriptions.
 */
public class TestServer extends AbstractWebSocketServer {
	private final CountDownLatch latch = new CountDownLatch(1);
	private final Consumer<Deadline> onDeadlineReceived;
	private volatile Session session;

	public TestServer(int port, Consumer<Deadline> onDeadlineReceived) throws FailedDeploymentException {
		this.onDeadlineReceived = onDeadlineReceived;
		startContainer("", port, GetMiningSpecificationEndpoint.config(this), MiningEndpoint.config(this));
	}

	public void requestDeadline(Challenge description) throws TimeoutException, InterruptedException, IOException {
		if (!latch.await(1, TimeUnit.SECONDS))
			throw new TimeoutException();

		sendObjectAsync(session, description);
	}

	public static class MiningEndpoint extends AbstractServerEndpoint<TestServer> {

		private static ServerEndpointConfig config(TestServer server) {
			return simpleConfig(server, MiningEndpoint.class, MINING_ENDPOINT, Deadlines.Decoder.class, Challenges.Encoder.class);
		}

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			server.session = session;
			addMessageHandler(session, server.onDeadlineReceived);
			server.latch.countDown();
	    }
	}

	// unused, but we must deploy it otherwise the handshake with the miner service fails
	public static class GetMiningSpecificationEndpoint extends AbstractServerEndpoint<TestServer> {
		
		@Override
	    public void onOpen(Session session, EndpointConfig config) {}
	
		private static ServerEndpointConfig config(TestServer server) {
			return simpleConfig(server, GetMiningSpecificationEndpoint.class, GET_MINING_SPECIFICATION_ENDPOINT,
				GetMiningSpecificationMessages.Decoder.class, GetMiningSpecificationResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}
}
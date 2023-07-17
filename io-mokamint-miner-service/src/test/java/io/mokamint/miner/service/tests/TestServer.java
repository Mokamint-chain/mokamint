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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.DeploymentException;
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

	public TestServer(int port, Consumer<Deadline> onDeadlineReceived) throws DeploymentException, IOException {
		this.onDeadlineReceived = onDeadlineReceived;
		startContainer("", port, MyEndpoint.config(this));
	}

	@Override
	public void close() {
		stopContainer();
	}

	public void requestDeadline(DeadlineDescription description) throws TimeoutException, InterruptedException, IOException {
		if (!latch.await(1, TimeUnit.SECONDS))
			throw new TimeoutException();

		sendObjectAsync(session, description);
	}

	public static class MyEndpoint extends AbstractServerEndpoint<TestServer> {

		private static ServerEndpointConfig config(TestServer server) {
			return simpleConfig(server, MyEndpoint.class, "/", Deadlines.Decoder.class, DeadlineDescriptions.Encoder.class);
		}

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			server.session = session;
			addMessageHandler(session, server.onDeadlineReceived);
			server.latch.countDown();
	    }
	}
}
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
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * The implementation of a test websocket server that forwards deadline descriptions.
 */
public class TestServer extends AbstractWebSocketServer {
	private static volatile Session session;
	private final static Semaphore semaphore = new Semaphore(0);
	private static Consumer<Deadline> onDeadlineReceived;

	public TestServer(int port, Consumer<Deadline> onDeadlineReceived) throws DeploymentException, IOException {
		TestServer.onDeadlineReceived = onDeadlineReceived;
    	var container = getContainer();
    	container.addEndpoint(ServerEndpointConfig.Builder.create(RemoteMinerEndpoint.class, "/")
				.encoders(List.of(DeadlineDescriptions.Encoder.class)) // it sends DeadlineDescription's
				.decoders(List.of(Deadlines.Decoder.class)) // it receives deadlines
				.build());
    	container.start("", port);
	}

	public void requestDeadline(DeadlineDescription description, int timeout) throws TimeoutException, InterruptedException {
		if (!semaphore.tryAcquire(timeout, TimeUnit.SECONDS))
			throw new TimeoutException();

		session.getAsyncRemote().sendObject(description);
	}

	public static class RemoteMinerEndpoint extends Endpoint {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			TestServer.session = session;
			session.addMessageHandler((MessageHandler.Whole<Deadline>) onDeadlineReceived::accept);
			semaphore.release();
	    }
	}
}
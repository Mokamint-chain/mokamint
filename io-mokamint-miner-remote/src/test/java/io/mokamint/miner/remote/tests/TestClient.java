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

package io.mokamint.miner.remote.tests;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.tyrus.client.ClientManager;

import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;

/**
 * The implementation of a test websocket client that forwards deadlines.
 */
public class TestClient extends AbstractWebSocketClient {
	private final Session session;
	private final Consumer<DeadlineDescription> onDeadlineDescriptionReceived;
	private final static Logger LOGGER = Logger.getLogger(TestClient.class.getName());

	public TestClient(URI uri, Consumer<DeadlineDescription> onDeadlineDescriptionReceived) throws DeploymentException, IOException {
		this.onDeadlineDescriptionReceived = onDeadlineDescriptionReceived;
		this.session = new MyEndpoint().deployAt(uri);
		LOGGER.info("test client bound to " + uri);
	}

	@Override
	public void close() {
		try {
			session.close();
		}
		catch (IOException e) {
			LOGGER.log(Level.WARNING, "cannot close the session", e);
		}

		super.close();
	}

	public void send(Deadline deadline) {
		session.getAsyncRemote().sendObject(deadline);
	}

	private class MyEndpoint extends Endpoint {

		Session deployAt(URI uri) throws DeploymentException, IOException {
			var config = ClientEndpointConfig.Builder.create()
				.decoders(List.of(DeadlineDescriptions.Decoder.class)) // it receives DeadlineDescription's
				.encoders(List.of(Deadlines.Encoder.class)) // and sends back Deadline's
				.build();

			return ClientManager.createClient().connectToServer(this, config, uri);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			session.addMessageHandler((MessageHandler.Whole<DeadlineDescription>) onDeadlineDescriptionReceived::accept);
		}
	}
}
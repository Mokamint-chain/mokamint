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
import java.util.function.Consumer;

import io.hotmoka.websockets.client.AbstractClientEndpoint;
import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * The implementation of a test websocket client that forwards deadlines.
 */
public class TestClient extends AbstractWebSocketClient {
	private final Consumer<DeadlineDescription> onDeadlineDescriptionReceived;
	private final Session session;

	public TestClient(URI uri, Consumer<DeadlineDescription> onDeadlineDescriptionReceived) throws DeploymentException, IOException {
		this.onDeadlineDescriptionReceived = onDeadlineDescriptionReceived;
		this.session = new MyEndpoint().deployAt(uri);
	}

	@Override
	public void close() throws IOException {
		session.close();
	}

	public void send(Deadline deadline) throws IOException {
		sendObjectAsync(session, deadline);
	}

	private class MyEndpoint extends AbstractClientEndpoint<TestClient> {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, DeadlineDescriptions.Decoder.class, Deadlines.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, onDeadlineDescriptionReceived);
		}
	}
}
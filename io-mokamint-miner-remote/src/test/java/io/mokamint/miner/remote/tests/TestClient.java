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

import static io.mokamint.miner.remote.api.RemoteMiner.GET_MINING_SPECIFICATION_ENDPOINT;
import static io.mokamint.miner.remote.api.RemoteMiner.MINING_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;

import io.hotmoka.websockets.beans.ExceptionMessages;
import io.hotmoka.websockets.client.AbstractClientEndpoint;
import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.mokamint.miner.messages.GetMiningSpecificationMessages;
import io.mokamint.miner.messages.GetMiningSpecificationResultMessages;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * The implementation of a test websocket client that forwards deadlines.
 */
public class TestClient extends AbstractWebSocketClient {
	private final Consumer<Challenge> onChallengeReceived;
	private final Session session;

	public TestClient(URI uri, Consumer<Challenge> onChallengeReceived) throws DeploymentException, IOException {
		this.onChallengeReceived = onChallengeReceived;
		this.session = new MiningEndpoint().deployAt(uri);

		// unused, but we must deploy it otherwise the handshake with the miner service fails
		new GetMiningSpecificationEndpoint().deployAt(uri);
	}

	@Override
	public void close() throws IOException {
		session.close();
	}

	public void send(Deadline deadline) throws IOException {
		sendObjectAsync(session, deadline);
	}

	private class MiningEndpoint extends AbstractClientEndpoint<TestClient> {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri.resolve(MINING_ENDPOINT), Challenges.Decoder.class, Deadlines.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, onChallengeReceived);
		}
	}

	private class GetMiningSpecificationEndpoint extends AbstractClientEndpoint<TestClient> {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri.resolve(GET_MINING_SPECIFICATION_ENDPOINT), GetMiningSpecificationResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetMiningSpecificationMessages.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {}
	}
}
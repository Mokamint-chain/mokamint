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

package io.mokamint.node.service.tests;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.hotmoka.websockets.beans.RpcMessage;
import io.hotmoka.websockets.client.AbstractClientEndpoint;
import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.ExceptionResultMessage;
import io.mokamint.node.messages.ExceptionResultMessages;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessage;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetPeersMessages;
import io.mokamint.node.messages.GetPeersResultMessage;
import io.mokamint.node.messages.GetPeersResultMessages;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * The implementation of a test websocket client that connects to a node service.
 */
public class TestClient extends AbstractWebSocketClient {
	private final Consumer<Stream<Peer>> onGetPeersResult;
	private final Consumer<Optional<Block>> onGetBlockResult;
	private final Consumer<ExceptionResultMessage> onException;
	private final Session getPeersSession;
	private final Session getBlockSession;

	public TestClient(URI uri, Consumer<Stream<Peer>> onGetPeersResult, Consumer<Optional<Block>> onGetBlockResult, Consumer<ExceptionResultMessage> onException) throws DeploymentException, IOException {
		this.onGetPeersResult = onGetPeersResult;
		this.onGetBlockResult = onGetBlockResult;
		this.onException = onException;
		this.getPeersSession = new GetPeersEndpoint().deployAt(uri.resolve("/get_peers"));
		this.getBlockSession = new GetBlockEndpoint().deployAt(uri.resolve("/get_block"));
	}

	@Override
	public void close() throws IOException {
		try {
			getPeersSession.close();
		}
		catch (IOException e) {
			getBlockSession.close();
			throw e;
		}

		getBlockSession.close();
	}

	public void sendGetPeers() {
		sendObjectAsync(getPeersSession, GetPeersMessages.of("id"));
	}

	public void sendGetBlock(byte[] hash) {
		sendObjectAsync(getBlockSession, GetBlockMessages.of(hash, "id"));
	}

	private class GetPeersEndpoint extends AbstractClientEndpoint<TestClient> {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetPeersResultMessages.Decoder.class, GetPeersMessages.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetPeersResultMessage message) -> onGetPeersResult.accept(message.get()));
		}
	}

	private class GetBlockEndpoint extends AbstractClientEndpoint<TestClient> {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetBlockResultMessages.Decoder.class, ExceptionResultMessages.Decoder.class, GetBlockMessages.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (RpcMessage message) -> {
				if (message instanceof GetBlockResultMessage)
					onGetBlockResult.accept(((GetBlockResultMessage) message).get());
				else if (message instanceof ExceptionResultMessage)
					onException.accept((ExceptionResultMessage) message);
			});
		}
	}
}
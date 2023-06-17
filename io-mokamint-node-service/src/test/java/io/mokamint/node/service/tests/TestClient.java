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
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.ExceptionResultMessage;
import io.mokamint.node.messages.ExceptionResultMessages;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessage;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoMessages;
import io.mokamint.node.messages.GetChainInfoResultMessage;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetConfigMessages;
import io.mokamint.node.messages.GetConfigResultMessage;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetPeersMessages;
import io.mokamint.node.messages.GetPeersResultMessage;
import io.mokamint.node.messages.GetPeersResultMessages;
import io.mokamint.node.service.api.PublicNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * The implementation of a test websocket client that connects to a node service.
 */
public class TestClient extends AbstractWebSocketClient {
	private final Consumer<Stream<Peer>> onGetPeersResult;
	private final Consumer<Optional<Block>> onGetBlockResult;
	private final Consumer<ConsensusConfig> onGetConfigResult;
	private final Consumer<ChainInfo> onGetChainInfoResult;
	private final Consumer<ExceptionResultMessage> onException;
	private final Session getPeersSession;
	private final Session getBlockSession;
	private final Session getConfigSession;
	private final Session getChainInfoSession;

	public TestClient(URI uri, Consumer<Stream<Peer>> onGetPeersResult,
			Consumer<Optional<Block>> onGetBlockResult,
			Consumer<ConsensusConfig> onGetConfigResult,
			Consumer<ChainInfo> onGetChainInfoResult,
			Consumer<ExceptionResultMessage> onException) throws DeploymentException, IOException {

		this.onGetPeersResult = onGetPeersResult;
		this.onGetBlockResult = onGetBlockResult;
		this.onGetConfigResult = onGetConfigResult;
		this.onGetChainInfoResult = onGetChainInfoResult;
		this.onException = onException;
		this.getPeersSession = new GetPeersEndpoint().deployAt(uri.resolve(PublicNodeService.GET_PEERS_ENDPOINT));
		this.getBlockSession = new GetBlockEndpoint().deployAt(uri.resolve(PublicNodeService.GET_BLOCK_ENDPOINT));
		this.getConfigSession = new GetConfigEndpoint().deployAt(uri.resolve(PublicNodeService.GET_CONFIG_ENDPOINT));
		this.getChainInfoSession = new GetChainInfoEndpoint().deployAt(uri.resolve(PublicNodeService.GET_CHAIN_INFO_ENDPOINT));
	}

	@Override
	public void close() throws IOException {
		getPeersSession.close();
		getBlockSession.close();
		getConfigSession.close();
		getChainInfoSession.close();
	}

	public void sendGetPeers() {
		sendObjectAsync(getPeersSession, GetPeersMessages.of("id"));
	}

	public void sendGetBlock(byte[] hash) {
		sendObjectAsync(getBlockSession, GetBlockMessages.of(hash, "id"));
	}

	public void sendGetConfig() {
		sendObjectAsync(getConfigSession, GetConfigMessages.of("id"));
	}

	public void sendGetChainInfo() {
		sendObjectAsync(getChainInfoSession, GetChainInfoMessages.of("id"));
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

	private class GetConfigEndpoint extends AbstractClientEndpoint<TestClient> {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetConfigResultMessages.Decoder.class, ExceptionResultMessages.Decoder.class, GetConfigMessages.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (RpcMessage message) -> {
				if (message instanceof GetConfigResultMessage)
					onGetConfigResult.accept(((GetConfigResultMessage) message).get());
				else if (message instanceof ExceptionResultMessage)
					onException.accept((ExceptionResultMessage) message);
			});
		}
	}

	private class GetChainInfoEndpoint extends AbstractClientEndpoint<TestClient> {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetChainInfoResultMessages.Decoder.class, ExceptionResultMessages.Decoder.class, GetChainInfoMessages.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (RpcMessage message) -> {
				if (message instanceof GetChainInfoResultMessage)
					onGetChainInfoResult.accept(((GetChainInfoResultMessage) message).get());
				else if (message instanceof ExceptionResultMessage)
					onException.accept((ExceptionResultMessage) message);
			});
		}
	}
}
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

package io.mokamint.node.remote.tests;

import java.io.IOException;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.GetBlockMessage;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoMessage;
import io.mokamint.node.messages.GetChainInfoMessages;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetConfigMessage;
import io.mokamint.node.messages.GetConfigMessages;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetPeersMessage;
import io.mokamint.node.messages.GetPeersMessages;
import io.mokamint.node.messages.GetPeersResultMessages;
import io.mokamint.node.service.api.PublicNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Test server implementation.
 */
@ThreadSafe
public class PublicTestServer extends AbstractWebSocketServer {

	/**
	 * Creates a new server, at the given network port.
	 * 
	 * @param port the port
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public PublicTestServer(int port) throws DeploymentException, IOException {
		startContainer("", port, GetPeersEndpoint.config(this), GetBlockEndpoint.config(this), GetConfigEndpoint.config(this), GetChainInfoEndpoint.config(this));
	}

	@Override
	public void close() {
		stopContainer();
	};

	protected void onGetPeers(GetPeersMessage message, Session session) {}

	public static class GetPeersEndpoint extends AbstractServerEndpoint<PublicTestServer> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetPeersMessage message) -> getServer().onGetPeers(message, session));
	    }

		private static ServerEndpointConfig config(PublicTestServer server) {
			return simpleConfig(server, GetPeersEndpoint.class, PublicNodeService.GET_PEERS_ENDPOINT,
					GetPeersMessages.Decoder.class, GetPeersResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetBlock(GetBlockMessage message, Session session) {}

	public static class GetBlockEndpoint extends AbstractServerEndpoint<PublicTestServer> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetBlockMessage message) -> getServer().onGetBlock(message, session));
	    }

		private static ServerEndpointConfig config(PublicTestServer server) {
			return simpleConfig(server, GetBlockEndpoint.class, PublicNodeService.GET_BLOCK_ENDPOINT,
					GetBlockMessages.Decoder.class, GetBlockResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetConfig(GetConfigMessage message, Session session) {}

	public static class GetConfigEndpoint extends AbstractServerEndpoint<PublicTestServer> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetConfigMessage message) -> getServer().onGetConfig(message, session));
	    }

		private static ServerEndpointConfig config(PublicTestServer server) {
			return simpleConfig(server, GetConfigEndpoint.class, PublicNodeService.GET_CONFIG_ENDPOINT,
					GetConfigMessages.Decoder.class, GetConfigResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetChainInfo(GetChainInfoMessage message, Session session) {}

	public static class GetChainInfoEndpoint extends AbstractServerEndpoint<PublicTestServer> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetChainInfoMessage message) -> getServer().onGetChainInfo(message, session));
	    }

		private static ServerEndpointConfig config(PublicTestServer server) {
			return simpleConfig(server, GetChainInfoEndpoint.class, PublicNodeService.GET_CHAIN_INFO_ENDPOINT,
					GetChainInfoMessages.Decoder.class, GetChainInfoResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}
}
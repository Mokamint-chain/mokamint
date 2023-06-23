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

package io.mokamint.node.service.internal;

import java.io.IOException;
import java.util.logging.Logger;

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
 * Partial implementation of a public node service. It publishes endpoints at a URL,
 * where clients can connect to query the public API of a Mokamint node.
 */
@ThreadSafe
public abstract class AbstractPublicNodeServiceImpl extends AbstractWebSocketServer implements PublicNodeService {

	/**
	 * The port of localhost, where this service is published.
	 */
	private final int port;

	private final static Logger LOGGER = Logger.getLogger(AbstractPublicNodeServiceImpl.class.getName());

	/**
	 * Creates a new server, at the given network port.
	 * 
	 * @param port the port
	 * @throws DeploymentException if the service cannot be deployed
	 */
	protected AbstractPublicNodeServiceImpl(int port) throws DeploymentException {
		this.port = port;
	}

	/**
	 * Deploys the service.
	 * 
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	protected void deploy() throws DeploymentException, IOException {
		startContainer("", port, GetPeersEndpoint.config(this), GetBlockEndpoint.config(this), GetConfigEndpoint.config(this), GetChainInfoEndpoint.config(this));
		LOGGER.info("published a public node service at ws://localhost:" + port);
	}

	@Override
	public void close() {
		stopContainer();
		LOGGER.info("closed the public node service at ws://localhost:" + port);
	};

	protected void onGetPeers(GetPeersMessage message, Session session) {
		LOGGER.info("received a " + GET_PEERS_ENDPOINT + " request");
	}

	public static class GetPeersEndpoint extends AbstractServerEndpoint<AbstractPublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetPeersMessage message) -> getServer().onGetPeers(message, session));
	    }

		private static ServerEndpointConfig config(AbstractPublicNodeServiceImpl server) {
			return simpleConfig(server, GetPeersEndpoint.class, GET_PEERS_ENDPOINT,
					GetPeersMessages.Decoder.class, GetPeersResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetBlock(GetBlockMessage message, Session session) {
		LOGGER.info("received a " + GET_BLOCK_ENDPOINT + " request");
	}

	public static class GetBlockEndpoint extends AbstractServerEndpoint<AbstractPublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetBlockMessage message) -> getServer().onGetBlock(message, session));
	    }

		private static ServerEndpointConfig config(AbstractPublicNodeServiceImpl server) {
			return simpleConfig(server, GetBlockEndpoint.class, GET_BLOCK_ENDPOINT,
					GetBlockMessages.Decoder.class, GetBlockResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetConfig(GetConfigMessage message, Session session) {
		LOGGER.info("received a " + GET_CONFIG_ENDPOINT + " request");
	}

	public static class GetConfigEndpoint extends AbstractServerEndpoint<AbstractPublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetConfigMessage message) -> getServer().onGetConfig(message, session));
	    }

		private static ServerEndpointConfig config(AbstractPublicNodeServiceImpl server) {
			return simpleConfig(server, GetConfigEndpoint.class, GET_CONFIG_ENDPOINT,
					GetConfigMessages.Decoder.class, GetConfigResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetChainInfo(GetChainInfoMessage message, Session session) {
		LOGGER.info("received a " + GET_CHAIN_INFO_ENDPOINT + " request");
	}

	public static class GetChainInfoEndpoint extends AbstractServerEndpoint<AbstractPublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetChainInfoMessage message) -> getServer().onGetChainInfo(message, session));
	    }

		private static ServerEndpointConfig config(AbstractPublicNodeServiceImpl server) {
			return simpleConfig(server, GetChainInfoEndpoint.class, GET_CHAIN_INFO_ENDPOINT,
					GetChainInfoMessages.Decoder.class, GetChainInfoResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}
}
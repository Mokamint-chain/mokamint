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
import io.mokamint.node.messages.AddPeersMessage;
import io.mokamint.node.messages.AddPeersMessages;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.RemovePeersMessage;
import io.mokamint.node.messages.RemovePeersMessages;
import io.mokamint.node.messages.VoidMessages;
import io.mokamint.node.service.api.RestrictedNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Partial implementation of a restricted node service. It publishes endpoints at a URL,
 * where clients can connect to query the restricted API of a Mokamint node.
 */
@ThreadSafe
public abstract class AbstractRestrictedNodeServiceImpl extends AbstractWebSocketServer implements RestrictedNodeService {

	/**
	 * The port of localhost, where this service is published.
	 */
	private final int port;

	private final static Logger LOGGER = Logger.getLogger(AbstractRestrictedNodeServiceImpl.class.getName());

	/**
	 * Creates a new server, at the given network port.
	 * 
	 * @param port the port
	 * @throws DeploymentException if the service cannot be deployed
	 */
	protected AbstractRestrictedNodeServiceImpl(int port) throws DeploymentException {
		this.port = port;
	}

	/**
	 * Deploys the service.
	 * 
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	protected void deploy() throws DeploymentException, IOException {
		startContainer("", port, AddPeersEndpoint.config(this), RemoveBlockEndpoint.config(this));
		LOGGER.info("published a restricted node service at ws://localhost:" + port);
	}

	@Override
	public void close() {
		stopContainer();
		LOGGER.info("closed the restricted node service at ws://localhost:" + port);
	};

	protected void onAddPeers(AddPeersMessage message, Session session) {
		LOGGER.info("received a " + ADD_PEERS_ENDPOINT + " request");
	}

	public static class AddPeersEndpoint extends AbstractServerEndpoint<AbstractRestrictedNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (AddPeersMessage message) -> getServer().onAddPeers(message, session));
	    }

		private static ServerEndpointConfig config(AbstractRestrictedNodeServiceImpl server) {
			return simpleConfig(server, AddPeersEndpoint.class, ADD_PEERS_ENDPOINT,
					AddPeersMessages.Decoder.class, VoidMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onRemovePeers(RemovePeersMessage message, Session session) {
		LOGGER.info("received a " + REMOVE_PEERS_ENDPOINT + " request");
	}

	public static class RemoveBlockEndpoint extends AbstractServerEndpoint<AbstractRestrictedNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (RemovePeersMessage message) -> getServer().onRemovePeers(message, session));
	    }

		private static ServerEndpointConfig config(AbstractRestrictedNodeServiceImpl server) {
			return simpleConfig(server, RemoveBlockEndpoint.class, REMOVE_PEERS_ENDPOINT,
					RemovePeersMessages.Decoder.class, VoidMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}
}
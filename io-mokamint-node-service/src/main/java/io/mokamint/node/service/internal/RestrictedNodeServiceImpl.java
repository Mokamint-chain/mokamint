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
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.node.api.RestrictedNode;
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
 * The implementation of a restricted node service. It publishes endpoints at a URL,
 * where clients can connect to query the restricted API of a Mokamint node.
 */
@ThreadSafe
public class RestrictedNodeServiceImpl extends AbstractWebSocketServer implements RestrictedNodeService {

	/**
	 * The port of localhost, where this service is listening.
	 */
	private final int port;

	/**
	 * The node whose API is published.
	 */
	private final RestrictedNode node;

	private final static Logger LOGGER = Logger.getLogger(RestrictedNodeServiceImpl.class.getName());

	/**
	 * Creates a new service for the given node, at the given network port.
	 * 
	 * @param node the node
	 * @param port the port
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public RestrictedNodeServiceImpl(RestrictedNode node, int port) throws DeploymentException, IOException {
		this.port = port;
		this.node = node;
		startContainer("", port, AddPeersEndpoint.config(this), RemovePeersEndpoint.config(this));
    	LOGGER.info("published a restricted node service at ws://localhost:" + port);
	}

	@Override
	public void close() {
		stopContainer();
		LOGGER.info("closed the restricted node service at ws://localhost:" + port);
	}

	private void addPeers(AddPeersMessage message, Session session) {
		LOGGER.info("received an " + ADD_PEERS_ENDPOINT + " request");
		try {
			node.addPeers(message.getPeers());
			sendObjectAsync(session, VoidMessages.of(message.getId()));
		}
		catch (TimeoutException | InterruptedException e) {
			sendObjectAsync(session, ExceptionMessages.of(e, message.getId()));
		}
	};

	public static class AddPeersEndpoint extends AbstractServerEndpoint<RestrictedNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (AddPeersMessage message) -> getServer().addPeers(message, session));
	    }

		private static ServerEndpointConfig config(RestrictedNodeServiceImpl server) {
			return simpleConfig(server, AddPeersEndpoint.class, ADD_PEERS_ENDPOINT, AddPeersMessages.Decoder.class, VoidMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	private void removePeers(RemovePeersMessage message, Session session) {
		LOGGER.info("received a " + REMOVE_PEERS_ENDPOINT + " request");
		try {
			node.removePeers(message.getPeers());
			sendObjectAsync(session, VoidMessages.of(message.getId()));
		}
		catch (TimeoutException | InterruptedException e) {
			sendObjectAsync(session, ExceptionMessages.of(e, message.getId()));
		}
	};

	public static class RemovePeersEndpoint extends AbstractServerEndpoint<RestrictedNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (RemovePeersMessage message) -> getServer().removePeers(message, session));
	    }

		private static ServerEndpointConfig config(RestrictedNodeServiceImpl server) {
			return simpleConfig(server, RemovePeersEndpoint.class, REMOVE_PEERS_ENDPOINT, RemovePeersMessages.Decoder.class, VoidMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}
}
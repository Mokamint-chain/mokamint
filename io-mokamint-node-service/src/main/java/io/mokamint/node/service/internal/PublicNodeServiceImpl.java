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
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.ExceptionResultMessages;
import io.mokamint.node.messages.GetBlockMessage;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetPeersMessage;
import io.mokamint.node.messages.GetPeersMessages;
import io.mokamint.node.messages.GetPeersResultMessages;
import io.mokamint.node.service.api.PublicNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * The implementation of a public node service. It publishes an endpoint at a URL,
 * where clients can connect to query the public API of a Mokamint node.
 */
@ThreadSafe
public class PublicNodeServiceImpl extends AbstractWebSocketServer implements PublicNodeService {

	/**
	 * The port of localhost, where this service is listening.
	 */
	private final int port;

	/**
	 * The node whose API is published.
	 */
	private final PublicNode node;

	private final static Logger LOGGER = Logger.getLogger(PublicNodeServiceImpl.class.getName());

	/**
	 * Creates a new service for the given node, at the given network port.
	 * 
	 * @param node the node
	 * @param port the port
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public PublicNodeServiceImpl(PublicNode node, int port) throws DeploymentException, IOException {
		this.port = port;
		this.node = node;
    	var container = getContainer();
    	container.addEndpoint(GetPeersEndpoint.config(this));
    	container.addEndpoint(GetBlockEndpoint.config(this));
    	container.start("", port);
    	LOGGER.info("published a public node service at ws://localhost:" + port);
	}

	@Override
	public void close() {
		super.close();
		LOGGER.info("closed the public node service at ws://localhost:" + port);
	}

	private void getPeers(GetPeersMessage message, Session session) {
		LOGGER.info("received a get_peers request");
		sendObjectAsync(session, GetPeersResultMessages.of(node.getPeers()));
	};

	public static class GetPeersEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetPeersMessage message) -> getServer().getPeers(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetPeersEndpoint.class, "/get_peers", GetPeersMessages.Decoder.class, GetPeersResultMessages.Encoder.class);
		}
	}

	private void getBlock(GetBlockMessage message, Session session) {
		LOGGER.info("received a get_block request");
		try {
			sendObjectAsync(session, GetBlockResultMessages.of(node.getBlock(message.getHash())));
		}
		catch (NoSuchAlgorithmException e) {
			sendObjectAsync(session, ExceptionResultMessages.of(e));
		}
	};

	public static class GetBlockEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetBlockMessage message) -> getServer().getBlock(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetBlockEndpoint.class, "/get_block", GetBlockMessages.Decoder.class, GetBlockResultMessages.Encoder.class, ExceptionResultMessages.Encoder.class);
		}
	}
}
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
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.node.NodeInternals.CloseHandler;
import io.mokamint.node.RestrictedNodeInternals;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.messages.AddPeerMessage;
import io.mokamint.node.messages.AddPeerMessages;
import io.mokamint.node.messages.AddPeerResultMessages;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.RemovePeerMessage;
import io.mokamint.node.messages.RemovePeerMessages;
import io.mokamint.node.messages.RemovePeerResultMessages;
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
	 * The node whose API is published.
	 */
	private final RestrictedNodeInternals node;

	/**
	 * The port of localhost, where this service is published.
	 */
	private final int port;

	/**
	 * We need this intermediate definition since two instances of a method reference
	 * are not the same, nor equals.
	 */
	private final CloseHandler this_close = this::close;

	private final static Logger LOGGER = Logger.getLogger(RestrictedNodeServiceImpl.class.getName());

	/**
	 * Creates a new service for the given node, at the given network port.
	 * 
	 * @param node the node
	 * @param port the port
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public RestrictedNodeServiceImpl(RestrictedNodeInternals node, int port) throws DeploymentException, IOException {
		this.node = node;
		this.port = port;

		// if the node gets closed, then this service will be closed as well
		node.addOnClosedHandler(this_close);

		startContainer("", port, AddPeersEndpoint.config(this), RemoveBlockEndpoint.config(this));
		LOGGER.info("published a restricted node service at ws://localhost:" + port);
	}

	@Override
	public void close() {
		node.removeOnCloseHandler(this_close);
		stopContainer();
		LOGGER.info("closed the restricted node service at ws://localhost:" + port);
	}

	/**
	 * Sends an exception message to the given session.
	 * 
	 * @param session the session
	 * @param e the exception used to build the message
	 * @param id the identifier of the message to send
	 * @throws IOException if there was an I/O problem
	 */
	private void sendExceptionAsync(Session session, Exception e, String id) throws IOException {
		sendObjectAsync(session, ExceptionMessages.of(e, id));
	}

	protected void onAddPeers(AddPeerMessage message, Session session) {
		LOGGER.info("received an " + ADD_PEER_ENDPOINT + " request");

		try {
			try {
				node.addPeer(message.getPeer());
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException | DatabaseException | IOException | IncompatiblePeerException e) {
				sendExceptionAsync(session, e, message.getId());
				return;
			}

			sendObjectAsync(session, AddPeerResultMessages.of(message.getId()));
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot send to session: it might be closed", e);
		}
	};

	protected void onRemovePeer(RemovePeerMessage message, Session session) {
		LOGGER.info("received a " + REMOVE_PEER_ENDPOINT + " request");

		try {
			try {
				node.removePeer(message.getPeer());
				sendObjectAsync(session, RemovePeerResultMessages.of(message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException | DatabaseException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot send to session: it might be closed", e);
		}
	};

	public static class AddPeersEndpoint extends AbstractServerEndpoint<RestrictedNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (AddPeerMessage message) -> getServer().onAddPeers(message, session));
	    }

		private static ServerEndpointConfig config(RestrictedNodeServiceImpl server) {
			return simpleConfig(server, AddPeersEndpoint.class, ADD_PEER_ENDPOINT,
					AddPeerMessages.Decoder.class, AddPeerResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	public static class RemoveBlockEndpoint extends AbstractServerEndpoint<RestrictedNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (RemovePeerMessage message) -> getServer().onRemovePeer(message, session));
	    }

		private static ServerEndpointConfig config(RestrictedNodeServiceImpl server) {
			return simpleConfig(server, RemoveBlockEndpoint.class, REMOVE_PEER_ENDPOINT,
					RemovePeerMessages.Decoder.class, RemovePeerResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}
}
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
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.closeables.api.OnCloseHandler;
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.miner.api.MinerException;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.PeerException;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.api.RestrictedNode;
import io.mokamint.node.messages.AddPeerMessages;
import io.mokamint.node.messages.AddPeerResultMessages;
import io.mokamint.node.messages.OpenMinerMessages;
import io.mokamint.node.messages.OpenMinerResultMessages;
import io.mokamint.node.messages.RemoveMinerMessages;
import io.mokamint.node.messages.RemoveMinerResultMessages;
import io.mokamint.node.messages.RemovePeerMessages;
import io.mokamint.node.messages.RemovePeerResultMessages;
import io.mokamint.node.messages.api.AddPeerMessage;
import io.mokamint.node.messages.api.OpenMinerMessage;
import io.mokamint.node.messages.api.RemoveMinerMessage;
import io.mokamint.node.messages.api.RemovePeerMessage;
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
	private final RestrictedNode node;

	/**
	 * We need this intermediate definition since two instances of a method reference
	 * are not the same, nor equals.
	 */
	private final OnCloseHandler this_close = this::close;

	/**
	 * The prefix used in the log messages;
	 */
	private final String logPrefix;

	private final static Logger LOGGER = Logger.getLogger(RestrictedNodeServiceImpl.class.getName());

	/**
	 * Creates a new service for the given node, at the given network port.
	 * 
	 * @param node the node
	 * @param port the port
	 * @throws NodeException if the service cannot be deployed
	 */
	public RestrictedNodeServiceImpl(RestrictedNode node, int port) throws NodeException {
		this.node = node;
		this.logPrefix = "restricted service(ws://localhost:" + port + "): ";

		// if the node gets closed, then this service will be closed as well
		node.addOnCloseHandler(this_close);

		try {
			startContainer("", port,
					AddPeersEndpoint.config(this), RemovePeerEndpoint.config(this),
					OpenMinerEndpoint.config(this), RemoveMinerEndpoint.config(this));
		}
		catch (IOException | DeploymentException e) {
			throw new NodeException(e);
		}

		LOGGER.info(logPrefix + "published");
	}

	@Override
	protected void closeResources() {
		node.removeOnCloseHandler(this_close);
		LOGGER.info(logPrefix + "closed");
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
		if (e instanceof InterruptedException) {
			// if the serviced node gets interrupted, then the external vision of the node
			// is that of a node that is not working properly
			sendObjectAsync(session, ExceptionMessages.of(new NodeException("The service has been interrupted"), id));
			// we take note that we have been interrupted
			Thread.currentThread().interrupt();
		}
		else
			sendObjectAsync(session, ExceptionMessages.of(e, id));
	}

	protected void onAddPeer(AddPeerMessage message, Session session) {
		LOGGER.info(logPrefix + "received an " + ADD_PEER_ENDPOINT + " request");

		try {
			Optional<PeerInfo> result;

			try {
				result = node.add(message.getPeer());
			}
			catch (TimeoutException | NodeException | InterruptedException | PeerException | PeerRejectedException e) {
				sendExceptionAsync(session, e, message.getId());
				return;
			}

			sendObjectAsync(session, AddPeerResultMessages.of(result, message.getId()));
		}
		catch (IOException e) {
			LOGGER.warning(logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	protected void onRemovePeer(RemovePeerMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + REMOVE_PEER_ENDPOINT + " request");

		try {
			boolean result;

			try {
				result = node.remove(message.getPeer());
			}
			catch (TimeoutException | InterruptedException | NodeException e) {
				sendExceptionAsync(session, e, message.getId());
				return;
			}

			sendObjectAsync(session, RemovePeerResultMessages.of(result, message.getId()));
		}
		catch (IOException e) {
			LOGGER.warning(logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	protected void onOpenMiner(OpenMinerMessage message, Session session) {
		LOGGER.info(logPrefix + "received an " + OPEN_MINER_ENDPOINT + " request");

		try {
			Optional<MinerInfo> result;

			try {
				result = node.openMiner(message.getPort());
			}
			catch (TimeoutException | InterruptedException | NodeException | MinerException e) {
				sendExceptionAsync(session, e, message.getId());
				return;
			}

			sendObjectAsync(session, OpenMinerResultMessages.of(result, message.getId()));
		}
		catch (IOException e) {
			LOGGER.warning(logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	protected void onRemoveMiner(RemoveMinerMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + REMOVE_MINER_ENDPOINT + " request");

		try {
			boolean result;

			try {
				result = node.removeMiner(message.getUUID());
			}
			catch (TimeoutException | InterruptedException | NodeException e) {
				sendExceptionAsync(session, e, message.getId());
				return;
			}

			sendObjectAsync(session, RemoveMinerResultMessages.of(result, message.getId()));
		}
		catch (IOException e) {
			LOGGER.warning(logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class AddPeersEndpoint extends AbstractServerEndpoint<RestrictedNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (AddPeerMessage message) -> server.onAddPeer(message, session));
	    }

		private static ServerEndpointConfig config(RestrictedNodeServiceImpl server) {
			return simpleConfig(server, AddPeersEndpoint.class, ADD_PEER_ENDPOINT,
					AddPeerMessages.Decoder.class, AddPeerResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	public static class RemovePeerEndpoint extends AbstractServerEndpoint<RestrictedNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (RemovePeerMessage message) -> server.onRemovePeer(message, session));
	    }

		private static ServerEndpointConfig config(RestrictedNodeServiceImpl server) {
			return simpleConfig(server, RemovePeerEndpoint.class, REMOVE_PEER_ENDPOINT,
					RemovePeerMessages.Decoder.class, RemovePeerResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	public static class OpenMinerEndpoint extends AbstractServerEndpoint<RestrictedNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (OpenMinerMessage message) -> server.onOpenMiner(message, session));
	    }

		private static ServerEndpointConfig config(RestrictedNodeServiceImpl server) {
			return simpleConfig(server, OpenMinerEndpoint.class, OPEN_MINER_ENDPOINT,
					OpenMinerMessages.Decoder.class, OpenMinerResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	public static class RemoveMinerEndpoint extends AbstractServerEndpoint<RestrictedNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (RemoveMinerMessage message) -> server.onRemoveMiner(message, session));
	    }

		private static ServerEndpointConfig config(RestrictedNodeServiceImpl server) {
			return simpleConfig(server, RemoveMinerEndpoint.class, REMOVE_MINER_ENDPOINT,
					RemoveMinerMessages.Decoder.class, RemoveMinerResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}
}
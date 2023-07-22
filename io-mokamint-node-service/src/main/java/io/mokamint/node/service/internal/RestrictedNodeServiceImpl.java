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
import io.mokamint.node.RestrictedNodeInternals;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.messages.AddPeerMessage;
import io.mokamint.node.messages.AddPeerResultMessages;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.RemovePeerMessage;
import io.mokamint.node.messages.RemovePeerResultMessages;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

/**
 * The implementation of a restricted node service. It publishes endpoints at a URL,
 * where clients can connect to query the restricted API of a Mokamint node.
 */
@ThreadSafe
public class RestrictedNodeServiceImpl extends AbstractRestrictedNodeServiceImpl {

	/**
	 * The node whose API is published.
	 */
	private final RestrictedNodeInternals node;

	/**
	 * We need this intermediate definition since two instances of a method reference
	 * are not the same, nor equals.
	 */
	private final Runnable this_close = this::close;

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
		super(port);
		this.node = node;

		// if the node gets closed, then this service will be closed as well
		node.addOnClosedHandler(this_close);

		deploy();
	}

	@Override
	public void close() {
		node.removeOnCloseHandler(this_close);
		super.close();
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

	@Override
	protected void onAddPeers(AddPeerMessage message, Session session) {
		super.onAddPeers(message, session);

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

	@Override
	protected void onRemovePeer(RemovePeerMessage message, Session session) {
		super.onRemovePeer(message, session);

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
}
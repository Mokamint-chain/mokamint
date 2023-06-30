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

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.api.IncompatiblePeerVersionException;
import io.mokamint.node.api.NodeListeners;
import io.mokamint.node.api.RestrictedNode;
import io.mokamint.node.messages.AddPeerMessage;
import io.mokamint.node.messages.AddPeerResultMessages;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.RemovePeerMessage;
import io.mokamint.node.messages.RemovePeerResultMessages;
import io.mokamint.node.service.AbstractRestrictedNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

/**
 * The implementation of a restricted node service. It publishes endpoints at a URL,
 * where clients can connect to query the restricted API of a Mokamint node.
 */
@ThreadSafe
public class RestrictedNodeServiceImpl extends AbstractRestrictedNodeService {

	/**
	 * The node whose API is published.
	 */
	private final RestrictedNode node;

	/**
	 * Creates a new service for the given node, at the given network port.
	 * 
	 * @param node the node
	 * @param port the port
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public RestrictedNodeServiceImpl(RestrictedNode node, int port) throws DeploymentException, IOException {
		super(port);
		this.node = node;

		if (node instanceof NodeListeners) {
			// TODO
			// ((NodeListeners) node).addOnPeerAddedListener(null);
		}

		deploy();
	}

	@Override
	public void close() {
		if (node instanceof NodeListeners) {
			// TODO
			// ((NodeListeners) node).removeOnPeerAddedListener(null);
		}

		super.close();
	}

	/**
	 * Sends an exception message to the given session.
	 * 
	 * @param session the session
	 * @param e the exception used to build the message
	 * @param id the identifier of the message to send
	 */
	private void sendExceptionAsync(Session session, Exception e, String id) {
		sendObjectAsync(session, ExceptionMessages.of(e, id));
	}

	@Override
	protected void onAddPeers(AddPeerMessage message, Session session) {
		super.onAddPeers(message, session);

		try {
			node.addPeer(message.getPeer());
			sendObjectAsync(session, AddPeerResultMessages.of(message.getId()));
		}
		catch (TimeoutException | InterruptedException | IOException | IncompatiblePeerVersionException e) {
			sendExceptionAsync(session, e, message.getId());
		}
	};

	@Override
	protected void onRemovePeer(RemovePeerMessage message, Session session) {
		super.onRemovePeer(message, session);

		try {
			node.removePeer(message.getPeer());
			sendObjectAsync(session, RemovePeerResultMessages.of(message.getId()));
		}
		catch (TimeoutException | InterruptedException e) {
			sendExceptionAsync(session, e, message.getId());
		}
	};
}
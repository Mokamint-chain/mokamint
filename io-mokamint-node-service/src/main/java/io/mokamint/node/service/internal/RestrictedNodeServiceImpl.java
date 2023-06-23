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
import io.mokamint.node.api.RestrictedNode;
import io.mokamint.node.messages.AddPeersMessage;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.RemovePeersMessage;
import io.mokamint.node.messages.VoidMessages;
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
		deploy();
	}

	/**
	 * Sends an exception message to the given session.
	 * 
	 * @param session the session
	 * @param e the exception used to build the message
	 * @param id the identifier of the message to send
	 */
	protected void sendExceptionAsync(Session session, Exception e, String id) {
		sendObjectAsync(session, ExceptionMessages.of(e, id));
	}

	@Override
	protected void onAddPeers(AddPeersMessage message, Session session) {
		super.onAddPeers(message, session);

		try {
			node.addPeers(message.getPeers());
			sendObjectAsync(session, VoidMessages.of(message.getId()));
		}
		catch (TimeoutException | InterruptedException e) {
			sendExceptionAsync(session, e, message.getId());
		}
	};

	@Override
	protected void onRemovePeers(RemovePeersMessage message, Session session) {
		super.onRemovePeers(message, session);

		try {
			node.removePeers(message.getPeers());
			sendObjectAsync(session, VoidMessages.of(message.getId()));
		}
		catch (TimeoutException | InterruptedException e) {
			sendExceptionAsync(session, e, message.getId());
		}
	};
}
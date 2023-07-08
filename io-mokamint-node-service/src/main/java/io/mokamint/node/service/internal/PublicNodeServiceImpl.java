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
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeListeners;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.GetBlockMessage;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoMessage;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetConfigMessage;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetInfoMessage;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.GetPeersMessage;
import io.mokamint.node.messages.GetPeersResultMessages;
import io.mokamint.node.service.AbstractPublicNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

/**
 * The implementation of a public node service. It publishes endpoints at a URL,
 * where clients can connect to query the public API of a Mokamint node.
 */
@ThreadSafe
public class PublicNodeServiceImpl extends AbstractPublicNodeService {

	/**
	 * The node whose API is published.
	 */
	private final PublicNode node;

	/**
	 * We need this intermediate definition since two instances of a method reference
	 * are not the same, nor equals.
	 */
	private final Consumer<Stream<Peer>> onPeerAddedListener = this::sendPeersSuggestion;

	/**
	 * Creates a new service for the given node, at the given network port.
	 * 
	 * @param node the node
	 * @param port the port
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public PublicNodeServiceImpl(PublicNode node, int port) throws DeploymentException, IOException {
		super(port);
		this.node = node;

		if (node instanceof NodeListeners nl)
			nl.addOnPeerAddedListener(onPeerAddedListener);

		deploy();
	}

	@Override
	public void close() {
		if (node instanceof NodeListeners nl)
			nl.removeOnPeerAddedListener(onPeerAddedListener);

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
	protected void onGetInfo(GetInfoMessage message, Session session) {
		super.onGetInfo(message, session);

		try {
			sendObjectAsync(session, GetInfoResultMessages.of(node.getInfo(), message.getId()));
		}
		catch (TimeoutException | InterruptedException e) {
			sendExceptionAsync(session, e, message.getId());
		}
	};

	@Override
	protected void onGetPeers(GetPeersMessage message, Session session) {
		super.onGetPeers(message, session);

		try {
			sendObjectAsync(session, GetPeersResultMessages.of(node.getPeers(), message.getId()));
		}
		catch (TimeoutException | InterruptedException e) {
			sendExceptionAsync(session, e, message.getId());
		}
	};

	@Override
	protected void onGetBlock(GetBlockMessage message, Session session) {
		super.onGetBlock(message, session);

		try {
			sendObjectAsync(session, GetBlockResultMessages.of(node.getBlock(message.getHash()), message.getId()));
		}
		catch (DatabaseException | NoSuchAlgorithmException | TimeoutException | InterruptedException e) {
			sendExceptionAsync(session, e, message.getId());
		}
	};

	@Override
	protected void onGetConfig(GetConfigMessage message, Session session) {
		super.onGetConfig(message, session);

		try {
			sendObjectAsync(session, GetConfigResultMessages.of(node.getConfig(), message.getId()));
		}
		catch (TimeoutException | InterruptedException e) {
			sendExceptionAsync(session, e, message.getId());
		}
	};

	@Override
	protected void onGetChainInfo(GetChainInfoMessage message, Session session) {
		super.onGetChainInfo(message, session);

		try {
			sendObjectAsync(session, GetChainInfoResultMessages.of(node.getChainInfo(), message.getId()));
		}
		catch (TimeoutException | InterruptedException | NoSuchAlgorithmException | DatabaseException e) {
			sendExceptionAsync(session, e, message.getId());
		}
	};
}
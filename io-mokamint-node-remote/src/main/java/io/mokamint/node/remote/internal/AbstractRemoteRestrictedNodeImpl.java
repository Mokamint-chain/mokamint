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

package io.mokamint.node.remote.internal;

import static io.mokamint.node.service.api.RestrictedNodeService.ADD_PEER_ENDPOINT;
import static io.mokamint.node.service.api.RestrictedNodeService.REMOVE_PEER_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.RpcMessage;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.AddPeerMessages;
import io.mokamint.node.messages.AddPeerResultMessage;
import io.mokamint.node.messages.AddPeerResultMessages;
import io.mokamint.node.messages.ExceptionMessage;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.RemovePeerMessages;
import io.mokamint.node.messages.RemovePeerResultMessage;
import io.mokamint.node.messages.RemovePeerResultMessages;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

/**
 * An implementation of a remote node that presents a programmatic interface
 * to a service for the restricted API of a Mokamint node.
 */
@ThreadSafe
public abstract class AbstractRemoteRestrictedNodeImpl extends AbstractRemoteNode {

	private final static Logger LOGGER = Logger.getLogger(AbstractRemoteRestrictedNodeImpl.class.getName());

	/**
	 * Opens and yields a new remote node for the restricted API of a node.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	public AbstractRemoteRestrictedNodeImpl(URI uri) throws DeploymentException, IOException {
		addSession(ADD_PEER_ENDPOINT, uri, AddPeerEndpoint::new);
		addSession(REMOVE_PEER_ENDPOINT, uri, RemovePeerEndpoint::new);
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		if (message instanceof AddPeerResultMessage)
			onAddPeerResult();
		else if (message instanceof RemovePeerResultMessage)
			onRemovePeerResult();
		else if (message == null)
			LOGGER.log(Level.SEVERE, "unexpected null message");
		else
			LOGGER.log(Level.SEVERE, "unexpected message", message.getClass().getName());
	}

	protected void sendAddPeer(Peer peer, String id) {
		sendObjectAsync(getSession(ADD_PEER_ENDPOINT), AddPeerMessages.of(peer, id));
	}

	protected void sendRemovePeer(Peer peer, String id) {
		sendObjectAsync(getSession(REMOVE_PEER_ENDPOINT), RemovePeerMessages.of(peer, id));
	}

	/**
	 * Handlers that can be overridden in subclasses.
	 */
	protected void onAddPeerResult() {}
	protected void onRemovePeerResult() {}
	protected void onException(ExceptionMessage message) {}

	private class AddPeerEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, AddPeerResultMessages.Decoder.class, ExceptionMessages.Decoder.class, AddPeerMessages.Encoder.class);
		}
	}

	private class RemovePeerEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, RemovePeerResultMessages.Decoder.class, ExceptionMessages.Decoder.class, RemovePeerMessages.Encoder.class);
		}
	}
}
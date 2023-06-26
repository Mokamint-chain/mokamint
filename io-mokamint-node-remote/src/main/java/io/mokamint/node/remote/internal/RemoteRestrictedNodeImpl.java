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
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.RpcMessage;
import io.mokamint.node.api.IncompatiblePeerVersionException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.AddPeerMessages;
import io.mokamint.node.messages.ExceptionMessage;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.RemovePeerMessages;
import io.mokamint.node.messages.VoidMessage;
import io.mokamint.node.messages.VoidMessages;
import io.mokamint.node.remote.RemoteRestrictedNode;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

/**
 * An implementation of a remote node that presents a programmatic interface
 * to a service for the restricted API of a Mokamint node.
 */
@ThreadSafe
public class RemoteRestrictedNodeImpl extends AbstractRemoteNode implements RemoteRestrictedNode {

	private final NodeMessageQueues queues;

	private final static Logger LOGGER = Logger.getLogger(RemoteRestrictedNodeImpl.class.getName());

	/**
	 * Opens and yields a new remote node for the restricted API of a node.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 * @return the new remote node
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	public RemoteRestrictedNodeImpl(URI uri, long timeout) throws DeploymentException, IOException {
		this.queues = new NodeMessageQueues(timeout);

		addSession(ADD_PEER_ENDPOINT, uri, AddPeersEndpoint::new);
		addSession(REMOVE_PEER_ENDPOINT, uri, RemovePeersEndpoint::new);
	}

	private RuntimeException unexpectedException(Exception e) {
		LOGGER.log(Level.SEVERE, "unexpected exception", e);
		return new RuntimeException("unexpected exception", e);
	}

	private boolean processStandardExceptions(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return TimeoutException.class.isAssignableFrom(clazz) || InterruptedException.class.isAssignableFrom(clazz);
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		queues.notifyResult(message);
	}

	private VoidMessage processVoidSuccess(RpcMessage message) {
		return message instanceof VoidMessage ? (VoidMessage) message : null;
	}

	@Override
	public void addPeer(Peer peer) throws IncompatiblePeerVersionException, IOException, TimeoutException, InterruptedException {
		var id = queues.nextId();
		sendObjectAsync(getSession(ADD_PEER_ENDPOINT), AddPeerMessages.of(peer, id));
		try {
			queues.waitForResult(id, this::processVoidSuccess, this::processAddPeerException);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | IOException | IncompatiblePeerVersionException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private boolean processAddPeerException(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return IncompatiblePeerVersionException.class.isAssignableFrom(clazz) ||
			IOException.class.isAssignableFrom(clazz) ||
			TimeoutException.class.isAssignableFrom(clazz) ||
			InterruptedException.class.isAssignableFrom(clazz);
	}

	@Override
	public void removePeer(Peer peer) throws TimeoutException, InterruptedException {
		var id = queues.nextId();
		sendObjectAsync(getSession(REMOVE_PEER_ENDPOINT), RemovePeerMessages.of(peer, id));
		try {
			queues.waitForResult(id, this::processVoidSuccess, this::processStandardExceptions);
		}
		catch (RuntimeException | TimeoutException | InterruptedException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private class AddPeersEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, VoidMessages.Decoder.class, ExceptionMessages.Decoder.class, AddPeerMessages.Encoder.class);
		}
	}

	private class RemovePeersEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, VoidMessages.Decoder.class, ExceptionMessages.Decoder.class, RemovePeerMessages.Encoder.class);
		}
	}
}
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

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.RpcMessage;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerVersionException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.AddPeerResultMessage;
import io.mokamint.node.messages.ExceptionMessage;
import io.mokamint.node.messages.RemovePeerResultMessage;
import io.mokamint.node.remote.AbstractRemoteRestrictedNode;
import io.mokamint.node.remote.RemoteRestrictedNode;
import jakarta.websocket.DeploymentException;

/**
 * An implementation of a remote node that presents a programmatic interface
 * to a service for the restricted API of a Mokamint node.
 */
@ThreadSafe
public class RemoteRestrictedNodeImpl extends AbstractRemoteRestrictedNode implements RemoteRestrictedNode {

	private final NodeMessageQueues queues;

	private final static Logger LOGGER = Logger.getLogger(RemoteRestrictedNodeImpl.class.getName());

	/**
	 * Opens and yields a new remote node for the restricted API of a node.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	public RemoteRestrictedNodeImpl(URI uri, long timeout) throws DeploymentException, IOException {
		super(uri);

		this.queues = new NodeMessageQueues(timeout);
	}

	private RuntimeException unexpectedException(Exception e) {
		LOGGER.log(Level.SEVERE, "unexpected exception", e);
		return new RuntimeException("unexpected exception", e);
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		super.notifyResult(message);
		queues.notifyResult(message);
	}

	@Override
	public void addPeer(Peer peer) throws IncompatiblePeerVersionException, DatabaseException, IOException, TimeoutException, InterruptedException {
		var id = queues.nextId();
		sendAddPeer(peer, id);
		try {
			queues.waitForResult(id, this::processAddPeerSuccess, this::processAddPeerException);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | DatabaseException | IOException | IncompatiblePeerVersionException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private AddPeerResultMessage processAddPeerSuccess(RpcMessage message) {
		return message instanceof AddPeerResultMessage ? (AddPeerResultMessage) message : null;
	}

	private boolean processAddPeerException(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return IncompatiblePeerVersionException.class.isAssignableFrom(clazz) ||
			DatabaseException.class.isAssignableFrom(clazz) ||
			IOException.class.isAssignableFrom(clazz) ||
			TimeoutException.class.isAssignableFrom(clazz) ||
			InterruptedException.class.isAssignableFrom(clazz);
	}

	@Override
	public void removePeer(Peer peer) throws DatabaseException, TimeoutException, InterruptedException {
		var id = queues.nextId();
		sendRemovePeer(peer, id);
		try {
			queues.waitForResult(id, this::processRemovePeerSuccess, this::processRemovePeerException);
		}
		catch (RuntimeException | DatabaseException | TimeoutException | InterruptedException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private RemovePeerResultMessage processRemovePeerSuccess(RpcMessage message) {
		return message instanceof RemovePeerResultMessage ? (RemovePeerResultMessage) message : null;
	}

	private boolean processRemovePeerException(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return DatabaseException.class.isAssignableFrom(clazz) ||
			TimeoutException.class.isAssignableFrom(clazz) ||
			InterruptedException.class.isAssignableFrom(clazz);
	}
}
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
import static io.mokamint.node.service.api.RestrictedNodeService.CLOSE_MINER_ENDPOINT;
import static io.mokamint.node.service.api.RestrictedNodeService.OPEN_MINER_ENDPOINT;
import static io.mokamint.node.service.api.RestrictedNodeService.REMOVE_PEER_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.messages.AddPeerMessages;
import io.mokamint.node.messages.AddPeerResultMessages;
import io.mokamint.node.messages.CloseMinerMessages;
import io.mokamint.node.messages.CloseMinerResultMessages;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.OpenMinerMessages;
import io.mokamint.node.messages.OpenMinerResultMessages;
import io.mokamint.node.messages.RemovePeerMessages;
import io.mokamint.node.messages.RemovePeerResultMessages;
import io.mokamint.node.messages.api.AddPeerResultMessage;
import io.mokamint.node.messages.api.CloseMinerResultMessage;
import io.mokamint.node.messages.api.ExceptionMessage;
import io.mokamint.node.messages.api.OpenMinerResultMessage;
import io.mokamint.node.messages.api.RemovePeerResultMessage;
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

	/**
	 * The prefix used in the log messages;
	 */
	private final String logPrefix;

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
		this.logPrefix = "remote to restricted service at " + uri + ": ";
		this.queues = new NodeMessageQueues(timeout);
		
		addSession(ADD_PEER_ENDPOINT, uri, AddPeerEndpoint::new);
		addSession(REMOVE_PEER_ENDPOINT, uri, RemovePeerEndpoint::new);
		addSession(OPEN_MINER_ENDPOINT, uri, OpenMinerEndpoint::new);
		addSession(CLOSE_MINER_ENDPOINT, uri, CloseMinerEndpoint::new);
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		if (message instanceof AddPeerResultMessage)
			onAddPeerResult();
		else if (message instanceof RemovePeerResultMessage)
			onRemovePeerResult();
		else if (message instanceof OpenMinerResultMessage)
			onOpenMinerResult();
		else if (message instanceof CloseMinerResultMessage)
			onCloseMinerResult();
		else if (message instanceof ExceptionMessage em)
			onException(em);
		else if (message == null) {
			LOGGER.log(Level.SEVERE, logPrefix + "unexpected null message");
			return;
		}
		else {
			LOGGER.log(Level.SEVERE, logPrefix + "unexpected message of class " + message.getClass().getName());
			return;
		}

		queues.notifyResult(message);
	}

	protected void sendAddPeer(Peer peer, String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(ADD_PEER_ENDPOINT), AddPeerMessages.of(peer, id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	protected void sendRemovePeer(Peer peer, String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(REMOVE_PEER_ENDPOINT), RemovePeerMessages.of(peer, id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	protected void sendOpenMiner(int port, String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(OPEN_MINER_ENDPOINT), OpenMinerMessages.of(port, id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	protected void sendCloseMiner(UUID uuid, String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(CLOSE_MINER_ENDPOINT), CloseMinerMessages.of(uuid, id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private RuntimeException unexpectedException(Exception e) {
		LOGGER.log(Level.SEVERE, logPrefix + "unexpected exception", e);
		return new RuntimeException("Unexpected exception", e);
	}

	/**
	 * Hooks that can be overridden in subclasses.
	 */
	protected void onAddPeerResult() {}
	protected void onRemovePeerResult() {}
	protected void onOpenMinerResult() {}
	protected void onCloseMinerResult() {}
	protected void onException(ExceptionMessage message) {}

	@Override
	public boolean add(Peer peer) throws PeerRejectedException, DatabaseException, IOException, TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendAddPeer(peer, id);
		try {
			return queues.waitForResult(id, this::processAddPeerSuccess, this::processAddPeerException);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | ClosedNodeException | DatabaseException | IOException | PeerRejectedException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private class AddPeerEndpoint extends Endpoint {
	
		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, AddPeerResultMessages.Decoder.class, ExceptionMessages.Decoder.class, AddPeerMessages.Encoder.class);
		}
	}

	private Boolean processAddPeerSuccess(RpcMessage message) {
		return message instanceof AddPeerResultMessage aprm ? aprm.get() : null;
	}

	private boolean processAddPeerException(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return PeerRejectedException.class.isAssignableFrom(clazz) ||
			DatabaseException.class.isAssignableFrom(clazz) ||
			IOException.class.isAssignableFrom(clazz) ||
			processStandardExceptions(message);
	}

	@Override
	public boolean remove(Peer peer) throws DatabaseException, TimeoutException, InterruptedException, ClosedNodeException, IOException {
		ensureIsOpen();
		var id = queues.nextId();
		sendRemovePeer(peer, id);
		try {
			return queues.waitForResult(id, this::processRemovePeerSuccess, this::processRemovePeerException);
		}
		catch (RuntimeException | DatabaseException | TimeoutException | InterruptedException | ClosedNodeException | IOException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private class RemovePeerEndpoint extends Endpoint {
	
		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, RemovePeerResultMessages.Decoder.class, ExceptionMessages.Decoder.class, RemovePeerMessages.Encoder.class);
		}
	}

	private Boolean processRemovePeerSuccess(RpcMessage message) {
		return message instanceof RemovePeerResultMessage rprm ? rprm.get() : null;
	}

	private boolean processRemovePeerException(ExceptionMessage message) {
		var exception = message.getExceptionClass();
		return DatabaseException.class.isAssignableFrom(exception) ||
			IOException.class.isAssignableFrom(exception) ||
			processStandardExceptions(message);
	}

	private class OpenMinerEndpoint extends Endpoint {
		
		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, OpenMinerResultMessages.Decoder.class, ExceptionMessages.Decoder.class, OpenMinerMessages.Encoder.class);
		}
	}

	@Override
	public boolean openMiner(int port) throws TimeoutException, IOException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendOpenMiner(port, id);
		try {
			return queues.waitForResult(id, this::processOpenMinerSuccess, this::processOpenMinerException);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | ClosedNodeException | IOException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private Boolean processOpenMinerSuccess(RpcMessage message) {
		return message instanceof OpenMinerResultMessage omrm ? omrm.get() : null;
	}

	private boolean processOpenMinerException(ExceptionMessage message) {
		var exception = message.getExceptionClass();
		return IOException.class.isAssignableFrom(exception) || processStandardExceptions(message);
	}

	private class CloseMinerEndpoint extends Endpoint {
		
		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, CloseMinerResultMessages.Decoder.class, ExceptionMessages.Decoder.class, CloseMinerMessages.Encoder.class);
		}
	}

	@Override
	public boolean closeMiner(UUID uuid) throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendCloseMiner(uuid, id);
		try {
			return queues.waitForResult(id, this::processCloseMinerSuccess, this::processStandardExceptions);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private Boolean processCloseMinerSuccess(RpcMessage message) {
		return message instanceof CloseMinerResultMessage cmrm ? cmrm.get() : null;
	}
}
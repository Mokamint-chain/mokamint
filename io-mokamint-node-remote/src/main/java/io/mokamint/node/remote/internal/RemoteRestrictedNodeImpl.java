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
import static io.mokamint.node.service.api.RestrictedNodeService.OPEN_MINER_ENDPOINT;
import static io.mokamint.node.service.api.RestrictedNodeService.REMOVE_MINER_ENDPOINT;
import static io.mokamint.node.service.api.RestrictedNodeService.REMOVE_PEER_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.hotmoka.websockets.beans.api.ExceptionMessage;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerException;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.messages.AddPeerMessages;
import io.mokamint.node.messages.AddPeerResultMessages;
import io.mokamint.node.messages.OpenMinerMessages;
import io.mokamint.node.messages.OpenMinerResultMessages;
import io.mokamint.node.messages.RemoveMinerMessages;
import io.mokamint.node.messages.RemoveMinerResultMessages;
import io.mokamint.node.messages.RemovePeerMessages;
import io.mokamint.node.messages.RemovePeerResultMessages;
import io.mokamint.node.messages.api.AddPeerResultMessage;
import io.mokamint.node.messages.api.OpenMinerResultMessage;
import io.mokamint.node.messages.api.RemoveMinerResultMessage;
import io.mokamint.node.messages.api.RemovePeerResultMessage;
import io.mokamint.node.remote.api.RemoteRestrictedNode;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

/**
 * An implementation of a remote node that presents a programmatic interface
 * to a service for the restricted API of a Mokamint node.
 */
@ThreadSafe
public class RemoteRestrictedNodeImpl extends AbstractRemoteNode implements RemoteRestrictedNode {

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
	 * @throws FailedDeploymentException if the remote node could not be created
	 */
	public RemoteRestrictedNodeImpl(URI uri, int timeout) throws FailedDeploymentException {
		super(timeout);

		this.logPrefix = "restricted remote(" + uri + "): ";

		try {
			addSession(ADD_PEER_ENDPOINT, uri, AddPeerEndpoint::new);
			addSession(REMOVE_PEER_ENDPOINT, uri, RemovePeerEndpoint::new);
			addSession(OPEN_MINER_ENDPOINT, uri, OpenMinerEndpoint::new);
			addSession(REMOVE_MINER_ENDPOINT, uri, RemoveMinerEndpoint::new);
		}
		catch (IOException | DeploymentException e) {
			throw new FailedDeploymentException(e);
		}
	}

	@Override
	protected void closeResources(CloseReason reason) {
		super.closeResources(reason);
		LOGGER.info(logPrefix + "closed with reason: " + reason);
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		if (message instanceof AddPeerResultMessage)
			onAddPeerResult();
		else if (message instanceof RemovePeerResultMessage)
			onRemovePeerResult();
		else if (message instanceof OpenMinerResultMessage)
			onOpenMinerResult();
		else if (message instanceof RemoveMinerResultMessage)
			onCloseMinerResult();
		else if (message != null && !(message instanceof ExceptionMessage)) {
			LOGGER.warning(logPrefix + "unexpected message of class " + message.getClass().getName());
			return;
		}

		super.notifyResult(message);
	}

	protected void sendAddPeer(Peer peer, String id) throws NodeException {
		sendObjectAsync(getSession(ADD_PEER_ENDPOINT), AddPeerMessages.of(peer, id), NodeException::new);
	}

	protected void sendRemovePeer(Peer peer, String id) throws NodeException {
		sendObjectAsync(getSession(REMOVE_PEER_ENDPOINT), RemovePeerMessages.of(peer, id), NodeException::new);
	}

	protected void sendOpenMiner(int port, String id) throws NodeException {
		sendObjectAsync(getSession(OPEN_MINER_ENDPOINT), OpenMinerMessages.of(port, id), NodeException::new);
	}

	protected void sendRemoveMiner(UUID uuid, String id) throws NodeException {
		sendObjectAsync(getSession(REMOVE_MINER_ENDPOINT), RemoveMinerMessages.of(uuid, id), NodeException::new);
	}

	/**
	 * Hooks that can be overridden in subclasses.
	 */
	protected void onAddPeerResult() {}
	protected void onRemovePeerResult() {}
	protected void onOpenMinerResult() {}
	protected void onCloseMinerResult() {}

	@Override
	public Optional<PeerInfo> add(Peer peer) throws PeerRejectedException, PeerException, TimeoutException, InterruptedException, NodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendAddPeer(peer, id);
		return waitForResult(id, AddPeerResultMessage.class, TimeoutException.class, NodeException.class, PeerException.class, PeerRejectedException.class);
	}

	private class AddPeerEndpoint extends Endpoint {
	
		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, AddPeerResultMessages.Decoder.class, ExceptionMessages.Decoder.class, AddPeerMessages.Encoder.class);
		}
	}

	@Override
	public boolean remove(Peer peer) throws TimeoutException, InterruptedException, NodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendRemovePeer(peer, id);
		return waitForResult(id, RemovePeerResultMessage.class, TimeoutException.class, NodeException.class);
	}

	private class RemovePeerEndpoint extends Endpoint {
	
		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, RemovePeerResultMessages.Decoder.class, ExceptionMessages.Decoder.class, RemovePeerMessages.Encoder.class);
		}
	}

	@Override
	public Optional<MinerInfo> openMiner(int port) throws TimeoutException, FailedDeploymentException, InterruptedException, NodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendOpenMiner(port, id);
		return waitForResult(id, OpenMinerResultMessage.class, TimeoutException.class, NodeException.class, FailedDeploymentException.class);
	}

	private class OpenMinerEndpoint extends Endpoint {
		
		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, OpenMinerResultMessages.Decoder.class, ExceptionMessages.Decoder.class, OpenMinerMessages.Encoder.class);
		}
	}

	@Override
	public boolean removeMiner(UUID uuid) throws TimeoutException, InterruptedException, NodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendRemoveMiner(uuid, id);
		return waitForResult(id, RemoveMinerResultMessage.class, TimeoutException.class, NodeException.class);
	}

	private class RemoveMinerEndpoint extends Endpoint {
		
		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, RemoveMinerResultMessages.Decoder.class, ExceptionMessages.Decoder.class, RemoveMinerMessages.Encoder.class);
		}
	}
}
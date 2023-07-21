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

import static io.mokamint.node.service.api.PublicNodeService.SUGGEST_PEERS_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.RpcMessage;
import io.mokamint.node.ListenerManager;
import io.mokamint.node.ListenerManagers;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.messages.ExceptionMessage;
import io.mokamint.node.messages.GetBlockResultMessage;
import io.mokamint.node.messages.GetChainInfoResultMessage;
import io.mokamint.node.messages.GetConfigResultMessage;
import io.mokamint.node.messages.GetInfoResultMessage;
import io.mokamint.node.messages.GetPeersResultMessage;
import io.mokamint.node.messages.SuggestPeersMessage;
import io.mokamint.node.messages.SuggestPeersMessages;
import io.mokamint.node.remote.AbstractRemotePublicNode;
import io.mokamint.node.remote.RemotePublicNode;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * An implementation of a remote node that presents a programmatic interface
 * to a service for the public API of a Mokamint node.
 */
@ThreadSafe
public class RemotePublicNodeImpl extends AbstractRemotePublicNode implements RemotePublicNode {

	private final NodeMessageQueues queues;

	/**
	 * The listeners called whenever a peer is added to this node.
	 */
	private final ListenerManager<Stream<Peer>> onPeersAddedListeners = ListenerManagers.mk();

	private final static Logger LOGGER = Logger.getLogger(RemotePublicNodeImpl.class.getName());

	/**
	 * Opens and yields a new remote node for the public API of a node.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	public RemotePublicNodeImpl(URI uri, long timeout) throws DeploymentException, IOException {
		super(uri);

		addSession(SUGGEST_PEERS_ENDPOINT, uri, SuggestPeersEndpoint::new);

		this.queues = new NodeMessageQueues(timeout);
	}

	@Override
	public void addOnPeersAddedListener(Consumer<Stream<Peer>> listener) {
		onPeersAddedListeners.add(listener);
	}

	@Override
	public void removeOnPeersAddedListener(Consumer<Stream<Peer>> listener) {
		onPeersAddedListeners.remove(listener);
	}

	/**
	 * Called when the bound service suggests to add some peers.
	 * 
	 * @param message the message containing the suggested peers
	 */
	protected void onSuggestPeers(SuggestPeersMessage message) {
		onPeersAddedListeners.getListeners().forEach(listener -> listener.accept(message.getPeers()));
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
	public NodeInfo getInfo() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetInfo(id);
		try {
			return queues.waitForResult(id, this::processGetInfoSuccess, this::processStandardExceptions);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private NodeInfo processGetInfoSuccess(RpcMessage message) {
		return message instanceof GetInfoResultMessage girm ? girm.get() : null;
	}

	@Override
	public Stream<PeerInfo> getPeerInfos() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetPeers(id);
		try {
			return queues.waitForResult(id, this::processGetPeersSuccess, this::processStandardExceptions);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private Stream<PeerInfo> processGetPeersSuccess(RpcMessage message) {
		return message instanceof GetPeersResultMessage gprm ? gprm.get() : null;
	}

	@Override
	public Optional<Block> getBlock(byte[] hash) throws DatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetBlock(hash, id);
		try {
			return queues.waitForResult(id, this::processGetBlockSuccess, this::processGetBlockException);
		}
		catch (RuntimeException | DatabaseException | NoSuchAlgorithmException | TimeoutException | InterruptedException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private Optional<Block> processGetBlockSuccess(RpcMessage message) {
		return message instanceof GetBlockResultMessage gbrm ? gbrm.get() : null;
	}

	private boolean processGetBlockException(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return DatabaseException.class.isAssignableFrom(clazz) ||
			NoSuchAlgorithmException.class.isAssignableFrom(clazz) ||
			processStandardExceptions(message);
	}

	@Override
	public ConsensusConfig getConfig() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetConfig(id);
		try {
			return queues.waitForResult(id, this::processGetConfigSuccess, this::processStandardExceptions);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private ConsensusConfig processGetConfigSuccess(RpcMessage message) {
		return message instanceof GetConfigResultMessage gcrm ? gcrm.get() : null;
	}

	@Override
	public ChainInfo getChainInfo() throws NoSuchAlgorithmException, DatabaseException, TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetChainInfo(id);
		try {
			return queues.waitForResult(id, this::processGetChainInfoSuccess, this::processGetChainInfoException);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | NoSuchAlgorithmException | DatabaseException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private ChainInfo processGetChainInfoSuccess(RpcMessage message) {
		return message instanceof GetChainInfoResultMessage gcirm ? gcirm.get() : null;
	}

	private boolean processGetChainInfoException(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return NoSuchAlgorithmException.class.isAssignableFrom(clazz) ||
			DatabaseException.class.isAssignableFrom(clazz) ||
			processStandardExceptions(message);
	}

	private class SuggestPeersEndpoint extends Endpoint {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, RemotePublicNodeImpl.this::onSuggestPeers);
		}

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, SuggestPeersMessages.Decoder.class);
		}
	}
}
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

import static io.mokamint.node.service.api.PublicNodeService.GET_BLOCK_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_CHAIN_INFO_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_CONFIG_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_INFO_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_PEER_INFOS_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.WHISPER_PEERS_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.RpcMessage;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.messages.ExceptionMessage;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessage;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoMessages;
import io.mokamint.node.messages.GetChainInfoResultMessage;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetConfigMessages;
import io.mokamint.node.messages.GetConfigResultMessage;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetInfoMessages;
import io.mokamint.node.messages.GetInfoResultMessage;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.GetPeerInfosMessages;
import io.mokamint.node.messages.GetPeerInfosResultMessage;
import io.mokamint.node.messages.GetPeerInfosResultMessages;
import io.mokamint.node.messages.WhisperPeersMessage;
import io.mokamint.node.messages.WhisperPeersMessages;
import io.mokamint.node.remote.RemotePublicNode;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * An implementation of a remote node that presents a programmatic interface
 * to a service for the public API of a Mokamint node.
 */
@ThreadSafe
public class RemotePublicNodeImpl extends AbstractRemoteNode implements RemotePublicNode {

	private final NodeMessageQueues queues;

	/**
	 * The code to execute when this node has some peers to whisper.
	 */
	private final CopyOnWriteArrayList<Consumer<Stream<Peer>>> onWhisperPeersHandlers = new CopyOnWriteArrayList<>();

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
		addSession(GET_PEER_INFOS_ENDPOINT, uri, GetPeersEndpoint::new);
		addSession(GET_BLOCK_ENDPOINT, uri, GetBlockEndpoint::new);
		addSession(GET_CONFIG_ENDPOINT, uri, GetConfigEndpoint::new);
		addSession(GET_CHAIN_INFO_ENDPOINT, uri, GetChainInfoEndpoint::new);
		addSession(GET_INFO_ENDPOINT, uri, GetInfoEndpoint::new);
		addSession(WHISPER_PEERS_ENDPOINT, uri, WhisperPeersEndpoint::new);

		this.queues = new NodeMessageQueues(timeout);
	}

	@Override
	public void addOnWhisperPeersHandler(Consumer<Stream<Peer>> handler) {
		onWhisperPeersHandlers.add(handler);
	}

	@Override
	public void removeOnWhisperPeersHandler(Consumer<Stream<Peer>> handler) {
		onWhisperPeersHandlers.remove(handler);
	}

	@Override
	public void whisperToPeers(Stream<Peer> peers) {
		try {
			sendObjectAsync(getSession(WHISPER_PEERS_ENDPOINT), WhisperPeersMessages.of(peers));
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot whisper peers to the connected service: the connection might be closed", e);
		}
	}

	@Override
	public void whisperToServices(Stream<Peer> peers) {
		var peersAsArray = peers.toArray(Peer[]::new);
		onWhisperPeersHandlers.stream().forEach(handler -> handler.accept(Stream.of(peersAsArray)));
	}

	private RuntimeException unexpectedException(Exception e) {
		LOGGER.log(Level.SEVERE, "unexpected exception", e);
		return new RuntimeException("unexpected exception", e);
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		if (message instanceof GetInfoResultMessage girm)
			onGetInfoResult(girm.get());
		else if (message instanceof GetPeerInfosResultMessage gprm)
			onGetPeerInfosResult(gprm.get());
		else if (message instanceof GetBlockResultMessage gbrm)
			onGetBlockResult(gbrm.get());
		else if (message instanceof GetConfigResultMessage gcrm)
			onGetConfigResult(gcrm.get());
		else if (message instanceof GetChainInfoResultMessage gcirm)
			onGetChainInfoResult(gcirm.get());
		else if (message instanceof ExceptionMessage em)
			onException(em);
		else if (message == null) {
			LOGGER.log(Level.SEVERE, "unexpected null message");
			return;
		}
		else {
			LOGGER.log(Level.SEVERE, "unexpected message of class " + message.getClass().getName());
			return;
		}

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

	protected void sendGetInfo(String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_INFO_ENDPOINT), GetInfoMessages.of(id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private NodeInfo processGetInfoSuccess(RpcMessage message) {
		return message instanceof GetInfoResultMessage girm ? girm.get() : null;
	}

	@Override
	public Stream<PeerInfo> getPeerInfos() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetPeerInfos(id);
		try {
			return queues.waitForResult(id, this::processGetPeerInfosSuccess, this::processStandardExceptions);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	protected void sendGetPeerInfos(String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_PEER_INFOS_ENDPOINT), GetPeerInfosMessages.of(id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private Stream<PeerInfo> processGetPeerInfosSuccess(RpcMessage message) {
		return message instanceof GetPeerInfosResultMessage gprm ? gprm.get() : null;
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

	protected void sendGetBlock(byte[] hash, String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_BLOCK_ENDPOINT), GetBlockMessages.of(hash, id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
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

	protected void sendGetConfig(String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_CONFIG_ENDPOINT), GetConfigMessages.of(id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
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

	protected void sendGetChainInfo(String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_CHAIN_INFO_ENDPOINT), GetChainInfoMessages.of(id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
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

	/**
	 * Handlers that can be overridden in subclasses.
	 */
	protected void onGetPeerInfosResult(Stream<PeerInfo> peers) {}
	protected void onGetBlockResult(Optional<Block> block) {}
	protected void onGetConfigResult(ConsensusConfig config) {}
	protected void onGetChainInfoResult(ChainInfo info) {}
	protected void onGetInfoResult(NodeInfo info) {}
	protected void onException(ExceptionMessage message) {}

	private class GetPeersEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetPeerInfosResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetPeerInfosMessages.Encoder.class);
		}
	}

	private class GetBlockEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetBlockResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetBlockMessages.Encoder.class);
		}
	}

	private class GetConfigEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetConfigResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetConfigMessages.Encoder.class);
		}
	}

	private class GetChainInfoEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetChainInfoResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetChainInfoMessages.Encoder.class);
		}
	}

	private class GetInfoEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetInfoResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetInfoMessages.Encoder.class);
		}
	}

	private class WhisperPeersEndpoint extends Endpoint {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (WhisperPeersMessage message) -> whisperToServices(message.getPeers()));
		}

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, WhisperPeersMessages.Decoder.class, WhisperPeersMessages.Encoder.class);
		}
	}
}
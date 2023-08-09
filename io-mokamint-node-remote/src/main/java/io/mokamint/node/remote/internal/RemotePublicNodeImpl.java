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
import static io.mokamint.node.service.api.PublicNodeService.GET_CHAIN_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_CHAIN_INFO_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_CONFIG_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_INFO_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_PEER_INFOS_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.WHISPER_BLOCK_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.WHISPER_PEERS_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.mokamint.node.SanitizedStrings;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.Chain;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoMessages;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetChainMessages;
import io.mokamint.node.messages.GetChainResultMessages;
import io.mokamint.node.messages.GetConfigMessages;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetInfoMessages;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.GetPeerInfosMessages;
import io.mokamint.node.messages.GetPeerInfosResultMessages;
import io.mokamint.node.messages.MessageMemories;
import io.mokamint.node.messages.MessageMemory;
import io.mokamint.node.messages.WhisperBlockMessages;
import io.mokamint.node.messages.WhisperPeersMessages;
import io.mokamint.node.messages.api.ExceptionMessage;
import io.mokamint.node.messages.api.GetBlockResultMessage;
import io.mokamint.node.messages.api.GetChainInfoResultMessage;
import io.mokamint.node.messages.api.GetChainResultMessage;
import io.mokamint.node.messages.api.GetConfigResultMessage;
import io.mokamint.node.messages.api.GetInfoResultMessage;
import io.mokamint.node.messages.api.GetPeerInfosResultMessage;
import io.mokamint.node.messages.api.WhisperBlockMessage;
import io.mokamint.node.messages.api.WhisperPeersMessage;
import io.mokamint.node.messages.api.Whisperer;
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
	 * The whisperers bound to this node.
	 */
	private final CopyOnWriteArrayList<Whisperer> boundWhisperers = new CopyOnWriteArrayList<>();

	/**
	 * A memory of the last whispered messages,
	 * This is used to avoid whispering already whispered messages again.
	 */
	private final MessageMemory whisperedMessages;

	private final static Logger LOGGER = Logger.getLogger(RemotePublicNodeImpl.class.getName());

	/**
	 * Opens and yields a new remote node for the public API of a node.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 * @param whisperedMessagesSize the size of the memory used to avoid whispering the same
	 *                              message again; higher numbers reduce the circulation of
	 *                              spurious messages
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	public RemotePublicNodeImpl(URI uri, long timeout, long whisperedMessagesSize) throws DeploymentException, IOException {
		this.whisperedMessages = MessageMemories.of(whisperedMessagesSize);

		addSession(GET_PEER_INFOS_ENDPOINT, uri, GetPeersEndpoint::new);
		addSession(GET_BLOCK_ENDPOINT, uri, GetBlockEndpoint::new);
		addSession(GET_CONFIG_ENDPOINT, uri, GetConfigEndpoint::new);
		addSession(GET_CHAIN_INFO_ENDPOINT, uri, GetChainInfoEndpoint::new);
		addSession(GET_CHAIN_ENDPOINT, uri, GetChainEndpoint::new);
		addSession(GET_INFO_ENDPOINT, uri, GetInfoEndpoint::new);
		addSession(WHISPER_PEERS_ENDPOINT, uri, WhisperPeersEndpoint::new);
		addSession(WHISPER_BLOCK_ENDPOINT, uri, WhisperBlockEndpoint::new);

		this.queues = new NodeMessageQueues(timeout);
	}

	@Override
	public void bindWhisperer(Whisperer whisperer) {
		boundWhisperers.add(whisperer);
	}

	@Override
	public void unbindWhisperer(Whisperer whisperer) {
		boundWhisperers.remove(whisperer);
	}

	@Override
	public void whisper(WhisperPeersMessage message, Predicate<Whisperer> seen) {
		whisper(message, seen, true);
	}

	@Override
	public void whisperItself(WhisperPeersMessage message, Predicate<Whisperer> seen) {
		whisper(message, seen);
	}

	@Override
	public void whisper(WhisperBlockMessage message, Predicate<Whisperer> seen) {
		whisper(message, seen, true);
	}

	private void whisper(WhisperBlockMessage message, Predicate<Whisperer> seen, boolean includeNetwork) {
		if (seen.test(this) || !whisperedMessages.add(message))
			return;

		LOGGER.info("remote: got whispered block"); // TODO

		onWhisperBlock(message);

		if (includeNetwork) {
			try {
				sendObjectAsync(getSession(WHISPER_BLOCK_ENDPOINT), message);
			}
			catch (IOException e) {
				LOGGER.log(Level.SEVERE, "cannot whisper a block to the connected service: the connection might be closed: " + e.getMessage());
			}
		}

		Predicate<Whisperer> newSeen = seen.or(Predicate.isEqual(this));
		boundWhisperers.forEach(whisperer -> whisperer.whisper(message, newSeen));
	}

	private void whisper(WhisperPeersMessage message, Predicate<Whisperer> seen, boolean includeNetwork) {
		if (seen.test(this) || !whisperedMessages.add(message))
			return;

		LOGGER.info("remote: got whispered peers " + SanitizedStrings.of(message.getPeers()));

		onWhisperPeers(message);

		if (includeNetwork) {
			try {
				sendObjectAsync(getSession(WHISPER_PEERS_ENDPOINT), message);
			}
			catch (IOException e) {
				LOGGER.log(Level.SEVERE, "cannot whisper peers to the connected service: the connection might be closed: " + e.getMessage());
			}
		}

		Predicate<Whisperer> newSeen = seen.or(Predicate.isEqual(this));
		boundWhisperers.forEach(whisperer -> whisperer.whisper(message, newSeen));
	}

	private RuntimeException unexpectedException(Exception e) {
		LOGGER.log(Level.SEVERE, "remote: unexpected exception", e);
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
		else if (message instanceof GetChainResultMessage gcrm)
			onGetChainResult(gcrm.get());
		else if (message instanceof ExceptionMessage em)
			onException(em);
		else if (message == null) {
			LOGGER.log(Level.SEVERE, "remote: unexpected null message");
			return;
		}
		else {
			LOGGER.log(Level.SEVERE, "remote: unexpected message of class " + message.getClass().getName());
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
	public ChainInfo getChainInfo() throws DatabaseException, TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetChainInfo(id);
		try {
			return queues.waitForResult(id, this::processGetChainInfoSuccess, this::processGetChainInfoException);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | DatabaseException | ClosedNodeException e) {
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
		return DatabaseException.class.isAssignableFrom(clazz) ||
			processStandardExceptions(message);
	}

	@Override
	public Chain getChain(long start, long count) throws DatabaseException, TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetChain(start, count, id);
		try {
			return queues.waitForResult(id, this::processGetChainSuccess, this::processGetChainException);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | DatabaseException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	protected void sendGetChain(long start, long count, String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_CHAIN_ENDPOINT), GetChainMessages.of(start, count, id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private Chain processGetChainSuccess(RpcMessage message) {
		return message instanceof GetChainResultMessage gcrm ? gcrm.get() : null;
	}

	private boolean processGetChainException(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return DatabaseException.class.isAssignableFrom(clazz) ||
			processStandardExceptions(message);
	}

	/**
	 * Hooks that can be overridden in subclasses.
	 */
	protected void onGetPeerInfosResult(Stream<PeerInfo> peers) {}
	protected void onGetBlockResult(Optional<Block> block) {}
	protected void onGetConfigResult(ConsensusConfig config) {}
	protected void onGetChainInfoResult(ChainInfo info) {}
	protected void onGetChainResult(Chain chain) {}
	protected void onGetInfoResult(NodeInfo info) {}
	protected void onException(ExceptionMessage message) {}
	protected void onWhisperPeers(WhisperPeersMessage message) {}
	protected void onWhisperBlock(WhisperBlockMessage message) {}

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

	private class GetChainEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetChainResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetChainMessages.Encoder.class);
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
			addMessageHandler(session, (WhisperPeersMessage message) -> whisper(message, _whisperer -> false, false));
		}

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, WhisperPeersMessages.Decoder.class, WhisperPeersMessages.Encoder.class);
		}
	}

	private class WhisperBlockEndpoint extends Endpoint {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (WhisperBlockMessage message) -> whisper(message, _whisperer -> false, false));
		}

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, WhisperBlockMessages.Decoder.class, WhisperBlockMessages.Encoder.class);
		}
	}
}
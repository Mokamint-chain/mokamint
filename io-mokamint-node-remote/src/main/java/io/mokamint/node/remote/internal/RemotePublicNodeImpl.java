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

import static io.mokamint.node.service.api.PublicNodeService.ADD_REQUEST_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_BLOCK_DESCRIPTION_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_BLOCK_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_CHAIN_INFO_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_CHAIN_PORTION_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_CONFIG_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_INFO_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_MEMPOOL_INFO_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_MEMPOOL_PORTION_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_MINER_INFOS_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_PEER_INFOS_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_TASK_INFOS_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_REQUEST_ADDRESS_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_REQUEST_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_REQUEST_REPRESENTATION_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.WHISPER_BLOCK_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.WHISPER_PEER_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.WHISPER_REQUEST_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.hotmoka.websockets.beans.api.ExceptionMessage;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.mokamint.node.Memories;
import io.mokamint.node.api.ApplicationTimeoutException;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.Memory;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.api.MempoolPortion;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PortionRejectedException;
import io.mokamint.node.api.TaskInfo;
import io.mokamint.node.api.Request;
import io.mokamint.node.api.RequestAddress;
import io.mokamint.node.api.RequestRejectedException;
import io.mokamint.node.api.WhisperMessage;
import io.mokamint.node.api.Whisperable;
import io.mokamint.node.api.Whisperer;
import io.mokamint.node.messages.AddRequestMessages;
import io.mokamint.node.messages.AddRequestResultMessages;
import io.mokamint.node.messages.GetBlockDescriptionMessages;
import io.mokamint.node.messages.GetBlockDescriptionResultMessages;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoMessages;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetChainPortionMessages;
import io.mokamint.node.messages.GetChainPortionResultMessages;
import io.mokamint.node.messages.GetConfigMessages;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetInfoMessages;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.GetMempoolInfoMessages;
import io.mokamint.node.messages.GetMempoolInfoResultMessages;
import io.mokamint.node.messages.GetMempoolPortionMessages;
import io.mokamint.node.messages.GetMempoolPortionResultMessages;
import io.mokamint.node.messages.GetMinerInfosMessages;
import io.mokamint.node.messages.GetMinerInfosResultMessages;
import io.mokamint.node.messages.GetPeerInfosMessages;
import io.mokamint.node.messages.GetPeerInfosResultMessages;
import io.mokamint.node.messages.GetTaskInfosMessages;
import io.mokamint.node.messages.GetTaskInfosResultMessages;
import io.mokamint.node.messages.GetRequestAddressMessages;
import io.mokamint.node.messages.GetRequestAddressResultMessages;
import io.mokamint.node.messages.GetRequestMessages;
import io.mokamint.node.messages.GetRequestRepresentationMessages;
import io.mokamint.node.messages.GetRequestRepresentationResultMessages;
import io.mokamint.node.messages.GetRequestResultMessages;
import io.mokamint.node.messages.WhisperBlockMessages;
import io.mokamint.node.messages.WhisperPeerMessages;
import io.mokamint.node.messages.WhisperRequestMessages;
import io.mokamint.node.messages.api.AddRequestResultMessage;
import io.mokamint.node.messages.api.GetBlockDescriptionResultMessage;
import io.mokamint.node.messages.api.GetBlockResultMessage;
import io.mokamint.node.messages.api.GetChainInfoResultMessage;
import io.mokamint.node.messages.api.GetChainPortionResultMessage;
import io.mokamint.node.messages.api.GetConfigResultMessage;
import io.mokamint.node.messages.api.GetInfoResultMessage;
import io.mokamint.node.messages.api.GetMempoolInfoResultMessage;
import io.mokamint.node.messages.api.GetMempoolPortionResultMessage;
import io.mokamint.node.messages.api.GetMinerInfosResultMessage;
import io.mokamint.node.messages.api.GetPeerInfosResultMessage;
import io.mokamint.node.messages.api.GetTaskInfosResultMessage;
import io.mokamint.node.messages.api.GetRequestAddressResultMessage;
import io.mokamint.node.messages.api.GetRequestRepresentationResultMessage;
import io.mokamint.node.messages.api.GetRequestResultMessage;
import io.mokamint.node.messages.api.WhisperBlockMessage;
import io.mokamint.node.messages.api.WhisperPeerMessage;
import io.mokamint.node.messages.api.WhisperRequestMessage;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.service.api.PublicNodeService;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * An implementation of a remote node that presents a programmatic interface
 * to a service for the public API of a Mokamint node.
 */
@ThreadSafe
public class RemotePublicNodeImpl extends AbstractRemoteNode implements RemotePublicNode {

	/**
	 * A service used to schedule periodic tasks.
	 */
	private final ScheduledExecutorService periodicTasks = Executors.newScheduledThreadPool(1);

	/**
	 * The whisperers bound to this node.
	 */
	private final CopyOnWriteArrayList<Whisperer> boundWhisperers = new CopyOnWriteArrayList<>();

	/**
	 * A memory of the last whispered things.
	 * This is used to avoid whispering already whispered things again.
	 */
	private final Memory<Whisperable> alreadyWhispered;

	/**
	 * A memory of the last whispered peers. This is used to avoid whispering already whispered messages again.
	 * We use a different memory than {@link #alreadyWhispered} since we want to allow peers to be
	 * whispered also after being whispered already.
	 */
	private final Memory<WhisperPeerMessage> peersAlreadyWhispered;

	/**
	 * The hasher to use for the requests.
	 */
	private final HashingAlgorithm hashingForRequests;

	/**
	 * The prefix used in the log messages;
	 */
	private final String logPrefix;

	private final Predicate<Whisperer> isThis = Predicate.isEqual(this);

	private final static Logger LOGGER = Logger.getLogger(RemotePublicNodeImpl.class.getName());

	/**
	 * Opens and yields a new remote node for the public API of a node.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 * @param serviceBroadcastInterval the time (in milliseconds) between successive broadcasts
	 *                                 of the services opened on this node; use a negative value to
	 *                                 disable service broadcasting
	 * @param whisperedMessagesSize the size of the memory used to avoid whispering the same
	 *                              message again; higher numbers reduce the circulation of
	 *                              spurious messages
	 * @throws FailedDeploymentException if the remote node could not be created
	 * @throws InterruptedException if the current thread has been interrupted
	 * @throws TimeoutException if the creation has timed out
	 */
	public RemotePublicNodeImpl(URI uri, int timeout, int serviceBroadcastInterval, int whisperedMessagesSize) throws FailedDeploymentException, TimeoutException, InterruptedException {
		super(timeout);

		this.logPrefix = "public remote(" + uri + "): ";
		this.alreadyWhispered = Memories.of(whisperedMessagesSize);
		this.peersAlreadyWhispered = Memories.of(whisperedMessagesSize);

		addSession(GET_PEER_INFOS_ENDPOINT, uri, GetPeerInfosEndpoint::new);
		addSession(GET_MINER_INFOS_ENDPOINT, uri, GetMinerInfosEndpoint::new);
		addSession(GET_TASK_INFOS_ENDPOINT, uri, GetTaskInfosEndpoint::new);
		addSession(GET_BLOCK_ENDPOINT, uri, GetBlockEndpoint::new);
		addSession(GET_BLOCK_DESCRIPTION_ENDPOINT, uri, GetBlockDescriptionEndpoint::new);
		addSession(GET_CONFIG_ENDPOINT, uri, GetConfigEndpoint::new);
		addSession(GET_CHAIN_INFO_ENDPOINT, uri, GetChainInfoEndpoint::new);
		addSession(GET_CHAIN_PORTION_ENDPOINT, uri, GetChainPortionEndpoint::new);
		addSession(GET_INFO_ENDPOINT, uri, GetInfoEndpoint::new);
		addSession(GET_REQUEST_REPRESENTATION_ENDPOINT, uri, GetRequestRepresentationEndpoint::new);
		addSession(GET_REQUEST_ADDRESS_ENDPOINT, uri, GetRequestAddressEndpoint::new);
		addSession(GET_REQUEST_ENDPOINT, uri, GetRequestEndpoint::new);
		addSession(GET_MEMPOOL_INFO_ENDPOINT, uri, GetMempoolInfoEndpoint::new);
		addSession(GET_MEMPOOL_PORTION_ENDPOINT, uri, GetMempoolPortionEndpoint::new);
		addSession(ADD_REQUEST_ENDPOINT, uri, AddRequestEndpoint::new);
		addSession(WHISPER_PEER_ENDPOINT, uri, WhisperPeerEndpoint::new);
		addSession(WHISPER_BLOCK_ENDPOINT, uri, WhisperBlockEndpoint::new);
		addSession(WHISPER_REQUEST_ENDPOINT, uri, WhisperRequestEndpoint::new);

		try {
			this.hashingForRequests = getConfig().getHashingForRequests();
		}
		catch (ClosedNodeException e) {
			throw new FailedDeploymentException(e);
		}

		if (serviceBroadcastInterval >= 0)
			periodicTasks.scheduleWithFixedDelay(this::whisperAllServices, 0L, serviceBroadcastInterval, TimeUnit.MILLISECONDS);
	}

	@Override
	protected void closeResources(CloseReason reason) {
		try {
			periodicTasks.shutdownNow();
		}
		finally {
			super.closeResources(reason);
			LOGGER.info(logPrefix + "closed with reason: " + reason);
		}
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
	public void whisper(WhisperMessage<?> message, Predicate<Whisperer> seen, String description) {
		whisper(message, seen, true, description);
	}

	private void whisper(WhisperMessage<?> message, Predicate<Whisperer> seen, boolean includeNetwork, String description) {
		if (seen.test(this))
			return;
		else if (message instanceof WhisperPeerMessage wpm) {
			if (!peersAlreadyWhispered.add(wpm))
				return;
		}
		else if (!alreadyWhispered.add(message.getWhispered()))
			return;

		LOGGER.info(logPrefix + "got whispered " + description);

		Predicate<Whisperer> newSeen = seen.or(isThis);
		boundWhisperers.forEach(whisperer -> whisperer.whisper(message, newSeen, description));

		if (message instanceof WhisperPeerMessage wpm) {
			sendWhisperedAsync(wpm, WHISPER_PEER_ENDPOINT, description, includeNetwork);
			onWhispered(wpm.getWhispered());
		}
		else if (message instanceof WhisperBlockMessage wbm) {
			sendWhisperedAsync(wbm, WHISPER_BLOCK_ENDPOINT, description, includeNetwork);
			onWhispered(wbm.getWhispered());
		}
		else if (message instanceof WhisperRequestMessage wtm) {
			sendWhisperedAsync(wtm, WHISPER_REQUEST_ENDPOINT, description, includeNetwork);
			onWhispered(wtm.getWhispered());
		}
		else
			LOGGER.log(Level.SEVERE, "unexpected whispered object of class " + message.getClass().getName());
	}

	private void sendWhisperedAsync(WhisperMessage<?> message, String endpoint, String description, boolean includeNetwork) {
		if (includeNetwork) {
			try {
				sendObjectAsync(getSession(endpoint), message);
			}
			catch (IOException e) {
				LOGGER.warning(logPrefix + "cannot whisper " + description + " to the connected service: the connection might be closed: " + e.getMessage());
			}
		}
	}

	private void whisperAllServices() {
		// we check how the external world sees our services as peers
		boundWhisperers.stream()
			.filter(whisperer -> whisperer instanceof PublicNodeService)
			.map(whisperer -> (PublicNodeService) whisperer)
			.map(PublicNodeService::getURI)
			.flatMap(Optional::stream)
			.distinct()
			.forEach(uri -> {
				var whisperedPeers = WhisperPeerMessages.of(uri, UUID.randomUUID().toString());
				String description = "peer " + whisperedPeers.getWhispered();
				whisper(whisperedPeers, _whisperer -> false, false, description);
			});
	}

	/**
	 * Sends the given message to the given endpoint. If it fails, it just logs
	 * the exception and continues.
	 * 
	 * @param endpoint the endpoint
	 * @param message the message
	 */
	private void sendObjectAsync(String endpoint, RpcMessage message) {
		try {
			sendObjectAsync(getSession(endpoint), message);
		}
		catch (IOException e) {
			LOGGER.warning("cannot send to " + endpoint + ": " + e.getMessage());
		}
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		switch (message) {
		case GetInfoResultMessage girm -> onGetInfoResult(girm.get());
		case GetPeerInfosResultMessage gprm -> onGetPeerInfosResult(gprm.get());
		case GetMinerInfosResultMessage gmrm -> onGetMinerInfosResult(gmrm.get());
		case GetTaskInfosResultMessage gtirm -> onGetTaskInfosResult(gtirm.get());
		case GetBlockResultMessage gbrm -> onGetBlockResult(gbrm.get());
		case GetBlockDescriptionResultMessage gbrm -> onGetBlockDescriptionResult(gbrm.get());
		case GetConfigResultMessage gcrm -> onGetConfigResult(gcrm.get());
		case GetChainInfoResultMessage gcirm -> onGetChainInfoResult(gcirm.get());
		case GetChainPortionResultMessage gcprm -> onGetChainPortionResult(gcprm.get());
		case AddRequestResultMessage ptrm -> onAddRequestResult(ptrm.get());
		case GetMempoolInfoResultMessage gmirm -> onGetMempoolInfoResult(gmirm.get());
		case GetMempoolPortionResultMessage gmprm -> onGetMempoolPortionResult(gmprm.get());
		case GetRequestResultMessage gtrm -> onGetRequestResult(gtrm.get());
		case GetRequestRepresentationResultMessage gtrrm -> onGetRequestRepresentationResult(gtrrm.get());
		case GetRequestAddressResultMessage gtarm -> onGetRequestAddressResult(gtarm.get());
		default -> {
			if (message != null && !(message instanceof ExceptionMessage)) {
				LOGGER.warning(logPrefix + "unexpected message of class " + message.getClass().getName());
				return;
			}
		}
		}

		super.notifyResult(message);
	}

	@Override
	public NodeInfo getInfo() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetInfo(id);
		return waitForResult(id, GetInfoResultMessage.class);
	}

	protected void sendGetInfo(String id) {
		sendObjectAsync(GET_INFO_ENDPOINT, GetInfoMessages.of(id));
	}

	@Override
	public Stream<MinerInfo> getMinerInfos() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetMinerInfos(id);
		return waitForResult(id, GetMinerInfosResultMessage.class);
	}

	protected void sendGetMinerInfos(String id) {
		sendObjectAsync(GET_MINER_INFOS_ENDPOINT, GetMinerInfosMessages.of(id));
	}

	@Override
	public Stream<TaskInfo> getTaskInfos() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetTaskInfos(id);
		return waitForResult(id, GetTaskInfosResultMessage.class);
	}

	protected void sendGetTaskInfos(String id) {
		sendObjectAsync(GET_TASK_INFOS_ENDPOINT, GetTaskInfosMessages.of(id));
	}

	@Override
	public Stream<PeerInfo> getPeerInfos() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetPeerInfos(id);
		return waitForResult(id, GetPeerInfosResultMessage.class);
	}

	protected void sendGetPeerInfos(String id) {
		sendObjectAsync(GET_PEER_INFOS_ENDPOINT, GetPeerInfosMessages.of(id));
	}

	@Override
	public Optional<Block> getBlock(byte[] hash) throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetBlock(hash, id);
		return waitForResult(id, GetBlockResultMessage.class);
	}

	protected void sendGetBlock(byte[] hash, String id) {
		sendObjectAsync(GET_BLOCK_ENDPOINT, GetBlockMessages.of(hash, id));
	}

	@Override
	public Optional<BlockDescription> getBlockDescription(byte[] hash) throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetBlockDescription(hash, id);
		return waitForResult(id, GetBlockDescriptionResultMessage.class);
	}

	protected void sendGetBlockDescription(byte[] hash, String id) {
		sendObjectAsync(GET_BLOCK_DESCRIPTION_ENDPOINT, GetBlockDescriptionMessages.of(hash, id));
	}

	@Override
	public ConsensusConfig<?,?> getConfig() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetConfig(id);
		return waitForResult(id, GetConfigResultMessage.class);
	}

	protected void sendGetConfig(String id) {
		sendObjectAsync(GET_CONFIG_ENDPOINT, GetConfigMessages.of(id));
	}

	@Override
	public ChainInfo getChainInfo() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetChainInfo(id);
		return waitForResult(id, GetChainInfoResultMessage.class);
	}

	protected void sendGetChainInfo(String id) {
		sendObjectAsync(GET_CHAIN_INFO_ENDPOINT, GetChainInfoMessages.of(id));
	}

	@Override
	public ChainPortion getChainPortion(long start, int count) throws PortionRejectedException, InterruptedException, TimeoutException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetChainPortion(start, count, id);
		return waitForResult(id, GetChainPortionResultMessage.class, PortionRejectedException.class);
	}

	protected void sendGetChainPortion(long start, int count, String id) {
		sendObjectAsync(GET_CHAIN_PORTION_ENDPOINT, GetChainPortionMessages.of(start, count, id));
	}

	@Override
	public MempoolEntry add(Request request) throws RequestRejectedException, ApplicationTimeoutException, TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendAddRequest(request, id);
		return waitForResult(id, AddRequestResultMessage.class, RequestRejectedException.class, ApplicationTimeoutException.class);
	}

	protected void sendAddRequest(Request request, String id) {
		sendObjectAsync(ADD_REQUEST_ENDPOINT, AddRequestMessages.of(request, id));
	}

	@Override
	public MempoolInfo getMempoolInfo() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetMempoolInfo(id);
		return waitForResult(id, GetMempoolInfoResultMessage.class);
	}

	protected void sendGetMempoolInfo(String id) {
		sendObjectAsync(GET_MEMPOOL_INFO_ENDPOINT, GetMempoolInfoMessages.of(id));
	}

	@Override
	public MempoolPortion getMempoolPortion(int start, int count) throws PortionRejectedException, TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetMempoolPortion(start, count, id);
		return waitForResult(id, GetMempoolPortionResultMessage.class, PortionRejectedException.class);
	}

	protected void sendGetMempoolPortion(int start, int count, String id) {
		sendObjectAsync(GET_MEMPOOL_PORTION_ENDPOINT, GetMempoolPortionMessages.of(start, count, id));
	}

	@Override
	public Optional<Request> getRequest(byte[] hash) throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetRequest(hash, id);
		return waitForResult(id, GetRequestResultMessage.class);
	}

	protected void sendGetRequest(byte[] hash, String id) {
		sendObjectAsync(GET_REQUEST_ENDPOINT, GetRequestMessages.of(hash, id));
	}

	@Override
	public Optional<String> getRequestRepresentation(byte[] hash) throws RequestRejectedException, TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetRequestRepresentation(hash, id);
		return waitForResult(id, GetRequestRepresentationResultMessage.class, RequestRejectedException.class);
	}

	protected void sendGetRequestRepresentation(byte[] hash, String id) {
		sendObjectAsync(GET_REQUEST_REPRESENTATION_ENDPOINT, GetRequestRepresentationMessages.of(hash, id));
	}

	@Override
	public Optional<RequestAddress> getRequestAddress(byte[] hash) throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen(ClosedNodeException::new);
		var id = nextId();
		sendGetRequestAddress(hash, id);
		return waitForResult(id, GetRequestAddressResultMessage.class);
	}

	protected void sendGetRequestAddress(byte[] hash, String id) {
		sendObjectAsync(GET_REQUEST_ADDRESS_ENDPOINT, GetRequestAddressMessages.of(hash, id));
	}

	/**
	 * Hooks that can be overridden in subclasses.
	 */
	protected void onGetPeerInfosResult(Stream<PeerInfo> peers) {}
	protected void onGetMinerInfosResult(Stream<MinerInfo> miners) {}
	protected void onGetTaskInfosResult(Stream<TaskInfo> tasks) {}
	protected void onGetBlockResult(Optional<Block> block) {}
	protected void onGetBlockDescriptionResult(Optional<BlockDescription> block) {}
	protected void onGetConfigResult(ConsensusConfig<?,?> config) {}
	protected void onGetChainInfoResult(ChainInfo info) {}
	protected void onGetChainPortionResult(ChainPortion chain) {}
	protected void onGetInfoResult(NodeInfo info) {}
	protected void onGetMempoolInfoResult(MempoolInfo info) {}
	protected void onGetMempoolPortionResult(MempoolPortion chain) {}
	protected void onGetRequestResult(Optional<Request> request) {}
	protected void onGetRequestRepresentationResult(Optional<String> representation) {}
	protected void onGetRequestAddressResult(Optional<RequestAddress> address) {}
	protected void onAddRequestResult(MempoolEntry info) {}
	protected void onException(ExceptionMessage message) {}
	protected void onWhispered(Peer peer) {}
	protected void onWhispered(Block block) {}
	protected void onWhispered(Request request) {}

	private class GetPeerInfosEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetPeerInfosResultMessages.Decoder.class, GetPeerInfosMessages.Encoder.class);
		}
	}

	private class GetMinerInfosEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetMinerInfosResultMessages.Decoder.class, GetMinerInfosMessages.Encoder.class);
		}
	}

	private class GetTaskInfosEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetTaskInfosResultMessages.Decoder.class, GetTaskInfosMessages.Encoder.class);
		}
	}

	private class GetBlockEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetBlockResultMessages.Decoder.class, GetBlockMessages.Encoder.class);
		}
	}

	private class GetBlockDescriptionEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetBlockDescriptionResultMessages.Decoder.class, GetBlockDescriptionMessages.Encoder.class);
		}
	}

	private class GetConfigEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetConfigResultMessages.Decoder.class, GetConfigMessages.Encoder.class);
		}
	}

	private class GetChainInfoEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetChainInfoResultMessages.Decoder.class, GetChainInfoMessages.Encoder.class);
		}
	}

	private class GetChainPortionEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetChainPortionResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetChainPortionMessages.Encoder.class);
		}
	}

	private class GetInfoEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetInfoResultMessages.Decoder.class, GetInfoMessages.Encoder.class);
		}
	}

	private class GetRequestEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetRequestResultMessages.Decoder.class, GetRequestMessages.Encoder.class);
		}
	}

	private class GetRequestRepresentationEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetRequestRepresentationResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetRequestRepresentationMessages.Encoder.class);
		}
	}

	private class GetRequestAddressEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetRequestAddressResultMessages.Decoder.class, GetRequestAddressMessages.Encoder.class);
		}
	}

	private class GetMempoolInfoEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetMempoolInfoResultMessages.Decoder.class, GetMempoolInfoMessages.Encoder.class);
		}
	}

	private class GetMempoolPortionEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetMempoolPortionResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetMempoolPortionMessages.Encoder.class);
		}
	}

	private class AddRequestEndpoint extends Endpoint {
	
		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, AddRequestResultMessages.Decoder.class, ExceptionMessages.Decoder.class, AddRequestMessages.Encoder.class);
		}
	}

	private class WhisperPeerEndpoint extends Endpoint {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (WhisperPeerMessage message) -> whisper(message, _whisperer -> false, false, "peer " + message.getWhispered()));
		}

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, WhisperPeerMessages.Decoder.class, WhisperPeerMessages.Encoder.class);
		}
	}

	private class WhisperBlockEndpoint extends Endpoint {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (WhisperBlockMessage message) -> whisper(message, _whisperer -> false, false, "block " + message.getWhispered().getHexHash()));
		}

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, WhisperBlockMessages.Decoder.class, WhisperBlockMessages.Encoder.class);
		}
	}

	private class WhisperRequestEndpoint extends Endpoint {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (WhisperRequestMessage message) -> whisper(message, _whisperer -> false, false, "request " + message.getWhispered().getHexHash(hashingForRequests)));
		}

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, WhisperRequestMessages.Decoder.class, WhisperRequestMessages.Encoder.class);
		}
	}
}
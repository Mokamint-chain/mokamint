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

import static io.mokamint.node.service.api.PublicNodeService.ADD_TRANSACTION_ENDPOINT;
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
import static io.mokamint.node.service.api.PublicNodeService.GET_TRANSACTION_ADDRESS_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_TRANSACTION_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_TRANSACTION_REPRESENTATION_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.WHISPER_BLOCK_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.WHISPER_PEER_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.WHISPER_TRANSACTION_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
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
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.mokamint.node.Peers;
import io.mokamint.node.SanitizedStrings;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.api.MempoolPortion;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.TaskInfo;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.api.Whispered;
import io.mokamint.node.api.WhisperedBlock;
import io.mokamint.node.api.WhisperedPeer;
import io.mokamint.node.api.WhisperedTransaction;
import io.mokamint.node.api.Whisperer;
import io.mokamint.node.messages.AddTransactionMessages;
import io.mokamint.node.messages.AddTransactionResultMessages;
import io.mokamint.node.messages.ExceptionMessages;
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
import io.mokamint.node.messages.GetTransactionAddressMessages;
import io.mokamint.node.messages.GetTransactionAddressResultMessages;
import io.mokamint.node.messages.GetTransactionMessages;
import io.mokamint.node.messages.GetTransactionRepresentationMessages;
import io.mokamint.node.messages.GetTransactionRepresentationResultMessages;
import io.mokamint.node.messages.GetTransactionResultMessages;
import io.mokamint.node.messages.WhisperBlockMessages;
import io.mokamint.node.messages.WhisperPeerMessages;
import io.mokamint.node.messages.WhisperTransactionMessages;
import io.mokamint.node.messages.WhisperedMemories;
import io.mokamint.node.messages.api.AddTransactionResultMessage;
import io.mokamint.node.messages.api.ExceptionMessage;
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
import io.mokamint.node.messages.api.GetTransactionAddressResultMessage;
import io.mokamint.node.messages.api.GetTransactionRepresentationResultMessage;
import io.mokamint.node.messages.api.GetTransactionResultMessage;
import io.mokamint.node.messages.api.WhisperBlockMessage;
import io.mokamint.node.messages.api.WhisperPeerMessage;
import io.mokamint.node.messages.api.WhisperTransactionMessage;
import io.mokamint.node.messages.api.WhisperingMemory;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.service.api.PublicNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * An implementation of a remote node that presents a programmatic interface
 * to a service for the public API of a Mokamint node.
 */
@ThreadSafe
public class RemotePublicNodeImpl extends AbstractRemoteNode implements RemotePublicNode {

	/**
	 * AprocessStandardExceptionsprocessStandardExceptions queue of messages received from the external world.
	 */
	private final NodeMessageQueues queues;

	/**
	 * A service used to schedule periodic tasks.
	 */
	private final ScheduledExecutorService periodicTasks = Executors.newScheduledThreadPool(1);

	/**
	 * The whisperers bound to this node.
	 */
	private final CopyOnWriteArrayList<Whisperer> boundWhisperers = new CopyOnWriteArrayList<>();

	/**
	 * A memory of the last whispered messages,
	 * This is used to avoid whispering already whispered messages again.
	 */
	private final WhisperingMemory alreadyWhispered;

	/**
	 * The hashing to use for the blocks.
	 */
	private final HashingAlgorithm hashingForBlocks;

	/**
	 * The hasher to use for the transactions.
	 */
	private final Hasher<Transaction> hasherForTransactions;

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
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	public RemotePublicNodeImpl(URI uri, long timeout, long serviceBroadcastInterval, int whisperedMessagesSize) throws DeploymentException, IOException {
		this.logPrefix = "public remote(" + uri + "): ";
		this.alreadyWhispered = WhisperedMemories.of(whisperedMessagesSize);

		addSession(GET_PEER_INFOS_ENDPOINT, uri, GetPeerInfosEndpoint::new);
		addSession(GET_MINER_INFOS_ENDPOINT, uri, GetMinerInfosEndpoint::new);
		addSession(GET_TASK_INFOS_ENDPOINT, uri, GetTaskInfosEndpoint::new);
		addSession(GET_BLOCK_ENDPOINT, uri, GetBlockEndpoint::new);
		addSession(GET_BLOCK_DESCRIPTION_ENDPOINT, uri, GetBlockDescriptionEndpoint::new);
		addSession(GET_CONFIG_ENDPOINT, uri, GetConfigEndpoint::new);
		addSession(GET_CHAIN_INFO_ENDPOINT, uri, GetChainInfoEndpoint::new);
		addSession(GET_CHAIN_PORTION_ENDPOINT, uri, GetChainPortionEndpoint::new);
		addSession(GET_INFO_ENDPOINT, uri, GetInfoEndpoint::new);
		addSession(GET_TRANSACTION_REPRESENTATION_ENDPOINT, uri, GetTransactionRepresentationEndpoint::new);
		addSession(GET_TRANSACTION_ADDRESS_ENDPOINT, uri, GetTransactionAddressEndpoint::new);
		addSession(GET_TRANSACTION_ENDPOINT, uri, GetTransactionEndpoint::new);
		addSession(GET_MEMPOOL_INFO_ENDPOINT, uri, GetMempoolInfoEndpoint::new);
		addSession(GET_MEMPOOL_PORTION_ENDPOINT, uri, GetMempoolPortionEndpoint::new);
		addSession(ADD_TRANSACTION_ENDPOINT, uri, AddTransactionEndpoint::new);
		addSession(WHISPER_PEER_ENDPOINT, uri, WhisperPeerEndpoint::new);
		addSession(WHISPER_BLOCK_ENDPOINT, uri, WhisperBlockEndpoint::new);
		addSession(WHISPER_TRANSACTION_ENDPOINT, uri, WhisperTransactionEndpoint::new);

		this.queues = new NodeMessageQueues(timeout);

		try {
			var config = getConfig();
			this.hashingForBlocks = config.getHashingForBlocks();
			this.hasherForTransactions = config.getHashingForTransactions().getHasher(Transaction::toByteArray);
		}
		catch (TimeoutException | InterruptedException | ClosedNodeException e) {
			LOGGER.warning(logPrefix + "failed to turn up the remote: " + e.getMessage());
			throw new IOException(e);
		}

		if (serviceBroadcastInterval >= 0)
			periodicTasks.scheduleWithFixedDelay(this::whisperAllServices, 0L, serviceBroadcastInterval, TimeUnit.MILLISECONDS);
	}

	@Override
	public void close() throws IOException, InterruptedException {
		super.close();
		periodicTasks.shutdownNow();
		periodicTasks.awaitTermination(10, TimeUnit.SECONDS);
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
	public void whisper(Whispered whispered, Predicate<Whisperer> seen, String description) {
		whisper(whispered, seen, true, description);
	}

	private void whisper(Whispered whispered, Predicate<Whisperer> seen, boolean includeNetwork, String description) {
		if (seen.test(this) || !alreadyWhispered.add(whispered))
			return;

		LOGGER.info(logPrefix + "got whispered " + description);

		Predicate<Whisperer> newSeen = seen.or(isThis);
		boundWhisperers.forEach(whisperer -> whisperer.whisper(whispered, newSeen, description));

		if (whispered instanceof WhisperedPeer whisperedPeer) {
			sendWhisperedAsync(whisperedPeer, WHISPER_PEER_ENDPOINT, description, includeNetwork);
			onWhispered(whisperedPeer.getPeer());
		}
		else if (whispered instanceof WhisperedBlock whisperedBlock) {
			sendWhisperedAsync(whisperedBlock, WHISPER_BLOCK_ENDPOINT, description, includeNetwork);
			onWhispered(whisperedBlock.getBlock());
		}
		else if (whispered instanceof WhisperedTransaction whisperedTransaction) {
			sendWhisperedAsync(whisperedTransaction, WHISPER_TRANSACTION_ENDPOINT, description, includeNetwork);
			onWhispered(whisperedTransaction.getTransaction());
		}
		else
			LOGGER.log(Level.SEVERE, "unexpected whispered object of class " + whispered.getClass().getName());
	}

	private void sendWhisperedAsync(Whispered whispered, String endpoint, String description, boolean includeNetwork) {
		if (includeNetwork) {
			try {
				sendObjectAsync(getSession(endpoint), whispered);
			}
			catch (IOException e) {
				LOGGER.log(Level.SEVERE, logPrefix + "cannot whisper " + description + " to the connected service: the connection might be closed: " + e.getMessage());
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
			.map(Peers::of)
			.forEach(peer -> {
				var whisperedPeers = WhisperPeerMessages.of(peer, UUID.randomUUID().toString());
				String description = "peers " + SanitizedStrings.of(peer);
				whisper(whisperedPeers, _whisperer -> false, false, description);
			});
	}

	private RuntimeException unexpectedException(Exception e) {
		LOGGER.log(Level.SEVERE, logPrefix + "remote: unexpected exception", e);
		return new RuntimeException("Unexpected exception", e);
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		if (message instanceof GetInfoResultMessage girm)
			onGetInfoResult(girm.get());
		else if (message instanceof GetPeerInfosResultMessage gprm)
			onGetPeerInfosResult(gprm.get());
		else if (message instanceof GetMinerInfosResultMessage gmrm)
			onGetMinerInfosResult(gmrm.get());
		else if (message instanceof GetTaskInfosResultMessage gtirm)
			onGetTaskInfosResult(gtirm.get());
		else if (message instanceof GetBlockResultMessage gbrm)
			onGetBlockResult(gbrm.get());
		else if (message instanceof GetBlockDescriptionResultMessage gbrm)
			onGetBlockDescriptionResult(gbrm.get());
		else if (message instanceof GetConfigResultMessage gcrm)
			onGetConfigResult(gcrm.get());
		else if (message instanceof GetChainInfoResultMessage gcirm)
			onGetChainInfoResult(gcirm.get());
		else if (message instanceof GetChainPortionResultMessage gcprm)
			onGetChainPortionResult(gcprm.get());
		else if (message instanceof AddTransactionResultMessage ptrm)
			onAddTransactionResult(ptrm.get());
		else if (message instanceof GetMempoolInfoResultMessage gmirm)
			onGetMempoolInfoResult(gmirm.get());
		else if (message instanceof GetMempoolPortionResultMessage gmprm)
			onGetMempoolPortionResult(gmprm.get());
		else if (message instanceof GetTransactionResultMessage gtrm)
			onGetTransactionResult(gtrm.get());
		else if (message instanceof GetTransactionRepresentationResultMessage gtrrm)
			onGetTransactionRepresentationResult(gtrrm.get());
		else if (message instanceof GetTransactionAddressResultMessage gtarm)
			onGetTransactionAddressResult(gtarm.get());
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
	public Stream<MinerInfo> getMinerInfos() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetMinerInfos(id);
		try {
			return queues.waitForResult(id, this::processGetMinerInfosSuccess, this::processStandardExceptions);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	protected void sendGetMinerInfos(String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_MINER_INFOS_ENDPOINT), GetMinerInfosMessages.of(id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private Stream<MinerInfo> processGetMinerInfosSuccess(RpcMessage message) {
		return message instanceof GetMinerInfosResultMessage gmrm ? gmrm.get() : null;
	}

	@Override
	public Stream<TaskInfo> getTaskInfos() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetTaskInfos(id);
		try {
			return queues.waitForResult(id, this::processGetTaskInfosSuccess, this::processStandardExceptions);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	protected void sendGetTaskInfos(String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_TASK_INFOS_ENDPOINT), GetTaskInfosMessages.of(id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private Stream<TaskInfo> processGetTaskInfosSuccess(RpcMessage message) {
		return message instanceof GetTaskInfosResultMessage gtirm ? gtirm.get() : null;
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
	public Optional<BlockDescription> getBlockDescription(byte[] hash) throws DatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetBlockDescription(hash, id);
		try {
			return queues.waitForResult(id, this::processGetBlockDescriptionSuccess, this::processGetBlockDescriptionException);
		}
		catch (RuntimeException | DatabaseException | NoSuchAlgorithmException | TimeoutException | InterruptedException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	protected void sendGetBlockDescription(byte[] hash, String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_BLOCK_DESCRIPTION_ENDPOINT), GetBlockDescriptionMessages.of(hash, id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private Optional<BlockDescription> processGetBlockDescriptionSuccess(RpcMessage message) {
		return message instanceof GetBlockDescriptionResultMessage gbrm ? gbrm.get() : null;
	}

	private boolean processGetBlockDescriptionException(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return DatabaseException.class.isAssignableFrom(clazz) ||
			NoSuchAlgorithmException.class.isAssignableFrom(clazz) ||
			processStandardExceptions(message);
	}

	@Override
	public ConsensusConfig<?,?> getConfig() throws TimeoutException, InterruptedException, ClosedNodeException {
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

	private ConsensusConfig<?,?> processGetConfigSuccess(RpcMessage message) {
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
	public ChainPortion getChainPortion(long start, int count) throws DatabaseException, TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetChainPortion(start, count, id);
		try {
			return queues.waitForResult(id, this::processGetChainPortionSuccess, this::processGetChainPortionException);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | DatabaseException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	protected void sendGetChainPortion(long start, int count, String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_CHAIN_PORTION_ENDPOINT), GetChainPortionMessages.of(start, count, id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private ChainPortion processGetChainPortionSuccess(RpcMessage message) {
		return message instanceof GetChainPortionResultMessage gcrm ? gcrm.get() : null;
	}

	private boolean processGetChainPortionException(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return DatabaseException.class.isAssignableFrom(clazz) ||
			processStandardExceptions(message);
	}

	@Override
	public MempoolEntry add(Transaction transaction) throws RejectedTransactionException, TimeoutException, InterruptedException, ClosedNodeException, NoSuchAlgorithmException, DatabaseException {
		ensureIsOpen();
		var id = queues.nextId();
		sendAddTransaction(transaction, id);
		try {
			return queues.waitForResult(id, this::processAddTransactionSuccess, this::processAddTransactionException);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | ClosedNodeException | NoSuchAlgorithmException | DatabaseException | RejectedTransactionException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	protected void sendAddTransaction(Transaction transaction, String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(ADD_TRANSACTION_ENDPOINT), AddTransactionMessages.of(transaction, id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private MempoolEntry processAddTransactionSuccess(RpcMessage message) {
		return message instanceof AddTransactionResultMessage atrm ? atrm.get() : null;
	}

	private boolean processAddTransactionException(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return RejectedTransactionException.class.isAssignableFrom(clazz) || NoSuchAlgorithmException.class.isAssignableFrom(clazz) || DatabaseException.class.isAssignableFrom(clazz) ||
			processStandardExceptions(message);
	}

	@Override
	public MempoolInfo getMempoolInfo() throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetMempoolInfo(id);
		try {
			return queues.waitForResult(id, this::processGetMempoolInfoSuccess, this::processStandardExceptions);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	protected void sendGetMempoolInfo(String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_MEMPOOL_INFO_ENDPOINT), GetMempoolInfoMessages.of(id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private MempoolInfo processGetMempoolInfoSuccess(RpcMessage message) {
		return message instanceof GetMempoolInfoResultMessage gmirm ? gmirm.get() : null;
	}

	@Override
	public MempoolPortion getMempoolPortion(int start, int count) throws TimeoutException, InterruptedException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetMempoolPortion(start, count, id);
		try {
			return queues.waitForResult(id, this::processGetMempoolPortionSuccess, this::processStandardExceptions);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	protected void sendGetMempoolPortion(int start, int count, String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_MEMPOOL_PORTION_ENDPOINT), GetMempoolPortionMessages.of(start, count, id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private MempoolPortion processGetMempoolPortionSuccess(RpcMessage message) {
		return message instanceof GetMempoolPortionResultMessage gcrm ? gcrm.get() : null;
	}

	@Override
	public Optional<Transaction> getTransaction(byte[] hash) throws TimeoutException, InterruptedException, NoSuchAlgorithmException, DatabaseException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetTransaction(hash, id);
		try {
			return queues.waitForResult(id, this::processGetTransactionSuccess, this::processGetTransactionException);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | NoSuchAlgorithmException | DatabaseException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	protected void sendGetTransaction(byte[] hash, String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_TRANSACTION_ENDPOINT), GetTransactionMessages.of(hash, id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private Optional<Transaction> processGetTransactionSuccess(RpcMessage message) {
		return message instanceof GetTransactionResultMessage gtrm ? gtrm.get() : null;
	}

	private boolean processGetTransactionException(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return NoSuchAlgorithmException.class.isAssignableFrom(clazz) || DatabaseException.class.isAssignableFrom(clazz) ||
			processStandardExceptions(message);
	}

	@Override
	public Optional<String> getTransactionRepresentation(byte[] hash) throws RejectedTransactionException, TimeoutException, InterruptedException, NoSuchAlgorithmException, DatabaseException, ClosedNodeException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetTransactionRepresentation(hash, id);
		try {
			return queues.waitForResult(id, this::processGetTransactionRepresentationSuccess, this::processGetTransactionRepresentationException);
		}
		catch (RuntimeException | RejectedTransactionException | TimeoutException | InterruptedException | NoSuchAlgorithmException | DatabaseException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	protected void sendGetTransactionRepresentation(byte[] hash, String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_TRANSACTION_REPRESENTATION_ENDPOINT), GetTransactionRepresentationMessages.of(hash, id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private Optional<String> processGetTransactionRepresentationSuccess(RpcMessage message) {
		return message instanceof GetTransactionRepresentationResultMessage gtrrm ? gtrrm.get() : null;
	}

	private boolean processGetTransactionRepresentationException(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return RejectedTransactionException.class.isAssignableFrom(clazz) || NoSuchAlgorithmException.class.isAssignableFrom(clazz) || DatabaseException.class.isAssignableFrom(clazz) ||
			processStandardExceptions(message);
	}

	@Override
	public Optional<TransactionAddress> getTransactionAddress(byte[] hash) throws TimeoutException, InterruptedException, ClosedNodeException, DatabaseException {
		ensureIsOpen();
		var id = queues.nextId();
		sendGetTransactionAddress(hash, id);
		try {
			return queues.waitForResult(id, this::processGetTransactionAddressSuccess, this::processGetTransactionAddressException);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | DatabaseException | ClosedNodeException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	protected void sendGetTransactionAddress(byte[] hash, String id) throws ClosedNodeException {
		try {
			sendObjectAsync(getSession(GET_TRANSACTION_ADDRESS_ENDPOINT), GetTransactionAddressMessages.of(hash, id));
		}
		catch (IOException e) {
			throw new ClosedNodeException(e);
		}
	}

	private Optional<TransactionAddress> processGetTransactionAddressSuccess(RpcMessage message) {
		return message instanceof GetTransactionAddressResultMessage gtarm ? gtarm.get() : null;
	}

	private boolean processGetTransactionAddressException(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return DatabaseException.class.isAssignableFrom(clazz) || processStandardExceptions(message);
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
	protected void onGetTransactionResult(Optional<Transaction> transaction) {}
	protected void onGetTransactionRepresentationResult(Optional<String> representation) {}
	protected void onGetTransactionAddressResult(Optional<TransactionAddress> address) {}
	protected void onAddTransactionResult(MempoolEntry info) {}
	protected void onException(ExceptionMessage message) {}
	protected void onWhispered(Peer peer) {}
	protected void onWhispered(Block block) {}
	protected void onWhispered(Transaction transaction) {}

	private class GetPeerInfosEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetPeerInfosResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetPeerInfosMessages.Encoder.class);
		}
	}

	private class GetMinerInfosEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetMinerInfosResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetMinerInfosMessages.Encoder.class);
		}
	}

	private class GetTaskInfosEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetTaskInfosResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetTaskInfosMessages.Encoder.class);
		}
	}

	private class GetBlockEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetBlockResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetBlockMessages.Encoder.class);
		}
	}

	private class GetBlockDescriptionEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetBlockDescriptionResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetBlockDescriptionMessages.Encoder.class);
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

	private class GetChainPortionEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetChainPortionResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetChainPortionMessages.Encoder.class);
		}
	}

	private class GetInfoEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetInfoResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetInfoMessages.Encoder.class);
		}
	}

	private class GetTransactionEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetTransactionResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetTransactionMessages.Encoder.class);
		}
	}

	private class GetTransactionRepresentationEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetTransactionRepresentationResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetTransactionRepresentationMessages.Encoder.class);
		}
	}

	private class GetTransactionAddressEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetTransactionAddressResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetTransactionAddressMessages.Encoder.class);
		}
	}

	private class GetMempoolInfoEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetMempoolInfoResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetMempoolInfoMessages.Encoder.class);
		}
	}

	private class GetMempoolPortionEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetMempoolPortionResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetMempoolPortionMessages.Encoder.class);
		}
	}

	private class AddTransactionEndpoint extends Endpoint {
	
		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, AddTransactionResultMessages.Decoder.class, ExceptionMessages.Decoder.class, AddTransactionMessages.Encoder.class);
		}
	}

	private class WhisperPeerEndpoint extends Endpoint {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (WhisperPeerMessage message) -> whisper(message, _whisperer -> false, false, "peer " + SanitizedStrings.of(message.getPeer())));
		}

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, WhisperPeerMessages.Decoder.class, WhisperPeerMessages.Encoder.class);
		}
	}

	private class WhisperBlockEndpoint extends Endpoint {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (WhisperBlockMessage message) -> whisper(message, _whisperer -> false, false, "block " + message.getBlock().getHexHash(hashingForBlocks)));
		}

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, WhisperBlockMessages.Decoder.class, WhisperBlockMessages.Encoder.class);
		}
	}

	private class WhisperTransactionEndpoint extends Endpoint {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (WhisperTransactionMessage message) -> whisper(message, _whisperer -> false, false, "transaction " + Hex.toHexString(hasherForTransactions.hash(message.getTransaction()))));
		}

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, WhisperTransactionMessages.Decoder.class, WhisperTransactionMessages.Encoder.class);
		}
	}
}
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

package io.mokamint.node.local.internal;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.api.Hasher;
import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.remote.RemoteMiners;
import io.mokamint.node.ChainPortions;
import io.mokamint.node.SanitizedStrings;
import io.mokamint.node.TaskInfos;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.api.MempoolPortion;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.TaskInfo;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.Whispered;
import io.mokamint.node.api.WhisperedBlock;
import io.mokamint.node.api.WhisperedPeers;
import io.mokamint.node.api.WhisperedTransaction;
import io.mokamint.node.api.Whisperer;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.api.LocalNode;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.blockchain.Blockchain;
import io.mokamint.node.local.internal.blockchain.DelayedMineNewBlockTask;
import io.mokamint.node.local.internal.blockchain.MineNewBlockTask;
import io.mokamint.node.local.internal.blockchain.SynchronizationTask;
import io.mokamint.node.local.internal.blockchain.VerificationException;
import io.mokamint.node.local.internal.mempool.Mempool;
import io.mokamint.node.local.internal.mempool.Mempool.TransactionEntry;
import io.mokamint.node.local.internal.miners.Miners;
import io.mokamint.node.local.internal.peers.Peers;
import io.mokamint.node.messages.WhisperBlockMessages;
import io.mokamint.node.messages.WhisperPeersMessages;
import io.mokamint.node.messages.WhisperTransactionMessages;
import io.mokamint.node.messages.WhisperedMemories;
import io.mokamint.node.messages.api.WhisperingMemory;
import io.mokamint.node.service.api.PublicNodeService;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.IllegalDeadlineException;
import jakarta.websocket.DeploymentException;

/**
 * A local node of a Mokamint blockchain.
 */
@ThreadSafe
public class LocalNodeImpl implements LocalNode {

	/**
	 * The configuration of the node.
	 */
	private final LocalNodeConfig config;

	/**
	 * The hasher for the transactions.
	 */
	private final Hasher<Transaction> hasherForTransactions;

	/**
	 * The key pair that the node uses to sign the blocks that it mines.
	 */
	private final KeyPair keyPair;

	/**
	 * The application running over this node.
	 */
	private final Application app;

	/**
	 * The miners connected to the node.
	 */
	private final Miners miners;

	/**
	 * The peers of the node.
	 */
	private final Peers peers;

	/**
	 * The blockchain of this node.
	 */
	private final Blockchain blockchain;

	/**
	 * The mempool of this node.
	 */
	private final Mempool mempool;

	/**
	 * The UUID of this node.
	 */
	private final UUID uuid;

	/**
	 * The executor of tasks and events. There might be more tasks and events in execution at the same time.
	 */
	private final ExecutorService executors = Executors.newCachedThreadPool();

	/**
	 * The executor of periodic tasks. There might be more periodic tasks in execution at the same time.
	 */
	private final ScheduledExecutorService periodicExecutors = Executors.newScheduledThreadPool(5);

	/**
	 * The set of tasks currently executing inside {@link #executors} or {@link #periodicExecutors}.
	 */
	private final Set<RunnableTask> currentlyExecutingTasks = ConcurrentHashMap.newKeySet();

	/**
	 * The code to execute when this node gets closed.
	 */
	private final CopyOnWriteArrayList<CloseHandler> onCloseHandlers = new CopyOnWriteArrayList<>();

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
	 * The lock used to block new calls when the node has been requested to close.
	 */
	private final ClosureLock closureLock = new ClosureLock();

	/**
	 * True if and only if a synchronization task is in process.
	 */
	private final AtomicBoolean isSynchronizing = new AtomicBoolean(false);

	/**
	 * The set of blocks over which mining is currently in progress.
	 */
	private final Set<Block> blocksOverWhichMiningIsInProgress = ConcurrentHashMap.newKeySet();

	private final Set<OnAddedTransactionHandler> onAddedTransactionHandlers = ConcurrentHashMap.newKeySet();

	private final Predicate<Whisperer> isThis = Predicate.isEqual(this);

	private final static Logger LOGGER = Logger.getLogger(LocalNodeImpl.class.getName());

	/**
	 * Creates a local node of a Mokamint blockchain, for the given application.
	 * 
	 * @param config the configuration of the node
	 * @param keyPair the key pair that the node will use to sign the blocks that it mines
	 * @param app the application
	 * @param init if true, creates a genesis block and starts mining on top
	 *             (initial synchronization is consequently skipped)
	 * @throws IOException if the version information cannot be read
	 * @throws DatabaseException if the database is corrupted
	 * @throws InterruptedException if the initialization of the node was interrupted
	 * @throws AlreadyInitializedException if {@code init} is true but the database of the node
	 *                                     contains a genesis block already
	 * @throws SignatureException if the genesis block cannot be signed
	 * @throws InvalidKeyException if the private key of the node is invalid
	 */
	public LocalNodeImpl(LocalNodeConfig config, KeyPair keyPair, Application app, boolean init) throws DatabaseException, IOException, InterruptedException, AlreadyInitializedException, InvalidKeyException, SignatureException {
		try {
			this.config = config;
			this.hasherForTransactions = config.getHashingForTransactions().getHasher(Transaction::toByteArray);
			this.keyPair = keyPair;
			this.app = app;
			this.alreadyWhispered = WhisperedMemories.of(config.getWhisperingMemorySize());
			this.miners = new Miners(this);
			this.blockchain = new Blockchain(this);
			this.mempool = new Mempool(this);
			this.peers = new Peers(this);
			this.uuid = getInfo().getUUID();

			if (init)
				blockchain.initialize();
			else
				scheduleSynchronization(0L);

			schedulePeriodicPingToAllPeersRecreateRemotesAndAddTheirPeers();
			schedulePeriodicWhisperingOfAllServices();
		}
		catch (ClosedNodeException e) {
			throw unexpectedException(e);
		}
	}

	@Override
	public void addOnClosedHandler(CloseHandler handler) {
		onCloseHandlers.add(handler);
	}

	@Override
	public void removeOnCloseHandler(CloseHandler handler) {
		onCloseHandlers.add(handler);
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
		if (seen.test(this) || !alreadyWhispered.add(whispered))
			return;

		if (whispered instanceof WhisperedPeers whisperedPeers)
			execute(() -> peers.reconnectOrTryToAdd(whisperedPeers.getPeers()), "reconnection or addition of whispered " + description);
		else if (whispered instanceof WhisperedBlock whisperedBlock) {
			try {
				blockchain.add(whisperedBlock.getBlock());
			}
			catch (NoSuchAlgorithmException | DatabaseException | VerificationException | ClosedDatabaseException e) {
				LOGGER.log(Level.SEVERE, "node " + uuid + ": whispered " + description + " could not be added to the blockchain: " + e.getMessage());
			}
		}
		else if (whispered instanceof WhisperedTransaction whisperedTransaction) {
			var tx = whisperedTransaction.getTransaction();
			try {
				mempool.add(tx);
			}
			catch (RejectedTransactionException | NoSuchAlgorithmException | ClosedDatabaseException | DatabaseException e) {
				LOGGER.log(Level.SEVERE, "node " + uuid + ": whispered " + description + " could not be added to the mempool: " + e.getMessage());
			}

			onTransactionAdded(tx);
		}
		else
			LOGGER.log(Level.SEVERE, "node: unexpected whispered object of class " + whispered.getClass().getName());

		Predicate<Whisperer> newSeen = seen.or(isThis);
		peers.whisper(whispered, newSeen, description);
		boundWhisperers.forEach(whisperer -> whisperer.whisper(whispered, newSeen, description));

		if (whispered instanceof WhisperedPeers whisperedPeers)
			onWhispered(whisperedPeers.getPeers());
		else if (whispered instanceof WhisperedBlock whisperedBlock)
			onWhispered(whisperedBlock.getBlock());
		else if (whispered instanceof WhisperedTransaction whisperedTransaction)
			onWhispered(whisperedTransaction.getTransaction());
	}

	@Override
	public Optional<Block> getBlock(byte[] hash) throws DatabaseException, NoSuchAlgorithmException, ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return blockchain.getBlock(hash);
		}
		catch (ClosedDatabaseException e) {
			throw unexpectedException(e); // the database cannot be closed because this node is open
		}
	}

	@Override
	public Optional<BlockDescription> getBlockDescription(byte[] hash) throws DatabaseException, NoSuchAlgorithmException, ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return blockchain.getBlockDescription(hash);
		}
		catch (ClosedDatabaseException e) {
			throw unexpectedException(e); // the database cannot be closed because this node is open
		}
	}

	@Override
	public Stream<PeerInfo> getPeerInfos() throws ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return peers.get();
		}
	}

	@Override
	public Stream<MinerInfo> getMinerInfos() throws ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return miners.getInfos();
		}
	}

	@Override
	public Stream<TaskInfo> getTaskInfos() throws TimeoutException, InterruptedException, ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return currentlyExecutingTasks.stream()
				.map(Object::toString)
				.map(TaskInfos::of);
		}
	}

	@Override
	public Optional<PeerInfo> add(Peer peer) throws TimeoutException, InterruptedException, ClosedNodeException, IOException, PeerRejectedException, DatabaseException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return peers.add(peer);
		}
		catch (ClosedDatabaseException e) {
			throw unexpectedException(e); // the database cannot be closed because this node is open
		}
	}

	@Override
	public boolean remove(Peer peer) throws DatabaseException, ClosedNodeException, InterruptedException, IOException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return peers.remove(peer);
		}
		catch (ClosedDatabaseException e) {
			throw unexpectedException(e); // the database cannot be closed because this node is open
		}
	}

	@Override
	public Optional<MinerInfo> openMiner(int port) throws IOException, ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return add(RemoteMiners.of(port, this::check));
		}
		catch (DeploymentException e) {
			throw new IOException(e);
		}
	}

	@Override
	public boolean closeMiner(UUID uuid) throws ClosedNodeException, IOException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			Optional<Miner> maybeMiner = miners.get().filter(miner -> miner.getUUID().equals(uuid)).findFirst();
			return maybeMiner.isPresent() && miners.remove(maybeMiner.get());
		}
	}

	@Override
	public void close() throws InterruptedException, DatabaseException, IOException {
		if (closureLock.stopNewCalls()) {
			executors.shutdownNow();
			periodicExecutors.shutdownNow();

			InterruptedException interruptedException = null;
			IOException ioException = null;
			
			for (var handler: onCloseHandlers) {
				try {
					handler.close();
				}
				catch (InterruptedException e) {
					interruptedException = e;
				}
				catch (IOException e) {
					ioException = e;
				}
			}

			try {
				executors.awaitTermination(3, TimeUnit.SECONDS);
				periodicExecutors.awaitTermination(3, TimeUnit.SECONDS);
			}
			finally {
				try {
					blockchain.close();
				}
				finally {
					try {
						peers.close();
					}
					finally {
						miners.close();
					}
				}
			}

			if (interruptedException != null)
				throw interruptedException;
			else if (ioException != null)
				throw ioException;
		}
	}

	@Override
	public NodeInfo getInfo() throws ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return peers.getNodeInfo();
		}
	}

	/**
	 * Yields the configuration of this node.
	 * 
	 * @return the configuration of this node
	 */
	@Override
	public LocalNodeConfig getConfig() {
		return config;
	}

	@Override
	public ChainInfo getChainInfo() throws DatabaseException, ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return blockchain.getChainInfo();
		}
		catch (ClosedDatabaseException e) {
			throw unexpectedException(e); // the database cannot be closed because this node is open
		}
	}

	@Override
	public ChainPortion getChainPortion(long start, int count) throws DatabaseException, ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return ChainPortions.of(blockchain.getChain(start, count));
		}
		catch (ClosedDatabaseException e) {
			throw unexpectedException(e); // the database cannot be closed because this node is open
		}
	}

	@Override
	public MempoolEntry add(Transaction transaction) throws RejectedTransactionException, ClosedNodeException, NoSuchAlgorithmException, DatabaseException {
		MempoolEntry result;

		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			result = mempool.add(transaction);
			var entry = new TransactionEntry(transaction, result.getPriority(), result.getHash());

			// TODO: maybe in its own thread?
			// we send the transaction also to all currently running mining tasks
			for (var handler: onAddedTransactionHandlers)
				handler.add(entry);
		}
		catch (ClosedDatabaseException e) {
			throw unexpectedException(e); // the database cannot be closed because this node is open
		}

		scheduleWhisperingWithoutAddition(transaction);
		onTransactionAdded(transaction);

		return result;
	}

	@Override
	public MempoolInfo getMempoolInfo() throws ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return mempool.getInfo();
		}
	}

	@Override
	public MempoolPortion getMempoolPortion(int start, int count) throws ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return mempool.getPortion(start, count);
		}
	}

	@Override
	public Optional<MinerInfo> add(Miner miner) throws ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			var count = miners.get().count();
			Optional<MinerInfo> result = miners.add(miner);
			// if there were no miners before this call, we require to mine
			if (count == 0L && result.isPresent())
				scheduleMining();
	
			return result;
		}
	}

	/**
	 * Yields the application running over this node.
	 * 
	 * @return the application
	 */
	public Application getApplication() {
		return app;
	}

	/**
	 * Yields the peers of this node.
	 * 
	 * @return the peers
	 */
	public Peers getPeers() {
		return peers;
	}

	/**
	 * Yields the miners of this node.
	 * 
	 * @return the miners
	 */
	public Miners getMiners() {
		return miners;
	}

	/**
	 * Yields the blockchain of this node.
	 * 
	 * @return the blockchain
	 */
	public Blockchain getBlockchain() {
		return blockchain;
	}

	/**
	 * Yields the key pair of this node. It is used to sign the blocks mined by this node.
	 * 
	 * @return the key pair
	 */
	public KeyPair getKeys() {
		return keyPair;
	}

	public interface OnAddedTransactionHandler {
		void add(TransactionEntry entry) throws NoSuchAlgorithmException, ClosedDatabaseException, DatabaseException;
	}

	/**
	 * A task is a complex activity that can be run in its own thread. Once it completes,
	 * it typically fires some events to signal something to the node.
	 */
	public interface Task {

		/**
		 * Main body of the task execution.
		 * 
		 * @throws Exception if the execution fails
		 */
		void body() throws Exception;
	}

	/**
	 * Determines if a deadline is legal for this node. This means that:
	 * <ul>
	 * <li> it is valid
	 * <li> its prolog specifies the same chain identifier as the node
	 * <li> its prolog uses a blocks signature public key that coincides with that of the node
	 * <li> the prolog uses a blocks signature algorithm that coincides with that of the node
	 * <li> the prolog uses a deadlines signature algorithm that coincides with that of the node
	 * <li> the extra bytes of the prolog are valid for the application
	 * </ul>
	 * 
	 * @param deadline the deadline to check
	 * @throws IllegalDeadlineException if and only if {@code deadline} is illegal
	 */
	protected void check(Deadline deadline) throws IllegalDeadlineException {
		var prolog = deadline.getProlog();
	
		if (!deadline.isValid())
			throw new IllegalDeadlineException("Invalid deadline");
		else if (!prolog.getChainId().equals(config.getChainId()))
			throw new IllegalDeadlineException("Wrong chain identifier in deadline");
		else if (!prolog.getPublicKeyForSigningBlocks().equals(keyPair.getPublic()))
			throw new IllegalDeadlineException("Wrong node key in deadline");
		else if (!prolog.getSignatureForBlocks().equals(config.getSignatureForBlocks()))
			throw new IllegalDeadlineException("Wrong blocks' signature algorithm in deadline");
		else if (!prolog.getSignatureForDeadlines().equals(config.getSignatureForDeadlines()))
			throw new IllegalDeadlineException("Wrong deadlines' signature algorithm in deadline");
		else if (!app.checkPrologExtra(prolog.getExtra()))
			throw new IllegalDeadlineException("Invalid extra data in deadline");
	}

	/**
	 * Determines if some mining task is currently mining immediately over the given block.
	 * 
	 * @param previous the block
	 * @return true if and only if that condition holds
	 */
	protected boolean isMiningOver(Block previous) {
		return blocksOverWhichMiningIsInProgress.contains(previous);
	}

	protected void rebaseMempoolAt(byte[] newHeadHash) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		mempool.rebaseAt(newHeadHash);
	}

	protected Stream<TransactionEntry> getMempoolTransactionsAt(byte[] newHeadHash) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		var result = new Mempool(mempool);
		result.rebaseAt(newHeadHash);
		return result.getTransactions();
	}

	/**
	 * Schedules a synchronization of the blockchain in this node, from the peers of the node,
	 * if the node is not currently performing a synchronization. Otherwise, nothing happens.
	 * 
	 * @param initialHeight the height of the blockchain from where synchronization must be applied
	 */
	protected void scheduleSynchronization(long initialHeight) {
		// we avoid to synchronize if synchronization is already in process
		if (isSynchronizing.getAndSet(true) == false)
			execute(new SynchronizationTask(this, initialHeight), "synchronization from the peers");
	}

	/**
	 * Schedules the mining of a next block on top of the current head.
	 */
	protected void scheduleMining() {
		// we avoid to mine during synchronization
		if (!isSynchronizing.get())
			execute(new MineNewBlockTask(this), "mining of next block");
	}

	/**
	 * Schedules the mining of a next block on top of the current head, after a delay.
	 */
	protected void scheduleDelayedMining() {
		// we avoid to mine during synchronization
		if (!isSynchronizing.get())
			execute(new DelayedMineNewBlockTask(this), "mining in " + config.getDeadlineWaitTimeout() + " ms");
	}

	/**
	 * Schedules the advertisement to its peers of the services published by this node.
	 */
	protected void scheduleWhisperingOfAllServices() {
		execute(this::whisperAllServices, "whispering of all node's services");
	}

	/**
	 * Schedules the whispering of some peers, but does not add them to this node.
	 * 
	 * @param peers the peers to whisper
	 */
	protected void scheduleWhisperingWithoutAddition(Stream<Peer> peers) {
		var peersAsArray = peers.distinct().toArray(Peer[]::new);

		int length = peersAsArray.length;
		if (length > 0) {
			var whisperedPeer = WhisperPeersMessages.of(Stream.of(peersAsArray), UUID.randomUUID().toString());
			String description = length == 1 ? ("peer " + SanitizedStrings.of(peersAsArray[0])) : ("peers " + SanitizedStrings.of(whisperedPeer.getPeers()));
			execute(() -> whisperWithoutAddition(whisperedPeer, description), "whispering of " + description);
		}
	}

	/**
	 * Schedules the whispering of a block, but does not add it to this node.
	 * 
	 * @param block the block to whisper
	 */
	protected void scheduleWhisperingWithoutAddition(Block block) {
		var whisperedBlock = WhisperBlockMessages.of(block, UUID.randomUUID().toString());
		String description = "block " + block.getHexHash(config.getHashingForBlocks());
		execute(() -> whisperWithoutAddition(whisperedBlock, description), "whispering of " + description);
	}

	/**
	 * Schedules the whispering of a transaction, but does not add it to this node.
	 * 
	 * @param transaction the transaction to whisper
	 */
	protected void scheduleWhisperingWithoutAddition(Transaction transaction) {
		var whisperedTransaction = WhisperTransactionMessages.of(transaction, UUID.randomUUID().toString());
		String description = "transaction " + hasherForTransactions.hash(transaction);
		execute(() -> whisperWithoutAddition(whisperedTransaction, description), "whispering of " + description);
	}

	/**
	 * Called when a peer gets connected.
	 * 
	 * @param peer the peer
	 */
	protected void onPeerConnected(Peer peer) {}

	/**
	 * Called when a peer has been added.
	 * 
	 * @param peer the added peer
	 */
	protected void onPeerAdded(Peer peer) {}

	/**
	 * Called when a peer gets disconnected.
	 * 
	 * @param peer the peer
	 */
	protected void onPeerDisconnected(Peer peer) {}

	/**
	 * Called when a peer has been removed.
	 * 
	 * @param peer the removed peer
	 */
	protected void onPeerRemoved(Peer peer) {}

	/**
	 * Called when a miner has been added.
	 * 
	 * @param miner the added miner
	 */
	protected void onMinerAdded(Miner miner) {}

	/**
	 * Called when a miner has been removed.
	 * 
	 * @param miner the removed miner
	 */
	protected void onMinerRemoved(Miner miner) {}

	/**
	 * Called when a transaction has been added to the mempool.
	 * 
	 * @param transaction the added transaction
	 */
	protected void onTransactionAdded(Transaction transaction) {}

	/**
	 * Called when no deadline has been found.
	 * 
	 * @param previous the block for whose subsequent block the deadline was being looked up
	 */
	protected void onNoDeadlineFound(Block previous) {}

	/**
	 * Called when a miner computes an illegal deadline.
	 * 
	 * @param deadline the illegal deadline
	 * @param miner the miner
	 */
	protected void onIllegalDeadlineComputed(Deadline deadline, Miner miner) {}

	/**
	 * Called when a node cannot mine because it has no miners attached.
	 */
	protected void onNoMinersAvailable() {}

	/**
	 * Called when mining immediately over the given block has been started.
	 * 
	 * @param previous the block
	 */
	protected void onMiningStarted(Block previous, OnAddedTransactionHandler handler) {
		blocksOverWhichMiningIsInProgress.add(previous);
		onAddedTransactionHandlers.add(handler);
	}

	/**
	 * Called when mining immediately over the given block stopped.
	 * 
	 * @param previous the block
	 */
	protected void onMiningCompleted(Block previous, OnAddedTransactionHandler handler) {
		blocksOverWhichMiningIsInProgress.remove(previous);
		onAddedTransactionHandlers.remove(handler);
	}

	/**
	 * Called when a synchronization from the peers has been completed.
	 */
	protected void onSynchronizationCompleted() {
		isSynchronizing.set(false);
		// after synchronization, we let the blockchain start to mine its blocks
		scheduleMining();
	}

	/**
	 * Called when a block gets added to the blockchain.
	 * 
	 * @param block the added block
	 */
	protected void onBlockAdded(Block block) {}

	/**
	 * Called when the head of the blockchain has been updated.
	 * 
	 * @param newHeadHash the hash of the new head
	 */
	protected void onHeadChanged(byte[] newHeadHash) {}

	/**
	 * Called when the node mines a new block.
	 * 
	 * @param block the mined block
	 */
	protected void onBlockMined(Block block) {}

	/**
	 * Called when some peers have been whispered to our peers.
	 * 
	 * @param peers the whispered peers
	 */
	protected void onWhispered(Stream<Peer> peers) {}

	/**
	 * Called when a block has been whispered to our peers.
	 * 
	 * @param block the whispered block
	 */
	protected void onWhispered(Block block) {}

	/**
	 * Called when a transaction has been whispered to our peers.
	 * 
	 * @param transaction the whispered transaction
	 */
	protected void onWhispered(Transaction transaction) {}

	/**
	 * An adapter of a task into a runnable with logs.
	 */
	private class RunnableTask implements Runnable {
		private final Task task;
		private final String description;
	
		private RunnableTask(Task task, String description) {
			this.task = task;
			this.description = description;
		}
	
		@Override
		public final void run() {
			currentlyExecutingTasks.add(this);
	
			try {
				task.body();
			}
			catch (InterruptedException e) {
				LOGGER.log(Level.WARNING, "node " + uuid + ": " + this + " interrupted");
				Thread.currentThread().interrupt();
				return;
			}
			catch (Exception e) {
				LOGGER.log(Level.SEVERE, "node " + uuid + ": " + this + " failed", e);
				return;
			}
			finally {
				currentlyExecutingTasks.remove(this);
			}
		}
	
		@Override
		public String toString() {
			return description;
		}
	}

	/**
	 * Runs the given task, asynchronously, in one thread from the {@link #executors} executor.
	 * 
	 * @param task the task to run
	 */
	private void execute(Task task, String description) {
		var runnable = new RunnableTask(task, description);
		try {
			executors.execute(runnable);
			LOGGER.info("node " + uuid + ": " + runnable + " scheduled");
		}
		catch (RejectedExecutionException e) {
			LOGGER.warning("node " + uuid + ": " + runnable + " rejected, probably because the node is shutting down");
		}
	}

	public Future<?> submit(Task task, String description) throws RejectedExecutionException {
		var runnable = new RunnableTask(task, description);
		try {
			var future = executors.submit(runnable);
			LOGGER.info("node " + uuid + ": " + runnable + " scheduled");
			return future;
		}
		catch (RejectedExecutionException e) {
			LOGGER.warning("node " + uuid + ": " + runnable + " rejected, probably because the node is shutting down");
			throw e;
		}
	}

	/**
	 * Runs the given task, periodically, with the {@link #periodicExecutors} executor.
	 * 
	 * @param task the task to run
	 * @param initialDelay the time to wait before running the task
	 * @param delay the time interval between successive, iterated executions
	 * @param unit the time interval unit
	 */
	private void scheduleWithFixedDelay(Task task, String description, long initialDelay, long delay, TimeUnit unit) {
		var runnable = new RunnableTask(task, description);
	
		try {
			periodicExecutors.scheduleWithFixedDelay(runnable, initialDelay, delay, unit);
			LOGGER.info("node " + uuid + ": " + runnable + " scheduled every " + delay + " " + unit);
		}
		catch (RejectedExecutionException e) {
			LOGGER.warning("node " + uuid + ": " + runnable + " rejected, probably because the node is shutting down");
		}
	}

	/**
	 * Schedules a periodic task that whispers the services open on this node.
	 */
	private void schedulePeriodicWhisperingOfAllServices() {
		long serviceBroadcastInterval = config.getServiceBrodcastInterval();
		if (serviceBroadcastInterval >= 0)
			scheduleWithFixedDelay(this::whisperAllServices, "whispering of all node's services", 0L, serviceBroadcastInterval, TimeUnit.MILLISECONDS);
	}

	/**
	 * Schedules a periodic task that pings all peers, recreates their remotes and adds the peers of such peers.
	 */
	private void schedulePeriodicPingToAllPeersRecreateRemotesAndAddTheirPeers() {
		var interval = config.getPeerPingInterval();
		if (interval >= 0)
			scheduleWithFixedDelay(peers::pingAllRecreateRemotesAndAddTheirPeers,
				"pinging all peers to create missing remotes and collect their peers", 0L, interval, TimeUnit.MILLISECONDS);
	}

	private void whisperWithoutAddition(Whispered whispered, String description) {
		alreadyWhispered.add(whispered);
		peers.whisper(whispered, isThis, description);
		boundWhisperers.forEach(whisperer -> whisperer.whisper(whispered, isThis, description));
	}

	private void whisperAllServices() {
		// we check how the external world sees our services as peers
		var servicesAsPeers = boundWhisperers.stream()
			.filter(whisperer -> whisperer instanceof PublicNodeService)
			.map(whisperer -> (PublicNodeService) whisperer)
			.map(PublicNodeService::getURI)
			.flatMap(Optional::stream)
			.map(io.mokamint.node.Peers::of)
			.distinct()
			.toArray(Peer[]::new);

		int length = servicesAsPeers.length;
		if (length > 0) {
			var whisperedPeers = WhisperPeersMessages.of(Stream.of(servicesAsPeers), UUID.randomUUID().toString());
			String description = length == 1 ? ("peer " + SanitizedStrings.of(servicesAsPeers[0])) : ("peers " + SanitizedStrings.of(whisperedPeers.getPeers()));
			whisperWithoutAddition(whisperedPeers, description);
		}
	}

	private RuntimeException unexpectedException(Exception e) {
		LOGGER.log(Level.SEVERE, "node " + uuid + ": unexpected exception", e);
		return new RuntimeException("Unexpected exception", e);
	}
}
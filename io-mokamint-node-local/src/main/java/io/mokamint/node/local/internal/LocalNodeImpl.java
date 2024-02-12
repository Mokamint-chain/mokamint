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
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.remote.RemoteMiners;
import io.mokamint.node.ChainPortions;
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
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.api.Whispered;
import io.mokamint.node.api.WhisperedBlock;
import io.mokamint.node.api.WhisperedPeer;
import io.mokamint.node.api.WhisperedTransaction;
import io.mokamint.node.api.Whisperer;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.api.LocalNode;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.Mempool.TransactionEntry;
import io.mokamint.node.messages.WhisperBlockMessages;
import io.mokamint.node.messages.WhisperPeerMessages;
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
	 * The miners that must be closed when this node is closed. These are those
	 * created in {@link #openMiner(int)}.
	 */
	private final Set<Miner> minersToCloseAtTheEnd = ConcurrentHashMap.newKeySet();

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
	 * The task that is mining new blocks.
	 */
	private final MiningTask miningTask;

	private final Predicate<Whisperer> isThis = Predicate.isEqual(this);

	/**
	 * The queue of the whispered peers to process.
	 */
	private final BlockingQueue<WhisperedInfo> whisperedPeersQueue = new ArrayBlockingQueue<>(1000);
	
	/**
	 * The queue of the whispered blocks to process.
	 */
	private final BlockingQueue<WhisperedInfo> whisperedBlocksQueue = new ArrayBlockingQueue<>(1000);
	
	/**
	 * The queue of the whispered transactions to process.
	 */
	private final BlockingQueue<WhisperedInfo> whisperedTransactionsQueue = new ArrayBlockingQueue<>(1000);

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
	 * @throws TimeoutException if the application did not answer in time
	 */
	public LocalNodeImpl(LocalNodeConfig config, KeyPair keyPair, Application app, boolean init) throws DatabaseException, IOException, InterruptedException, AlreadyInitializedException, InvalidKeyException, SignatureException, TimeoutException {
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
			peers.reconnectToSeedsAndPreviousPeers();

			if (init)
				blockchain.initialize();
			else
				scheduleSynchronization(0L);

			execute(this::processWhisperedPeers, "peers whispering process");
			execute(this::processWhisperedBlocks, "blocks whispering process");
			execute(this::processWhisperedTransactions, "transactions whispering process");
			schedulePeriodicPingToAllPeersRecreateRemotesAndAddTheirPeers();
			schedulePeriodicWhisperingOfAllServices();
			execute(this.miningTask = new MiningTask(this), "blocks mining process");
		}
		catch (ClosedNodeException | ClosedDatabaseException e) {
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
		// since a new whisperer arrived, it might be the time to inform everybody about our services
		whisperAllServices();
	}

	@Override
	public void unbindWhisperer(Whisperer whisperer) {
		boundWhisperers.remove(whisperer);
	}

	private static class WhisperedInfo {
		private final Whispered whispered;
		private final Predicate<Whisperer> seen;
		private final String description;
		private final boolean add;

		private WhisperedInfo(Whispered whispered, Predicate<Whisperer> seen, String description, boolean add) {
			this.whispered = whispered;
			this.seen = seen;
			this.description = description;
			this.add = add;
		}
	}

	@Override
	public void whisper(Whispered whispered, Predicate<Whisperer> seen, String description) {
		if (!seen.test(this) && alreadyWhispered.add(whispered))
			if (whispered instanceof WhisperedPeer)
				whisperedPeersQueue.offer(new WhisperedInfo(whispered, seen, description, true));
			else if (whispered instanceof WhisperedBlock)
				whisperedBlocksQueue.offer(new WhisperedInfo(whispered, seen, description, true));
			else if (whispered instanceof WhisperedTransaction)
				whisperedTransactionsQueue.offer(new WhisperedInfo(whispered, seen, description, true));
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
				for (var miner: minersToCloseAtTheEnd)
					miner.close();

				executors.awaitTermination(5, TimeUnit.SECONDS);
				periodicExecutors.awaitTermination(5, TimeUnit.SECONDS);
			}
			finally {
				try {
					peers.close();
				}
				finally {
					blockchain.close();
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
	public MempoolEntry add(Transaction transaction) throws RejectedTransactionException, ClosedNodeException, NoSuchAlgorithmException, DatabaseException, TimeoutException, InterruptedException {
		MempoolEntry result;

		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			result = mempool.add(transaction);

			if (miningTask != null)
				miningTask.add(new TransactionEntry(transaction, result.getPriority(), result.getHash()));
		}
		catch (ClosedDatabaseException e) {
			throw unexpectedException(e); // the database cannot be closed because this node is open
		}

		whisperWithoutAddition(transaction);

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
	public Optional<Transaction> getTransaction(byte[] hash) throws DatabaseException, NoSuchAlgorithmException, ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return blockchain.getTransaction(hash);
		}
		catch (ClosedDatabaseException e) {
			throw unexpectedException(e); // the database cannot be closed because this node is open
		}
	}

	@Override
	public Optional<String> getTransactionRepresentation(byte[] hash) throws RejectedTransactionException, DatabaseException, ClosedNodeException, NoSuchAlgorithmException, TimeoutException, InterruptedException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			Optional<Transaction> maybeTransaction = blockchain.getTransaction(hash);
			if (maybeTransaction.isEmpty())
				return Optional.empty();
			else
				return Optional.of(app.getRepresentation(maybeTransaction.get()));
		}
		catch (ClosedDatabaseException e) {
			throw unexpectedException(e); // the database cannot be closed because this node is open
		}
	}

	@Override
	public Optional<TransactionAddress> getTransactionAddress(byte[] hash) throws ClosedNodeException, DatabaseException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			return blockchain.getTransactionAddress(hash);
		}
		catch (ClosedDatabaseException e) {
			throw unexpectedException(e); // the database cannot be closed because this node is open
		}
	}

	@Override
	public Optional<PeerInfo> add(Peer peer) throws TimeoutException, InterruptedException, ClosedNodeException, IOException, PeerRejectedException, DatabaseException {
		Optional<PeerInfo> result;
	
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			result = peers.add(peer);
		}
		catch (ClosedDatabaseException e) {
			throw unexpectedException(e); // the database cannot be closed because this node is open
		}
	
		if (result.isPresent()) {
			scheduleSynchronization(0L);
			scheduleWhisperingOfAllServices();
			whisperWithoutAddition(peer);
		}
	
		return result;
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
			var miner = RemoteMiners.of(port, this::check);
			Optional<MinerInfo> maybeInfo = miners.add(miner);
			if (maybeInfo.isPresent()) {
				minersToCloseAtTheEnd.add(miner);
				onAdded(miner);
			}
			else
				miner.close();

			return maybeInfo;
		}
		catch (DeploymentException e) {
			throw new IOException(e);
		}
	}

	@Override
	public Optional<MinerInfo> add(Miner miner) throws ClosedNodeException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			Optional<MinerInfo> maybeInfo = miners.add(miner);
			if (maybeInfo.isPresent())
				onAdded(miner);

			return maybeInfo;
		}
	}

	@Override
	public boolean removeMiner(UUID uuid) throws ClosedNodeException, IOException {
		try (var scope = closureLock.scope(ClosedNodeException::new)) {
			var toRemove = miners.get().filter(miner -> miner.getUUID().equals(uuid)).toArray(Miner[]::new);
			for (var miner: toRemove) {
				miners.remove(miner);
				if (minersToCloseAtTheEnd.contains(miner))
					miner.close();
			}
	
			return toRemove.length > 0;
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

	/**
	 * Yields the hasher that can be used for hashing the transactions.
	 * 
	 * @return the hasher
	 */
	protected Hasher<Transaction> getHasherForTransactions() {
		return hasherForTransactions;
	}

	/**
	 * Punishes a miner, by reducing its points. If the miner reaches zero points,
	 * it gets removed from the set of miners of this node. If the miner was not present in this
	 * node, nothing happens.
	 * 
	 * @param miner the miner to punish
	 * @param points how many points get removed
	 */
	protected void punish(Miner miner, long points) {
		LOGGER.info("punishing miner " + miner.getUUID() + " by removing " + points + " points");
	
		if (miners.punish(miner, points) && minersToCloseAtTheEnd.contains(miner)) {
			try {
				miner.close();
			}
			catch (IOException e) {
				LOGGER.warning("cannot close miner " + miner.getUUID() + ": " + e.getMessage());
			}
		}
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
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws TimeoutException if the application does not answer in time
	 */
	protected void check(Deadline deadline) throws IllegalDeadlineException, TimeoutException, InterruptedException {
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
	 * Rebases the mempool of this node so that it is relative to the given {@code block}.
	 * This means that a common ancestor is found, between the current mempool base and {@code block}.
	 * All transactions from the current mempool base to the ancestor are added to the mempool
	 * and all transactions from the ancestor to {@code block} are removed from the mempool.
	 * This method is typically called when the head of the blockchain is updated, so that the
	 * mempool can be updated as well.
	 * 
	 * @param block the block
	 * @throws NoSuchAlgorithmException if some block in blockchain refers to an unknown cryptographic algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws TimeoutException if the application does not answer in time
	 */
	protected void rebaseMempoolAt(Block block) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException, TimeoutException, InterruptedException {
		mempool.rebaseAt(block);
	}

	/**
	 * Yields the transactions from the mempool, rebased at the given {@code block} (see
	 * {@link #rebaseMempoolAt(Block)}. The mempool of this node is not modified.
	 * 
	 * @param block the block
	 * @return the transactions, in decreasing order of priority
	 * @throws NoSuchAlgorithmException if some block in blockchain refers to an unknown cryptographic algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws TimeoutException if the application did not answer in time
	 */
	protected Stream<TransactionEntry> getMempoolTransactionsAt(Block block) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException, TimeoutException, InterruptedException {
		var result = new Mempool(mempool); // clone the mempool
		result.rebaseAt(block); // rebase the clone
		return result.getTransactions(); // extract the resulting transactions
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
	 * Determines if synchronization has been requested for this node.
	 * 
	 * @return true if and only if that condition holds
	 */
	protected boolean isSynchronizing() {
		return isSynchronizing.get();
	}

	/**
	 * Schedules the advertisement to its peers of the services published by this node.
	 */
	protected void scheduleWhisperingOfAllServices() {
		execute(this::whisperAllServices, "whispering of all node's services");
	}

	/**
	 * Whispers a peer, but does not add it to this node.
	 * 
	 * @param peer the peer to whisper
	 */
	private void whisperWithoutAddition(Peer peer) {
		var whisperedPeers = WhisperPeerMessages.of(peer, UUID.randomUUID().toString());
		alreadyWhispered.add(whisperedPeers);
		String description = "peer " + peer.toStringSanitized();
		whisperedPeersQueue.offer(new WhisperedInfo(whisperedPeers, isThis, description, false));
	}

	/**
	 * Whispers a peer, but does not add it to this node.
	 * 
	 * @param uri the URI of the peer to whisper
	 */
	private void whisperWithoutAddition(URI uri) {
		var whisperedPeers = WhisperPeerMessages.of(uri, UUID.randomUUID().toString());
		alreadyWhispered.add(whisperedPeers);
		String description = "peer " + whisperedPeers.getPeer().toStringSanitized();
		whisperedPeersQueue.offer(new WhisperedInfo(whisperedPeers, isThis, description, false));
	}

	/**
	 * Whispers a block, but does not add it to this node.
	 * 
	 * @param block the block to whisper
	 */
	protected void whisperWithoutAddition(Block block) {
		var whisperedBlock = WhisperBlockMessages.of(block, UUID.randomUUID().toString());
		alreadyWhispered.add(whisperedBlock);
		String description = "block " + block.getHexHash(config.getHashingForBlocks());
		whisperedBlocksQueue.offer(new WhisperedInfo(whisperedBlock, isThis, description, false));
	}

	/**
	 * Whispers a transaction, but does not add it to this node.
	 * 
	 * @param transaction the transaction to whisper
	 */
	private void whisperWithoutAddition(Transaction transaction) {
		var whisperedTransaction = WhisperTransactionMessages.of(transaction, UUID.randomUUID().toString());
		alreadyWhispered.add(whisperedTransaction);
		String description = "transaction " + transaction.getHexHash(hasherForTransactions);
		whisperedTransactionsQueue.offer(new WhisperedInfo(whisperedTransaction, isThis, description, false));
	}

	/**
	 * Schedules the execution of a transactions executor.
	 * 
	 * @param task the transactions executor task to start
	 * @return the future to the result of the task
	 * @throws RejectedExecutionException if the task could not be started
	 */
	protected Future<?> scheduleTransactionExecutor(TransactionsExecutionTask task) throws RejectedExecutionException {
		return submit(task, "transactions execution from state " + Hex.toHexString(task.getPrevious().getStateId()));
	}

	/**
	 * Called when a peer has been added.
	 * 
	 * @param peer the added peer
	 */
	protected void onAdded(Peer peer) {
		LOGGER.info("added peer " + peer.toStringSanitized());
	}

	/**
	 * Called when a peer gets connected.
	 * 
	 * @param peer the peer
	 */
	protected void onConnected(Peer peer) {
		LOGGER.info("connected to peer " + peer.toStringSanitized());
	}

	/**
	 * Called when a peer gets disconnected.
	 * 
	 * @param peer the peer
	 */
	protected void onDisconnected(Peer peer) {
		LOGGER.info("disconnected from peer " + peer.toStringSanitized());
	}

	/**
	 * Called when a peer has been removed.
	 * 
	 * @param peer the removed peer
	 */
	protected void onRemoved(Peer peer) {
		LOGGER.info("removed peer " + peer.toStringSanitized());
	}

	/**
	 * Called when a miner has been added.
	 * 
	 * @param miner the added miner
	 */
	protected void onAdded(Miner miner) {
		LOGGER.info("added miner " + miner.getUUID() + " (" + miner + ")");
		if (miningTask != null)
			miningTask.onMinerAdded();
	}

	/**
	 * Called when a miner has been removed.
	 * 
	 * @param miner the removed miner
	 */
	protected void onRemoved(Miner miner) {
		LOGGER.info("removed miner " + miner.getUUID() + " (" + miner + ")");
	}

	/**
	 * Called when a transaction has been added to the mempool.
	 * 
	 * @param transaction the added transaction
	 */
	protected void onAdded(Transaction transaction) {}

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
	 * @param previous the block over which mining has been started
	 */
	protected void onMiningStarted(Block previous) {}

	/**
	 * Called when mining immediately over the given block stopped.
	 * 
	 * @param previous the block over which mining has been completed
	 */
	protected void onMiningCompleted(Block previous) {}

	/**
	 * Called when a synchronization from the peers has been completed.
	 */
	protected void onSynchronizationCompleted() {
		isSynchronizing.set(false);
		if (miningTask != null)
			miningTask.onSynchronizationCompleted();
	}

	/**
	 * Called when a block gets added to the blockchain.
	 * 
	 * @param block the added block
	 */
	protected void onAdded(Block block) {
		if (miningTask != null)
			miningTask.onBlockAdded();
	}

	/**
	 * Called when the head of the blockchain has been updated.
	 * 
	 * @param newHead the new head
	 */
	protected void onHeadChanged(Block newHead) {
		if (miningTask != null)
			miningTask.restartFromCurrentHead();
	}

	/**
	 * Called when the node mines a new block.
	 * 
	 * @param block the mined block
	 */
	protected void onMined(Block block) {}

	/**
	 * Called when some peer has been whispered to our peers.
	 * 
	 * @param peer the whispered peer
	 */
	protected void onWhispered(Peer peer) {}

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
				LOGGER.warning("node " + uuid + ": " + this + " interrupted");
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

	private Future<?> submit(Task task, String description) throws RejectedExecutionException {
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

	/**
	 * Processes the whispered objects received by this node, until interrupted.
	 */
	private void processWhisperedPeers() {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				/*if (whisperedPeersQueue.size() > 5) {
					System.out.println(whisperedPeersQueue.stream().map(info -> info.description).sorted().collect(Collectors.joining("\n")));
					System.out.println("*******************************************************");
				}*/
				var whisperedInfo = whisperedPeersQueue.take();

				try {
					if (whisperedInfo.add)
						if (whisperedInfo.whispered instanceof WhisperedPeer whisperedPeers)
							peers.add(whisperedPeers.getPeer());

					var whispered = whisperedInfo.whispered;
					Predicate<Whisperer> newSeen = whisperedInfo.seen.or(isThis);
					peers.whisper(whispered, newSeen, whisperedInfo.description);
					boundWhisperers.forEach(whisperer -> whisperer.whisper(whispered, newSeen, whisperedInfo.description));

					if (whispered instanceof WhisperedPeer whisperedPeers)
						onWhispered(whisperedPeers.getPeer());
				}
				catch (DatabaseException e) {
					LOGGER.log(Level.SEVERE, "node " + uuid + ": whispered " + whisperedInfo.description + " could not be added", e);
				}
				catch (ClosedNodeException | ClosedDatabaseException | IOException | PeerRejectedException | TimeoutException e) {
					LOGGER.warning("node " + uuid + ": whispered " + whisperedInfo.description + " could not be added: " + e.getMessage());
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Processes the whispered objects received by this node, until interrupted.
	 */
	private void processWhisperedBlocks() {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				/*if (whisperedPeersQueue.size() > 5) {
					System.out.println(whisperedPeersQueue.stream().map(info -> info.description).sorted().collect(Collectors.joining("\n")));
					System.out.println("*******************************************************");
				}*/
				var whisperedInfo = whisperedBlocksQueue.take();

				try {
					var whispered = whisperedInfo.whispered;

					if (whisperedInfo.add)
						if (whispered instanceof WhisperedBlock whisperedBlock) {
							try {
								blockchain.add(whisperedBlock.getBlock());
							}
							catch (TimeoutException e) {
								LOGGER.warning("node " + uuid + ": whispered " + whisperedInfo.description + " could not be added: " + e.getMessage());
							}
						}

					Predicate<Whisperer> newSeen = whisperedInfo.seen.or(isThis);
					peers.whisper(whispered, newSeen, whisperedInfo.description);
					boundWhisperers.forEach(whisperer -> whisperer.whisper(whispered, newSeen, whisperedInfo.description));

					if (whispered instanceof WhisperedBlock whisperedBlock)
						onWhispered(whisperedBlock.getBlock());
				}
				catch (NoSuchAlgorithmException | DatabaseException e) {
					LOGGER.log(Level.SEVERE, "node " + uuid + ": whispered " + whisperedInfo.description + " could not be added", e);
				}
				catch (VerificationException | ClosedDatabaseException e) {
					LOGGER.warning("node " + uuid + ": whispered " + whisperedInfo.description + " could not be added: " + e.getMessage());
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
	
	/**
	 * Processes the whispered objects received by this node, until interrupted.
	 */
	private void processWhisperedTransactions() {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				/*if (whisperedPeersQueue.size() > 5) {
					System.out.println(whisperedPeersQueue.stream().map(info -> info.description).sorted().collect(Collectors.joining("\n")));
					System.out.println("*******************************************************");
				}*/
				var whisperedInfo = whisperedTransactionsQueue.take();

				try {
					if (whisperedInfo.add)
						if (whisperedInfo.whispered instanceof WhisperedTransaction whisperedTransaction) {
							try {
								mempool.add(whisperedTransaction.getTransaction());
							}
							catch (TimeoutException e) {
								LOGGER.warning("node " + uuid + ": whispered " + whisperedInfo.description + " could not be added: " + e.getMessage());
							}
						}

					var whispered = whisperedInfo.whispered;
					Predicate<Whisperer> newSeen = whisperedInfo.seen.or(isThis);
					peers.whisper(whispered, newSeen, whisperedInfo.description);
					boundWhisperers.forEach(whisperer -> whisperer.whisper(whispered, newSeen, whisperedInfo.description));

					if (whispered instanceof WhisperedTransaction whisperedTransaction)
						onWhispered(whisperedTransaction.getTransaction());
				}
				catch (NoSuchAlgorithmException | DatabaseException e) {
					LOGGER.log(Level.SEVERE, "node " + uuid + ": whispered " + whisperedInfo.description + " could not be added", e);
				}
				catch (RejectedTransactionException | ClosedDatabaseException e) {
					LOGGER.warning("node " + uuid + ": whispered " + whisperedInfo.description + " could not be added: " + e.getMessage());
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
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
			.forEach(this::whisperWithoutAddition);
	}

	private RuntimeException unexpectedException(Exception e) {
		LOGGER.log(Level.SEVERE, "node " + uuid + ": unexpected exception", e);
		return new RuntimeException("Unexpected exception", e);
	}
}
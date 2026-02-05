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

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.Deque;
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
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.closeables.AbstractAutoCloseableWithLockAndOnCloseHandlers;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.mokamint.application.AbstractApplication;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.application.api.Info;
import io.mokamint.miner.MiningSpecifications;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.remote.RemoteMiners;
import io.mokamint.miner.remote.api.DeadlineValidityCheckException;
import io.mokamint.miner.remote.api.IllegalDeadlineException;
import io.mokamint.node.ChainPortions;
import io.mokamint.node.Memories;
import io.mokamint.node.TaskInfos;
import io.mokamint.node.api.ApplicationTimeoutException;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.ClosedPeerException;
import io.mokamint.node.api.Memory;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.api.MempoolPortion;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.api.PortionRejectedException;
import io.mokamint.node.api.TaskInfo;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.api.TransactionRejectedException;
import io.mokamint.node.api.WhisperMessage;
import io.mokamint.node.api.Whisperable;
import io.mokamint.node.api.Whisperer;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.LocalNodeException;
import io.mokamint.node.local.SynchronizationException;
import io.mokamint.node.local.api.LocalNode;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.Mempool.TransactionEntry;
import io.mokamint.node.messages.WhisperBlockMessages;
import io.mokamint.node.messages.WhisperPeerMessages;
import io.mokamint.node.messages.WhisperTransactionMessages;
import io.mokamint.node.messages.api.WhisperBlockMessage;
import io.mokamint.node.messages.api.WhisperPeerMessage;
import io.mokamint.node.messages.api.WhisperTransactionMessage;
import io.mokamint.node.service.api.PublicNodeService;
import io.mokamint.nonce.api.Deadline;

/**
 * A local node of a Mokamint blockchain.
 */
@ThreadSafe
public class LocalNodeImpl extends AbstractAutoCloseableWithLockAndOnCloseHandlers<ClosedNodeException> implements LocalNode {

	/**
	 * The configuration of the node.
	 */
	private final LocalNodeConfig config;

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
	private final MinersSet miners;

	/**
	 * The peers of the node.
	 */
	private final PeersSet peers;

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
	 * The executor of tasks. There might be more tasks in execution at the same time.
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
	 * The whisperers bound to this node.
	 */
	private final CopyOnWriteArrayList<Whisperer> boundWhisperers = new CopyOnWriteArrayList<>();

	/**
	 * A memory of the last whispered things.
	 * This is used to avoid whispering already whispered messages again.
	 */
	private final Memory<Whisperable> alreadyWhispered;

	/**
	 * A memory of the last whispered peer messages. This is used to avoid whispering already whispered messages again.
	 * We use a different memory than {@link #alreadyWhispered} since we want to allow peers to be
	 * whispered also after being whispered already.
	 */
	private final Memory<WhisperPeerMessage> peersAlreadyWhispered;

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
	private final BlockingQueue<WhisperedInfo<WhisperPeerMessage>> whisperedPeersQueue = new ArrayBlockingQueue<>(1000);
	
	/**
	 * The queue of the whispered blocks to process.
	 */
	private final BlockingQueue<WhisperedInfo<WhisperBlockMessage>> whisperedBlocksQueue = new ArrayBlockingQueue<>(1000);
	
	/**
	 * The queue of the whispered transactions to process.
	 */
	private final BlockingQueue<WhisperedInfo<WhisperTransactionMessage>> whisperedTransactionsQueue = new ArrayBlockingQueue<>(1000);

	/**
	 * The queue of blocks to publish.
	 */
	private final BlockingQueue<Block> blocksToPublishQueue = new ArrayBlockingQueue<>(1000);

	/**
	 * The mining specification of every miner that works with this node.
	 */
	private final MiningSpecification miningSpecification;

	/**
	 * An exception thrown if the last synchronization could not be completed.
	 */
	private volatile SynchronizationException synchronizationException;

	private final static Logger LOGGER = Logger.getLogger(LocalNodeImpl.class.getName());

	/**
	 * Creates a local node of a Mokamint blockchain, for the given application.
	 * 
	 * @param config the configuration of the node
	 * @param keyPair the key pair that the node will use to sign the blocks that it mines
	 * @param app the application
	 * @param init if true, creates a genesis block and starts mining on top (initial synchronization is consequently skipped)
	 * @throws ClosedApplicationException if {@code app} is already closed
	 * @throws ApplicationTimeoutException if {@code app} is unresponsive
	 * @throws InterruptedException if the initialization of the node was interrupted
	 */
	public LocalNodeImpl(LocalNodeConfig config, KeyPair keyPair, Application app, boolean init) throws ClosedApplicationException, ApplicationTimeoutException, InterruptedException {
		this(config, keyPair, app, getInfo(app), init);
	}

	/**
	 * Creates a local node of a Mokamint blockchain, for the given application.
	 * 
	 * @param config the configuration of the node
	 * @param keyPair the key pair that the node will use to sign the blocks that it mines
	 * @param app the application
	 * @param init if true, creates a genesis block and starts mining on top (initial synchronization is consequently skipped)
	 * @throws InterruptedException if the initialization of the node was interrupted
	 * @throws ClosedApplicationException if {@code app} is already closed
	 */
	public LocalNodeImpl(LocalNodeConfig config, KeyPair keyPair, AbstractApplication app, boolean init) throws InterruptedException, ClosedApplicationException {
		this(config, keyPair, app, app.getInfo(), init);
	}

	/**
	 * Creates a local node of a Mokamint blockchain, for the given application.
	 * 
	 * @param config the configuration of the node
	 * @param keyPair the key pair that the node will use to sign the blocks that it mines
	 * @param app the application
	 * @param init if true, creates a genesis block and starts mining on top (initial synchronization is consequently skipped)
	 * @throws InterruptedException if the initialization of the node was interrupted
	 */
	private LocalNodeImpl(LocalNodeConfig config, KeyPair keyPair, Application app, Info appInfo, boolean init) throws InterruptedException {
		super(ClosedNodeException::new);

		this.config = config;
		this.keyPair = keyPair;
		this.app = app;
		this.miningSpecification = MiningSpecifications.of(
			appInfo.getName(), appInfo.getDescription(),
			config.getChainId(), config.getHashingForDeadlines(), config.getSignatureForBlocks(), config.getSignatureForDeadlines(),
			getKeys().getPublic()
		);
		this.peersAlreadyWhispered = Memories.of(config.getWhisperingMemorySize());
		this.alreadyWhispered = Memories.of(config.getWhisperingMemorySize());
		this.miningTask = new MiningTask(this);
		this.miners = new MinersSet(this);

		try {
			this.blockchain = new Blockchain(this);
			this.mempool = new Mempool(this);
			this.peers = new PeersSet(this);
			this.uuid = getInfo().getUUID();
		}
		catch (ClosedNodeException e) { // the node itself cannot be already closed
			throw new LocalNodeException("The node has been unexpectedly closed", e);
		}

		// if the application gets closed, this node cannot work properly anymore and we close it as well
		app.addOnCloseHandler(this::close);

		if (init)
			initialize();

		try {
			// we force a connection to the peers of our peers, in order to synchronize with as many peers as possible later
			if (config.getPeerPingInterval() >= 0)
				peers.pingAllAndReconnect();
		}
		catch (ClosedNodeException | ClosedDatabaseException e) { // the node itself cannot be already closed
			throw new LocalNodeException("The node has been unexpectedly closed", e);
		}

		scheduleTasks();
	}

	/**
	 * Yields the information about the given application.
	 * 
	 * @param app the application
	 * @return the information about {@code app}
	 * @throws ClosedApplicationException if {@code app} is already closed
	 * @throws ApplicationTimeoutException if the operation times out
	 * @throws InterruptedException if the operation gets interrupted before completion
	 */
	private static Info getInfo(Application app) throws ClosedApplicationException, ApplicationTimeoutException, InterruptedException {
		try {
			return app.getInfo();
		}
		catch (TimeoutException e) {
			throw new ApplicationTimeoutException(e);
		}
	}

	private void initialize() throws InterruptedException {
		try {
			blockchain.initialize();
		}
		catch (AlreadyInitializedException e) {
			close();
			throw new LocalNodeException("The blockchain database is already initialized: delete \"" + config.getDir() + "\" and try again", e);
		}
		catch (ApplicationTimeoutException e) {
			close();
			throw new LocalNodeException("The application timed out", e);
		}
		catch (InvalidKeyException | SignatureException e) {
			close();
			throw new LocalNodeException("The genesis block could not be signed", e);
		}
		catch (ClosedDatabaseException e) {
			close();
			 // the database cannot be already closed
			throw new LocalNodeException("The database has been unexpectedly closed", e);
		}
		catch (ClosedApplicationException e) {
			close();
			throw new LocalNodeException("The application is already closed", e);
		}
		catch (MisbehavingApplicationException e) {
			close();
			throw new LocalNodeException("The application is misbehaving", e);
		}
	}

	private void scheduleTasks() {
		try {
			schedulePeriodicSynchronization();
			schedule(this::processWhisperedPeers, () -> "peers whispering process");
			schedule(this::processWhisperedBlocks, () -> "blocks whispering process");
			schedule(this::processWhisperedTransactions, () -> "transactions whispering process");
			schedule(this::publisher, () -> "blocks publishing process");
			schedulePeriodicPingToAllPeersAndReconnection();
			schedulePeriodicWhisperingOfAllServices();
			schedulePeriodicIdentificationOfTheNonFrozenPartOfBlockchain();
			schedule(miningTask, () -> "blocks mining process");
		}
		catch (TaskRejectedExecutionException e) {
			close();
			throw new LocalNodeException("Could not spawn the node's tasks", e);
		}
	}

	@Override
	public void close() {
		try {
			if (stopNewCalls())
				closeExecutorsHandlersMinersPeersAndBlockchain();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
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

	private static class WhisperedInfo<M extends WhisperMessage<?>> {
		private final M message;
		private final Predicate<Whisperer> seen;
		private final String description;
		private final boolean add;

		private WhisperedInfo(M message, Predicate<Whisperer> seen, String description, boolean add) {
			this.message = message;
			this.seen = seen;
			this.description = description;
			this.add = add;
		}
	}

	@Override
	public void whisper(WhisperMessage<?> message, Predicate<Whisperer> seen, String description) {
		if (!seen.test(this))
			if (message instanceof WhisperPeerMessage wpm && peersAlreadyWhispered.add(wpm))
				whisperedPeersQueue.offer(new WhisperedInfo<>(wpm, seen, description, true));
			else if (message instanceof WhisperBlockMessage wbm && alreadyWhispered.add(message.getWhispered()))
				whisperedBlocksQueue.offer(new WhisperedInfo<>(wbm, seen, description, true));
			else if (message instanceof WhisperTransactionMessage wtm && alreadyWhispered.add(message.getWhispered()))
				whisperedTransactionsQueue.offer(new WhisperedInfo<>(wtm, seen, description, true));
	}

	@Override
	public Optional<Block> getBlock(byte[] hash) throws ClosedNodeException {
		try (var scope = mkScope()) {
			return blockchain.getBlock(hash);
		}
		catch (ClosedDatabaseException e) {
			// the database is encapsulated in this implementation and closed
			// only when also the node gets closed
			throw new ClosedNodeException(e);
		}
	}

	@Override
	public Optional<BlockDescription> getBlockDescription(byte[] hash) throws ClosedNodeException {
		try (var scope = mkScope()) {
			return blockchain.getBlockDescription(hash);
		}
		catch (ClosedDatabaseException e) {
			throw new ClosedNodeException(e);
		}
	}

	@Override
	public Stream<PeerInfo> getPeerInfos() throws ClosedNodeException {
		try (var scope = mkScope()) {
			return peers.get();
		}
	}

	@Override
	public Stream<MinerInfo> getMinerInfos() throws ClosedNodeException {
		try (var scope = mkScope()) {
			return miners.getInfos();
		}
	}

	@Override
	public Stream<TaskInfo> getTaskInfos() throws TimeoutException, InterruptedException, ClosedNodeException {
		try (var scope = mkScope()) {
			return currentlyExecutingTasks.stream()
				.map(Object::toString)
				.map(TaskInfos::of);
		}
	}

	@Override
	public NodeInfo getInfo() throws ClosedNodeException {
		try (var scope = mkScope()) {
			return peers.getNodeInfo();
		}
	}

	@Override
	public LocalNodeConfig getConfig() throws ClosedNodeException {
		try (var scope = mkScope()) {
			return config;
		}
	}

	@Override
	public ChainInfo getChainInfo() throws ClosedNodeException {
		try (var scope = mkScope()) {
			try {
				return blockchain.getChainInfo();
			}
			catch (ClosedDatabaseException e) {
				throw new ClosedNodeException(e);
			}
		}
	}

	@Override
	public ChainPortion getChainPortion(long start, int count) throws ClosedNodeException, PortionRejectedException {
		try (var scope = mkScope()) {
			try {
				return ChainPortions.of(blockchain.getChain(start, count));
			}
			catch (ClosedDatabaseException e) {
				throw new ClosedNodeException(e);
			}
		}
	}

	@Override
	public MempoolEntry add(Transaction transaction) throws TransactionRejectedException, ClosedNodeException, ApplicationTimeoutException, InterruptedException {
		TransactionEntry result;

		try {
			try (var scope = mkScope()) {
				result = mempool.add(transaction);
			}

			miningTask.add(result);
		}
		catch (ClosedDatabaseException | ClosedApplicationException e) {
			throw new ClosedNodeException(e);
		}

		whisperWithoutAddition(transaction);

		return result.toMempoolEntry();
	}

	@Override
	public MempoolInfo getMempoolInfo() throws ClosedNodeException {
		try (var scope = mkScope()) {
			return mempool.getInfo();
		}
	}

	@Override
	public MempoolPortion getMempoolPortion(int start, int count) throws ClosedNodeException, PortionRejectedException {
		try (var scope = mkScope()) {
			return mempool.getPortion(start, count);
		}
	}

	@Override
	public Optional<Transaction> getTransaction(byte[] hash) throws ClosedNodeException {
		try (var scope = mkScope()) {
			try {
				return blockchain.getTransaction(hash);
			}
			catch (ClosedDatabaseException e) {
				throw new ClosedNodeException(e);
			}
		}
	}

	@Override
	public Optional<String> getTransactionRepresentation(byte[] hash) throws TransactionRejectedException, ClosedNodeException, TimeoutException, InterruptedException {
		try (var scope = mkScope()) {
			Optional<Transaction> maybeTransaction = blockchain.getTransaction(hash);
			if (maybeTransaction.isEmpty())
				return Optional.empty();
			else
				return Optional.of(app.getRepresentation(maybeTransaction.get()));
		}
		catch (ClosedDatabaseException | ClosedApplicationException e) {
			// the database is encapsulated inside this node: if it is closed, then it is because close() has
			// been called on this node; moreover, if the application has been closed then this node is meant
			// to be closed as well, through the on close handler added to the application in the constructor of this node
			throw new ClosedNodeException(e);
		}
	}

	@Override
	public Optional<TransactionAddress> getTransactionAddress(byte[] hash) throws ClosedNodeException {
		try (var scope = mkScope()) {
			try {
				return blockchain.getTransactionAddress(hash);
			}
			catch (ClosedDatabaseException e) {
				throw new ClosedNodeException(e);
			}
		}
	}

	@Override
	public Optional<PeerInfo> add(Peer peer) throws TimeoutException, InterruptedException, ClosedNodeException, ClosedPeerException, PeerRejectedException {
		Optional<PeerInfo> result;
	
		try (var scope = mkScope()) {
			result = peers.add(peer);
		}
		catch (ClosedDatabaseException e) {
			throw new ClosedNodeException(e);
		}
	
		if (result.isPresent()) {
			scheduleSynchronizationIfPossible();
			whisperAllServices();
			whisperWithoutAddition(peer);
		}
	
		return result;
	}

	@Override
	public boolean remove(Peer peer) throws ClosedNodeException {
		try (var scope = mkScope()) {
			try {
				return peers.remove(peer);
			}
			catch (ClosedDatabaseException e) {
				throw new ClosedNodeException(e);
			}
		}
	}

	@Override
	public Optional<MinerInfo> openMiner(int port) throws FailedDeploymentException, ClosedNodeException {
		try (var scope = mkScope()) {
			var miner = RemoteMiners.open(port, miningSpecification, this::getBalance, this::checkForMiners);
			Optional<MinerInfo> maybeInfo = miners.add(miner);
			if (maybeInfo.isPresent())
				minersToCloseAtTheEnd.add(miner);
			else
				miner.close();
			
			return maybeInfo;
		}
	}

	@Override
	public Optional<MinerInfo> add(Miner miner) throws ClosedNodeException {
		try (var scope = mkScope()) {
			return miners.add(miner);
		}
	}

	@Override
	public boolean removeMiner(UUID uuid) throws ClosedNodeException {
		try (var scope = mkScope()) {
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
	protected PeersSet getPeers() {
		return peers;
	}

	/**
	 * Yields the miners of this node.
	 * 
	 * @return the miners
	 */
	protected MinersSet getMiners() {
		return miners;
	}

	/**
	 * Yields the blockchain of this node.
	 * 
	 * @return the blockchain
	 */
	protected Blockchain getBlockchain() {
		return blockchain;
	}

	/**
	 * Yields the key pair of this node. It is used to sign the blocks mined by this node.
	 * 
	 * @return the key pair
	 */
	protected KeyPair getKeys() {
		return keyPair;
	}

	protected LocalNodeConfig getConfigInternal() {
		return config;
	}

	protected void remove(TransactionEntry transactionEntry) {
		mempool.remove(transactionEntry);
	}

	/**
	 * Punishes a miner, by reducing its points. If the miner reaches zero points,
	 * it gets removed from the set of miners of this node. If the miner was not present in this
	 * node, nothing happens.
	 * 
	 * @param miner the miner to punish
	 * @param points how many points get removed
	 * @param reason the reason why the miner got punished
	 */
	protected void punish(Miner miner, long points, String reason) {
		if (points > 0) {
			LOGGER.warning("punishing miner " + miner.getUUID() + " by removing " + points + " points since " + reason);

			if (miners.punish(miner, points) && minersToCloseAtTheEnd.contains(miner))
				miner.close();
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
		 * @throws InterruptedException if the task has been interrupted
		 * @throws ClosedDatabaseException if the database has been closed
		 * @throws ClosedNodeException if the node has been closed
		 * @throws ClosedApplicationException if the application has been closed
		 */
		void body() throws InterruptedException, ClosedDatabaseException, ClosedNodeException, ClosedApplicationException;
	}

	private Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey publicKey) throws InterruptedException {
		try {
			return app.getBalance(signature, publicKey);
		}
		catch (ClosedApplicationException e) {
			LOGGER.warning("could not determine the balance of a public key since the application is already closed");
			return Optional.empty();
		}
		catch (TimeoutException e) {
			LOGGER.warning("could not determine the balance of a public key since the balance provider timed out");
			return Optional.empty();
		}
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
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws ApplicationTimeoutException if the application does not answer in time
	 */
	protected void check(Deadline deadline) throws IllegalDeadlineException, ApplicationTimeoutException, InterruptedException, ClosedApplicationException {
		var prolog = deadline.getProlog();
	
		if (!deadline.isValid())
			throw new IllegalDeadlineException("Invalid deadline");

		if (!prolog.getChainId().equals(config.getChainId()))
			throw new IllegalDeadlineException("Wrong chain identifier in deadline");
		else if (!prolog.getPublicKeyForSigningBlocks().equals(keyPair.getPublic()))
			throw new IllegalDeadlineException("Wrong node key in deadline");
		else if (!prolog.getSignatureForBlocks().equals(config.getSignatureForBlocks()))
			throw new IllegalDeadlineException("Wrong blocks' signature algorithm in deadline");
		else if (!prolog.getSignatureForDeadlines().equals(config.getSignatureForDeadlines()))
			throw new IllegalDeadlineException("Wrong deadlines' signature algorithm in deadline");
		else {
			try {
				if (!app.checkDeadline(deadline))
					throw new IllegalDeadlineException("The application rejected the deadline");
			}
			catch (TimeoutException e) {
				throw new ApplicationTimeoutException(e);
			}
		}
	}

	private void checkForMiners(Deadline deadline) throws IllegalDeadlineException, DeadlineValidityCheckException, InterruptedException {
		try {
			check(deadline);
		}
		catch (ClosedApplicationException | ApplicationTimeoutException e) {
			throw new DeadlineValidityCheckException(e);
		}
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
	 * @throws InterruptedException if the current thread gets interrupted while performing the operation
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 * @throws MisbehavingApplicationException if the application is misbehaving
	 * @throws ClosedApplicationException if the application is already closed
	 */
	protected void rebaseMempoolAt(Block block) throws InterruptedException, ApplicationTimeoutException, ClosedApplicationException, MisbehavingApplicationException {
		mempool.rebaseAt(block);
	}

	/**
	 * Performs an action for each transaction from the mempool, rebased at the given {@code block} (see
	 * {@link #rebaseMempoolAt(Block)}. The mempool of this node is not modified.
	 * 
	 * @param block the block
	 * @param action the action
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 * @throws MisbehavingApplicationException if the application is misbehaving
	 * @throws ClosedApplicationException if the application is already closed
	 */
	protected void forEachMempoolTransactionAt(Block block, Consumer<TransactionEntry> action) throws InterruptedException, ApplicationTimeoutException, ClosedApplicationException, MisbehavingApplicationException {
		var result = new Mempool(mempool); // clone the mempool
		result.rebaseAt(block); // rebase the clone
		result.forEachTransaction(action); // process the resulting transactions
	}

	/**
	 * Schedules a synchronization of the blockchain in this node, from the peers of the node,
	 * if the node is not currently performing a synchronization. Otherwise, nothing happens.
	 * This method does nothing if the synchronization task is rejected.
	 */
	private void scheduleSynchronizationIfPossible() {
		try {
			schedule(this::synchronize, () -> "synchronization from the peers");
		}
		catch (TaskRejectedExecutionException e) {
			LOGGER.warning("node " + uuid + ": synchronization request rejected, probably because the node is shutting down");
		}
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
	 * Yields the exception that explains why the last synchronization failed.
	 * 
	 * @return the exception, if any; this is empty if the last synchronization succeeded
	 */
	protected Optional<SynchronizationException> getLastSynchronizationException() {
		return Optional.ofNullable(synchronizationException);
	}

	/**
	 * Whispers a peer, but does not add it to this node.
	 * 
	 * @param peer the peer to whisper
	 */
	private void whisperWithoutAddition(Peer peer) {
		var whisperPeerMessage = WhisperPeerMessages.of(peer, UUID.randomUUID().toString());

		if (peersAlreadyWhispered.add(whisperPeerMessage))
			whisperedPeersQueue.offer(new WhisperedInfo<>(whisperPeerMessage, isThis, "peer " + peer, false));
	}

	/**
	 * Whispers a block, but does not add it to this node.
	 * 
	 * @param block the block to whisper
	 */
	protected void whisperWithoutAddition(Block block) {
		if (alreadyWhispered.add(block)) {
			var whisperBlockMessage = WhisperBlockMessages.of(block, UUID.randomUUID().toString());
			whisperedBlocksQueue.offer(new WhisperedInfo<>(whisperBlockMessage, isThis, "block " + block.getHexHash(), false));
		}
	}

	/**
	 * Whispers a transaction, but does not add it to this node.
	 * 
	 * @param transaction the transaction to whisper
	 */
	private void whisperWithoutAddition(Transaction transaction) {
		if (alreadyWhispered.add(transaction)) {
			var whisperTransactionMessage = WhisperTransactionMessages.of(transaction, UUID.randomUUID().toString());
			whisperedTransactionsQueue.offer(new WhisperedInfo<>(whisperTransactionMessage, isThis, "transaction " + transaction.getHexHash(config.getHashingForTransactions()), false));
		}
	}

	/**
	 * Called when a peer has been added.
	 * 
	 * @param peer the added peer
	 */
	protected void onAdded(Peer peer) {
		LOGGER.info("added peer " + peer);
	}

	/**
	 * Called when a peer gets connected.
	 * 
	 * @param peer the peer
	 */
	protected void onConnected(Peer peer) {
		LOGGER.info("connected to peer " + peer);
	}

	/**
	 * Called when a peer gets disconnected.
	 * 
	 * @param peer the peer
	 */
	protected void onDisconnected(Peer peer) {
		LOGGER.info("disconnected from peer " + peer);
	}

	/**
	 * Called when a peer has been removed.
	 * 
	 * @param peer the removed peer
	 */
	protected void onRemoved(Peer peer) {
		LOGGER.info("removed peer " + peer);
	}

	/**
	 * Called when a miner has been added.
	 * 
	 * @param miner the added miner
	 */
	protected void onAdded(Miner miner) {
		LOGGER.info("added miner " + miner.getUUID() + " (" + miner + ")");
		miningTask.continueIfSuspended();
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
		miningTask.continueIfSuspended();
	}

	/**
	 * Called when a block gets added to the blockchain, not necessarily to the current best chain.
	 * 
	 * @param block the added block
	 */
	protected void onAdded(Block block) {
		miningTask.continueIfSuspended();
	}

	/**
	 * Called when the head of the blockchain has been updated.
	 * 
	 * @param pathToNewHead the path of blocks added to the blockchain and leading to the new head;
	 *                      the last element of this list is the new head of the blockchain; very often, this list
	 *                      contains only one element: the head; however, there might be history changes, in which
	 *                      case the list is longer than a single element; in any case, this list is never empty
	 */
	protected void onHeadChanged(Deque<Block> pathToNewHead) {
		// it is not essential to publish the blocks immediately, therefore
		// we use a queue, in order to avoid delaying this hook
		pathToNewHead.forEach(block -> {
			if (!blocksToPublishQueue.offer(block))
				LOGGER.warning("cannot shcedule the publication of block " + block.getHexHash() + " since the publishing queue is full");
		});

		miningTask.restartFromCurrentHead();
	}

	/**
	 * Called when the node mines a new block.
	 * 
	 * @param block the mined block
	 */
	protected void onMined(NonGenesisBlock block) {}

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
		private final Supplier<String> description;
	
		private RunnableTask(Task task, Supplier<String> description) {
			this.task = task;
			this.description = description;
		}
	
		@Override
		public final void run() {
			currentlyExecutingTasks.add(this);
	
			try {
				task.body();
			}
			catch (ClosedDatabaseException e) {
				LOGGER.warning("node " + uuid + ": " + this + " exits since the database has been closed: " + e.getMessage());
			}
			catch (ClosedNodeException e) {
				LOGGER.warning("node " + uuid + ": " + this + " exits since the node has been closed: " + e.getMessage());
			}
			catch (ClosedApplicationException e) {
				LOGGER.warning("node " + uuid + ": " + this + " exits since the application has been closed: " + e.getMessage());
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				LOGGER.warning("node " + uuid + ": " + this + " exits since the node is shutting down");
			}
			catch (RuntimeException e) {
				LOGGER.log(Level.SEVERE, "node " + uuid + ": " + this + " threw an exception", e);
			}
			finally {
				currentlyExecutingTasks.remove(this);
			}
		}
	
		@Override
		public String toString() {
			return description.get();
		}
	}

	/**
	 * Runs the given task, asynchronously, in one thread from the {@link #executors} executor.
	 * 
	 * @param task the task to run
	 * @param description the description of the task
	 * @throws TaskRejectedExecutionException if the task could not be spawned
	 */
	private void schedule(Task task, Supplier<String> description) throws TaskRejectedExecutionException {
		var runnable = new RunnableTask(task, description);

		try {
			executors.execute(runnable);
			LOGGER.info("node " + uuid + ": " + runnable + " scheduled");
		}
		catch (RejectedExecutionException e) {
			throw new TaskRejectedExecutionException(e);
		}
	}

	Future<?> submit(Task task, Supplier<String> description) throws TaskRejectedExecutionException {
		var runnable = new RunnableTask(task, description);

		try {
			var future = executors.submit(runnable);
			LOGGER.info("node " + uuid + ": " + runnable + " scheduled");
			return future;
		}
		catch (RejectedExecutionException e) {
			LOGGER.warning("node " + uuid + ": " + runnable + " rejected, probably because the node is shutting down");
			throw new TaskRejectedExecutionException(e);
		}
	}

	/**
	 * Runs the given task, periodically, with the {@link #periodicExecutors} executor.
	 * 
	 * @param task the task to run
	 * @param description the description of the task
	 * @param initialDelay the time to wait before running the task
	 * @param delay the time interval between successive, iterated executions
	 * @param unit the time interval unit
	 * @throws TaskRejectedExecutionException if the task could not be spawned
	 */
	private void scheduleWithFixedDelay(Task task, Supplier<String> description, long initialDelay, long delay, TimeUnit unit) throws TaskRejectedExecutionException {
		var runnable = new RunnableTask(task, description);
	
		try {
			periodicExecutors.scheduleWithFixedDelay(runnable, initialDelay, delay, unit);
			LOGGER.info("node " + uuid + ": " + runnable + " scheduled every " + delay + " " + unit);
		}
		catch (RejectedExecutionException e) {
			throw new TaskRejectedExecutionException(e);
		}
	}

	/**
	 * Schedules a periodic task that whispers the services open on this node.
	 */
	private void schedulePeriodicWhisperingOfAllServices() throws TaskRejectedExecutionException {
		long serviceBroadcastInterval = config.getServiceBrodcastInterval();
		if (serviceBroadcastInterval >= 0)
			scheduleWithFixedDelay(this::whisperAllServices, () -> "whispering of all node's services", 0L, serviceBroadcastInterval, TimeUnit.MILLISECONDS);
	}

	/**
	 * Schedules a periodic task that identifies where the non-frozen part of the blockchain starts
	 * and informs the application that states in the frozen part are eligible for garbage-collection.
	 */
	private void schedulePeriodicIdentificationOfTheNonFrozenPartOfBlockchain() throws TaskRejectedExecutionException {
		scheduleWithFixedDelay(this::identifyNonFrozenPartOfBlockchain, () -> "identification of the non-frozen part of the blockchain", 10000L, 10000L, TimeUnit.MILLISECONDS);
	}

	/**
	 * Schedules a periodic task that synchronizes the blockchain with its peers.
	 */
	private void schedulePeriodicSynchronization() throws TaskRejectedExecutionException {
		var interval = config.getSynchronizationInterval();
		if (interval >= 0)
			scheduleWithFixedDelay(this::synchronize, () -> "synchronization from the peers", 0L, interval, TimeUnit.MILLISECONDS);
	}

	private void synchronize() throws InterruptedException {
		if (isSynchronizing.getAndSet(true) == false) { // we avoid to synchronize if synchronization is already in process
			try {
				blockchain.synchronize();
				synchronizationException = null; // yes, we did it!
			}
			catch (ClosedNodeException e) {
				LOGGER.warning("sync: stop synchronizing since the node has been closed: " + e.getMessage());
			}
			catch (ClosedDatabaseException e) {
				LOGGER.warning("sync: stop synchronizing since the database has been closed: " + e.getMessage());
			}
			catch (SynchronizationException e) {
				synchronizationException = e;
				LOGGER.warning("sync: synchronization could not be completed: " + e.getMessage());
			}
		}
	}

	/**
	 * Schedules a periodic task that pings all peers, reconnects to them and their peers.
	 */
	private void schedulePeriodicPingToAllPeersAndReconnection() throws TaskRejectedExecutionException {
		var interval = config.getPeerPingInterval();
		if (interval >= 0)
			scheduleWithFixedDelay(this::pingAllPeersAndReconnect,
				() -> "ping of all peers to check connection and collect their peers", 0L, interval, TimeUnit.MILLISECONDS);
	}

	private void pingAllPeersAndReconnect() throws InterruptedException {
		try {
			if (peers.pingAllAndReconnect())
				scheduleSynchronizationIfPossible();
		}
		catch (ClosedNodeException e) {
			LOGGER.warning("ping all peers exits since the node has been closed");
		}
		catch (ClosedDatabaseException e) {
			LOGGER.warning("ping all peers exits since the database has been closed");
		}
	}

	/**
	 * Processes the whispered objects received by this node, until interrupted.
	 */
	private void processWhisperedPeers() throws InterruptedException, ClosedNodeException, ClosedDatabaseException {
		while (!Thread.currentThread().isInterrupted()) {
			var whisperedInfo = whisperedPeersQueue.take();
			var whisperedPeerMessage = whisperedInfo.message;
			var peer = whisperedPeerMessage.getWhispered();

			if (whisperedInfo.add) {
				try {
					if (peers.add(peer).isPresent())
						scheduleSynchronizationIfPossible();
				}
				catch (PeerTimeoutException | ClosedPeerException e) {
					LOGGER.warning("node " + uuid + ": whispered " + whisperedInfo.description + " could not be added because it is misbehaving: " + e.getMessage());
				}
				catch (PeerRejectedException e) {
					LOGGER.warning("node " + uuid + ": whispered " + whisperedInfo.description + " has been rejected: " + e.getMessage());
				}
			}

			Predicate<Whisperer> newSeen = whisperedInfo.seen.or(isThis);
			peers.whisper(whisperedPeerMessage, newSeen, whisperedInfo.description);
			boundWhisperers.forEach(whisperer -> whisperer.whisper(whisperedPeerMessage, newSeen, whisperedInfo.description));
			onWhispered(peer);
		}
	}

	/**
	 * Processes the whispered objects received by this node, until interrupted.
	 */
	private void processWhisperedBlocks() throws InterruptedException, ClosedDatabaseException {
		while (!Thread.currentThread().isInterrupted()) {
			var whisperedInfo = whisperedBlocksQueue.take();

			try {
				var whisperedBlockMessage = whisperedInfo.message;
				var block = whisperedBlockMessage.getWhispered();

				if (whisperedInfo.add) {
					try {
						blockchain.add(block);
					}
					catch (ApplicationTimeoutException | ClosedApplicationException | MisbehavingApplicationException e) {
						LOGGER.warning("node " + uuid + ": whispered " + whisperedInfo.description + " could not be added because of a problem with the application: " + e.getMessage());
					}
				}

				Predicate<Whisperer> newSeen = whisperedInfo.seen.or(isThis);
				peers.whisper(whisperedBlockMessage, newSeen, whisperedInfo.description);
				boundWhisperers.forEach(whisperer -> whisperer.whisper(whisperedBlockMessage, newSeen, whisperedInfo.description));
				onWhispered(block);
			}
			catch (VerificationException e) {
				// if verification fails, we do not pass the block to our peers
				LOGGER.warning("node " + uuid + ": whispered " + whisperedInfo.description + " did not pass verification: " + e.getMessage());
			}
		}
	}
	
	/**
	 * Processes the whispered objects received by this node, until interrupted.
	 */
	private void processWhisperedTransactions() throws InterruptedException, ClosedDatabaseException {
		while (!Thread.currentThread().isInterrupted()) {
			var whisperedInfo = whisperedTransactionsQueue.take();
			var whisperedTransactionMessage = whisperedInfo.message;
			var tx = whisperedTransactionMessage.getWhispered();

			if (whisperedInfo.add) {
				try {
					mempool.add(tx);
				}
				catch (ApplicationTimeoutException | ClosedApplicationException e) {
					LOGGER.warning("node " + uuid + ": whispered " + whisperedInfo.description + " could not be added because the application is not responding: " + e.getMessage());
				}
				catch (TransactionRejectedException e) {
					LOGGER.warning("node " + uuid + ": whispered " + whisperedInfo.description + " has been rejected: " + e.getMessage());
				}
			}

			Predicate<Whisperer> newSeen = whisperedInfo.seen.or(isThis);
			peers.whisper(whisperedTransactionMessage, newSeen, whisperedInfo.description);
			boundWhisperers.forEach(whisperer -> whisperer.whisper(whisperedTransactionMessage, newSeen, whisperedInfo.description));
			onWhispered(tx);
		}
	}

	private void publisher() throws InterruptedException, ClosedApplicationException {
		while (true) {
			Block block = blocksToPublishQueue.take();

			try {
				app.publish(block);
			}
			catch (TimeoutException e) {
				LOGGER.warning("cannot publish block " + block.getHexHash() + " since the application is unresponsive");
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
			.map(io.mokamint.node.Peers::of)
			.forEach(this::whisperWithoutAddition);
	}

	private void identifyNonFrozenPartOfBlockchain() throws InterruptedException, ClosedApplicationException, ClosedDatabaseException {
		try {
			Optional<LocalDateTime> maybeStartTimeOfNonFrozenPart = blockchain.getStartingTimeOfNonFrozenHistory();
			if (maybeStartTimeOfNonFrozenPart.isPresent())
				app.keepFrom(maybeStartTimeOfNonFrozenPart.get());
		}
		catch (TimeoutException e) {
			LOGGER.log(Level.WARNING, "cannot identify the non-frozen part of the blockchain because the application is unresponsive: " + e.getMessage());
		}
	}

	private void closeExecutorsHandlersMinersPeersAndBlockchain() {
		try {
			executors.shutdownNow();
		}
		finally {
			try {
				periodicExecutors.shutdownNow();
			}
			finally {
				closeHandlersMinersPeersAndBlockchain();
			}
		}
	}

	private void closeHandlersMinersPeersAndBlockchain() {
		try {
			callCloseHandlers();
		}
		finally {
			closeMinersPeersAndBlockchain(minersToCloseAtTheEnd.toArray(Miner[]::new), 0);
		}
	}

	private void closeMinersPeersAndBlockchain(Miner[] miners, int pos) {
		if (pos < miners.length) {
			try {
				miners[pos].close();
			}
			finally {
				closeMinersPeersAndBlockchain(miners, pos + 1);
			}
		}
		else
			closePeersAndBlockchain();
	}

	private void closePeersAndBlockchain() {
		try {
			peers.close();
		}
		finally {
			blockchain.close();
		}
	}
}
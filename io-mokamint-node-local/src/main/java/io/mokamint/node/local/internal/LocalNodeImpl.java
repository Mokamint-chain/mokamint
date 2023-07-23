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
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.OnThread;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.Hex;
import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.Versions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNode;
import io.mokamint.node.local.internal.tasks.AddPeersTask;
import io.mokamint.node.local.internal.tasks.DelayedMineNewBlockTask;
import io.mokamint.node.local.internal.tasks.MineNewBlockTask;
import io.mokamint.node.messages.WhisperPeersMessages;
import io.mokamint.node.messages.api.Whisperer;

/**
 * A local node of a Mokamint blockchain.
 */
@ThreadSafe
public class LocalNodeImpl implements LocalNode {

	/**
	 * The configuration of the node.
	 */
	private final Config config;

	/**
	 * The application executed by the blockchain.
	 */
	private final Application app;

	/**
	 * The miners connected to the node.
	 */
	private final NodeMiners miners;

	/**
	 * The peers of the node.
	 */
	private final NodePeers peers;

	/**
	 * The database containing the blockchain.
	 */
	private final Database db;

	/**
	 * The non-consensus information about this node.
	 */
	private final NodeInfo info;

	/**
	 * The time of creation of the genesis block.
	 */
	private final LocalDateTime startDateTime;

	/**
	 * The single executor of the events. Events get queued into this queue and run in order
	 * on that executor, called the event thread.
	 */
	private final ExecutorService events = Executors.newSingleThreadExecutor();

	/**
	 * The executors of tasks. There might be more tasks in execution at the same time.
	 */
	private final ExecutorService tasks = Executors.newCachedThreadPool();

	/**
	 * The code to execute when this node gets closed.
	 */
	private final CopyOnWriteArrayList<CloseHandler> onCloseHandlers = new CopyOnWriteArrayList<>();

	/**
	 * The code to execute when this node has some peers to whisper to the services using this node.
	 */
	private final CopyOnWriteArrayList<Consumer<Stream<Peer>>> onWhisperPeersToServicesHandlers = new CopyOnWriteArrayList<>();

	/**
	 * The whisperers bound to this node.
	 */
	private final CopyOnWriteArrayList<Whisperer> boundWhisperers = new CopyOnWriteArrayList<>();

	/**
	 * True if and only if this node has been closed already.
	 */
	private final AtomicBoolean isClosed = new AtomicBoolean();

	/**
	 * A buffer where blocks without a known previous are parked, in case
	 * their previous block arrives later.
	 */
	@GuardedBy("itself")
	private final NonGenesisBlock[] orphans = new NonGenesisBlock[20];

	/**
	 * The next insertion position inside the {@link #orphans} array.
	 */
	@GuardedBy("orphans")
	private int orphansPos;

	private final static Logger LOGGER = Logger.getLogger(LocalNodeImpl.class.getName());

	/**
	 * Creates a local node of a Mokamint blockchain, for the given application,
	 * using the given miners.
	 * 
	 * @param config the configuration of the node
	 * @param app the application
	 * @param miners the miners
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 * @throws IOException if the version information cannot be read
	 * @throws DatabaseException if the database is corrupted
	 */
	public LocalNodeImpl(Config config, Application app, Miner... miners) throws NoSuchAlgorithmException, DatabaseException, IOException {
		this.config = config;
		this.app = app;
		this.db = new Database(config);
		this.info = NodeInfos.of(Versions.current(), db.getUUID());
		this.miners = new NodeMiners(this, Stream.of(miners));
		this.peers = new NodePeers(this, db, peers -> executeAddPeersTask(peers, false, true));
		addSeedsAsPeers();
		this.startDateTime = db.getGenesis().map(GenesisBlock::getStartDateTimeUTC).orElse(LocalDateTime.now(ZoneId.of("UTC")));
		startNextBlockMining();
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
	public void addOnWhisperPeersToServicesHandler(Consumer<Stream<Peer>> handler) {
		onWhisperPeersToServicesHandlers.add(handler);
	}

	@Override
	public void removeOnWhisperPeersToServicesHandler(Consumer<Stream<Peer>> handler) {
		onWhisperPeersToServicesHandlers.remove(handler);
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
	public void whisperToPeers(Stream<Peer> peers) {
		// we check if some peers is interesting for us
		// and only in that case we forward it to our peers
		executeAddPeersTask(peers, false, false);
	}

	@Override
	public void whisperToServices(Stream<Peer> peers) {
		var peersAsArray = peers.toArray(Peer[]::new);
		onWhisperPeersToServicesHandlers.stream().forEach(handler -> handler.accept(Stream.of(peersAsArray)));
	}

	@Override
	public void whisperItselfToPeers(Peer itself) {
		LocalNodeImpl.this.peers.get()
			.filter(PeerInfo::isConnected)
			.map(PeerInfo::getPeer)
			.map(LocalNodeImpl.this.peers::getRemote)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.forEach(remote -> remote.whisperToPeers(Stream.of(itself)));
	}

	private void ensureIsOpen() throws ClosedNodeException {
		if (isClosed.get())
			throw new ClosedNodeException("the node has been closed");
	}

	@Override
	public Optional<Block> getBlock(byte[] hash) throws DatabaseException, NoSuchAlgorithmException, ClosedNodeException {
		ensureIsOpen();
		return db.getBlock(hash);
	}

	@Override
	public Stream<PeerInfo> getPeerInfos() {
		return peers.get();
	}

	@Override
	public void addPeer(Peer peer) throws TimeoutException, InterruptedException, ClosedNodeException, IOException, IncompatiblePeerException, DatabaseException {
		ensureIsOpen();
		if (peers.add(peer, true))
			submit(new PeersAddedEvent(Stream.of(peer), true));
	}

	@Override
	public void removePeer(Peer peer) throws DatabaseException, ClosedNodeException {
		ensureIsOpen();
		peers.remove(peer);
	}

	@Override
	public void close() throws InterruptedException, DatabaseException, IOException {
		if (!isClosed.getAndSet(true)) {
			events.shutdownNow();
			tasks.shutdownNow();

			InterruptedException interruptedException = null;
			
			for (var handler: onCloseHandlers) {
				try {
					handler.close();
				}
				catch (InterruptedException e) {
					interruptedException = e;
				}
			}

			try {
				events.awaitTermination(10, TimeUnit.SECONDS);
				tasks.awaitTermination(10, TimeUnit.SECONDS);
			}
			finally {
				try {
					db.close();
				}
				finally {
					peers.close();
				}
			}

			if (interruptedException != null)
				throw interruptedException;
		}
	}

	@Override
	public NodeInfo getInfo() {
		return info;
	}

	/**
	 * Yields the configuration of this node.
	 * 
	 * @return the configuration of this node
	 */
	@Override
	public Config getConfig() {
		return config;
	}

	@Override
	public ChainInfo getChainInfo() throws NoSuchAlgorithmException, DatabaseException, ClosedNodeException {
		ensureIsOpen();
		var maybeHeadHash = db.getHeadHash();
		if (maybeHeadHash.isEmpty())
			return ChainInfos.of(0L, Optional.empty(), Optional.empty());
		else {
			var head = db.getBlock(maybeHeadHash.get()).orElseThrow(() -> new DatabaseException("the hash of the head is set but the head block is not in the database"));
			return ChainInfos.of(head.getHeight(), db.getGenesisHash(), maybeHeadHash);
		}
	}

	/**
	 * Adds the given block to the database of blocks of this node.
	 * If the block was already in the database, nothing happens.
	 * 
	 * @param block the block to add
	 * @param headChanged set to true if the head has been changed because of the addition
	 *                    of {@code block}; otherwise it is left unchanged
	 * @return true if the block has been actually added to the tree of blocks
	 *         rooted at the genesis block, false otherwise.
	 *         There are a few situations when the result can be false. For instance,
	 *         if {@code block} was already in the tree, or if {@code block} is
	 *         a genesis block but a genesis block is already present in the tree, or
	 *         if {@code block} has no previous block already in the tree (it is orphaned),
	 *         or if the block has a previous block in the tree but it cannot be
	 *         correctly verified as a legal child of that previous block
	 * @throws DatabaseException if the block cannot be added, because the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 */
	public boolean add(Block block, AtomicBoolean headChanged) throws DatabaseException, NoSuchAlgorithmException {
		boolean added = false, first = true;

		// we use a working set, since the addition of a single block might
		// trigger the further addition of orphan blocks, recursively
		var ws = new ArrayList<Block>();
		ws.add(block);

		do {
			Block cursor = ws.remove(ws.size() - 1);

			if (!db.contains(cursor)) { // optimization check, to avoid repeated verification
				if (cursor instanceof NonGenesisBlock ngb) {
					Optional<Block> previous = db.getBlock(ngb.getHashOfPreviousBlock());
					if (previous.isEmpty())
						putAmongOrphans(ngb);
					else {
						if (verify(ngb, previous.get()) && db.add(cursor, headChanged)) {
							getOrphansWithParent(cursor).forEach(ws::add);
							if (first)
								added = true;
						}
					}
				}
				else if (verify((GenesisBlock) block) && db.add(cursor, headChanged)) {
					getOrphansWithParent(cursor).forEach(ws::add);
					if (first)
						added = true;
				}
			}

			first = false;
		}
		while (!ws.isEmpty());

		return added;
	}

	/**
	 * Adds the given block to the database of blocks of this node.
	 * If the block was already in the database, nothing happens.
	 * 
	 * @param block the block to add
	 * @return true if the block has been actually added to the tree of blocks
	 *         rooted at the genesis block, false otherwise.
	 *         There are a few situations when the result can be false. For instance,
	 *         if {@code block} was already in the tree, or if {@code block} is
	 *         a genesis block but a genesis block is already present in the tree, or
	 *         if {@code block} has no previous block already in the tree (it is orphaned),
	 *         or if the block has a previous block in the tree but it cannot be
	 *         correctly verified as a legal child of that previous block
	 * @throws DatabaseException if the block cannot be added, because the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 */
	public boolean add(Block block) throws DatabaseException, NoSuchAlgorithmException {
		return add(block, new AtomicBoolean()); // the AtomicBoolean is not used
	}

	private boolean verify(GenesisBlock block) {
		return true;
	}

	private boolean verify(NonGenesisBlock block, Block previous) {
		return true;
	}

	/**
	 * Adds the given block to {@link #orphans}.
	 * 
	 * @param block the block to add
	 */
	private void putAmongOrphans(NonGenesisBlock block) {
		synchronized (orphans) {
			if (Stream.of(orphans).anyMatch(block::equals))
				// it is already inside the array: it is better not to waste a slot
				return;

			orphansPos = (orphansPos + 1) % orphans.length;
			orphans[orphansPos] = block;
		}
	}

	/**
	 * Yields the orphans having the given parent.
	 * 
	 * @param parent the parent
	 * @return the orphans whose previous block is {@code parent}, if any
	 */
	private Stream<NonGenesisBlock> getOrphansWithParent(Block parent) {
		byte[] hashOfParent = parent.getHash(config.getHashingForBlocks());

		synchronized (orphans) {
			return Stream.of(orphans)
					.filter(Objects::nonNull)
					.filter(orphan -> Arrays.equals(orphan.getHashOfPreviousBlock(), hashOfParent));
		}
	}

	/**
	 * The time of creation of the genesis block.
	 */
	public LocalDateTime getStartDateTime() {
		return startDateTime;
	}

	/**
	 * Yields the head block of the tree of blocks in the database, if it has been set already.
	 * 
	 * @return the head block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws DatabaseException if the database is corrupted
	 */
	public Optional<Block> getHead() throws NoSuchAlgorithmException, DatabaseException {
		return db.getHead();
	}

	/**
	 * Yields the application this node is running.
	 * 
	 * @return the application
	 */
	public Application getApplication() {
		return app;
	}

	/**
	 * Yields miners of this node.
	 * 
	 * @return the miners
	 */
	public Stream<Miner> getMiners() {
		return miners.get();
	}

	/**
	 * Schedules a task that will add the given peers to the node.
	 * 
	 * @param peers the peers to add
	 * @param force true if and only if the peers must be added also if the maximum number of peers
	 *              for the node has been reached
	 * @param whisper true if and only if the peers actually added, at the end, must be whispered
	 *                to all peers of this node
	 */
	private void executeAddPeersTask(Stream<Peer> peers, boolean force, boolean whisper) {
		var peersAsArray = peers.distinct().toArray(Peer[]::new);
		if (peersAsArray.length > 0)
			submit(new AddPeersTask(Stream.of(peersAsArray), peer -> this.peers.add(peer, force), this, whisper));
	}

	/**
	 * Adds the seeds as peers.
	 */
	private void addSeedsAsPeers() {
		executeAddPeersTask(config.seeds().map(Peers::of), true, true);
	}

	/**
	 * Starts the mining.
	 */
	private void startNextBlockMining() {
		submit(new MineNewBlockTask(this));
	}

	/**
	 * Partial implementation of events. It weaves the monitoring call-backs
	 * and delegates to its {@link Event#body()} method.
	 */
	protected abstract class Event implements Runnable {

		@Override @OnThread("events")
		public final void run() {
			onStart(this);

			try {
				body();
			}
			catch (Exception e) {
				onFail(this, e);
				return;
			}

			onComplete(this);
		}

		/**
		 * Main body of the event execution.
		 * 
		 * @throws Exception if the execution fails
		 */
		@OnThread("events")
		protected abstract void body() throws Exception;
	}

	/**
	 * Determines if the given event is disabled, that is, if emitted
	 * it won't be executed. By default, this method yields false,
	 * but subclasses might redefine (typically, for testing).
	 * 
	 * @param event the event
	 * @return true if and only if the event is disabled
	 */
	protected boolean isDisabled(Event event) {
		return false;
	}

	/**
	 * Callback called when an event is submitted.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param event the event
	 */
	protected void onSubmit(Event event) {}

	/**
	 * Callback called when an event begins being executed.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param event the event
	 */
	protected void onStart(Event event) {}

	/**
	 * Callback called at the end of the successful execution of an event.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param event the event
	 */
	protected void onComplete(Event event) {}

	/**
	 * Callback called at the end of the failed execution of an event.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param event the event
	 * @param exception the failure cause
	 */
	protected void onFail(Event event, Exception e) {
		LOGGER.log(Level.SEVERE, "failed execution of " + event, e);
	}

	/**
	 * Signals that an event occurred. This is typically called from tasks,
	 * to signal that something occurred and that the node must react accordingly.
	 * Events get queued into the {@link #events} queue and eventually executed
	 * on its only thread, in order.
	 * 
	 * @param event the emitted event
	 */
	public final void submit(Event event) {
		try {
			LOGGER.info("received " + event);
			onSubmit(event);

			if (isDisabled(event))
				LOGGER.info("discarding disabled " + event);
			else
				events.execute(event);
		}
		catch (RejectedExecutionException e) {
			LOGGER.warning(event + " rejected, probably because the node is shutting down");
		}
	}

	/**
	 * A task is a complex activity that can be run in its own thread. Once it completes,
	 * it typically fires some events to signal something to the node.
	 */
	public abstract class Task implements Runnable {

		/**
		 * The node running this task.
		 */
		protected final LocalNodeImpl node = LocalNodeImpl.this;

		@Override @OnThread("tasks")
		public final void run() {
			onStart(this);

			try {
				body();
			}
			catch (Exception e) {
				onFail(this, e);
				return;
			}

			onComplete(this);
		}

		/**
		 * Main body of the task execution.
		 * 
		 * @throws Exception if the execution fails
		 */
		@OnThread("tasks")
		protected abstract void body() throws Exception;
	}

	/**
	 * Callback called when a task is submitted.
	 * It can be useful for testing or monitoring tasks.
	 * 
	 * @param task the task
	 */
	protected void onSubmit(Task task) {}

	/**
	 * Determines if the given task is disabled, that is, if scheduled
	 * it won't be executed. By default, this method yields false,
	 * but subclasses might redefine (typically, for testing).
	 * 
	 * @param task the task
	 * @return true if and only if the task is disabled
	 */
	protected boolean isDisabled(Task task) {
		return false;
	}

	/**
	 * Callback called when a task begins being executed.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param task the task
	 */
	protected void onStart(Task task) {}

	/**
	 * Callback called at the end of the successful execution of a task.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param task the task
	 */
	protected void onComplete(Task task) {}

	/**
	 * Callback called at the end of the failed execution of a task.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param task the task
	 * @param exception the failure cause
	 */
	protected void onFail(Task task, Exception e) {
		LOGGER.log(Level.SEVERE, "failed execution of " + task, e);
	}

	/**
	 * Runs the given task in one thread from the {@link #tasks} executors.
	 * 
	 * @param task the task to run
	 */
	private void submit(Task task) {
		try {
			LOGGER.info("scheduling " + task);
			onSubmit(task);

			if (isDisabled(task))
				LOGGER.info("discarding disabled " + task);
			else
				tasks.execute(task);
		}
		catch (RejectedExecutionException e) {
			LOGGER.warning(task + " rejected, probably because the node is shutting down");
		}
	}

	/**
	 * An event fired to signal that a block has been discovered. This might come
	 * from the node itself, if it finds a deadline and a new block by using its own miners,
	 * but also from a peer, that fund a block and whispers it to us.
	 */
	public class BlockDiscoveryEvent extends Event {
		public final Block block;

		public BlockDiscoveryEvent(Block block) {
			this.block = block;
		}

		@Override
		public String toString() {
			return "discovery event for block " + Hex.toHexString(block.getHash(config.getHashingForBlocks())) + " at height " + block.getHeight();
		}

		@Override @OnThread("events")
		protected void body() throws DatabaseException, NoSuchAlgorithmException {
			var headChanged = new AtomicBoolean(false);
			add(block, headChanged);
			if (headChanged.get())
				startNextBlockMining();
		}
	}

	/**
	 * An event fired to signal that some peers have been added to the node.
	 */
	public class PeersAddedEvent extends Event {
		private final Peer[] peers;
		private final boolean whisper;

		public PeersAddedEvent(Stream<Peer> peers, boolean whisper) {
			this.peers = peers.toArray(Peer[]::new);
			this.whisper = whisper;
		}

		@Override
		public String toString() {
			return "addition event for peers " + Arrays.toString(peers);
		}

		/**
		 * Yields the added peers.
		 * 
		 * @return the added peers
		 */
		public Stream<Peer> getPeers() {
			return Stream.of(peers);
		}

		@Override @OnThread("events")
		protected void body() {
			LocalNodeImpl.this.peers.get()
				.filter(PeerInfo::isConnected)
				.map(PeerInfo::getPeer)
				.map(LocalNodeImpl.this.peers::getRemote)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.forEach(remote -> remote.whisperToPeers(getPeers()));

			// also the nodes bound to the services using this node receive the whispering
			whisperToServices(getPeers());

			if (whisper)
				whisper(WhisperPeersMessages.of(Stream.of(peers), UUID.randomUUID().toString()), _whisperer -> false);
		}
	}

	/**
	 * An event fired to signal that a peer of the node disconnected.
	 */
	public class PeerDisconnectedEvent extends Event {
		private final Peer peer;

		public PeerDisconnectedEvent(Peer peer) {
			this.peer = peer;
		}

		@Override
		public String toString() {
			return "disconnection event for peer " + peer;
		}

		/**
		 * Yields the disconnected peer.
		 * 
		 * @return the disconnected peer
		 */
		public Peer getPeer() {
			return peer;
		}

		@Override @OnThread("events")
		protected void body() {
		}
	}

	/**
	 * An event fired to signal that a miner misbehaved.
	 */
	public class MinerMisbehaviorEvent extends Event {
		public final Miner miner;
		public final long points;

		/**
		 * Creates an event that expresses a miner's misbehavior.
		 * 
		 * @param miner the miner that misbehaved
		 * @param points how many points should be removed for this misbehavior
		 */
		public MinerMisbehaviorEvent(Miner miner, long points) {
			this.miner = miner;
			this.points = points;
		}

		@Override
		public String toString() {
			return "miner misbehavior event [-" + points + " points] for miner " + miner.toString();
		}

		@Override @OnThread("events")
		protected void body() {
			miners.punish(miner, points);
		}
	}

	/**
	 * An event fired to signal that the connection to a miner timed-out.
	 */
	public class IllegalDeadlineEvent extends MinerMisbehaviorEvent {

		public IllegalDeadlineEvent(Miner miner) {
			super(miner, config.minerPunishmentForIllegalDeadline);
		}

		@Override
		public String toString() {
			return "miner computed illegal deadline event [-" + points + " points] for miner " + miner.toString();
		}
	}

	/**
	 * An event fired when a new block task timed-out without finding a single deadline,
	 * despite having at least a miner available.
	 */
	public class NoDeadlineFoundEvent extends Event {

		@Override
		public String toString() {
			return "no deadline found event";
		}

		@Override @OnThread("events")
		protected void body() throws NoSuchAlgorithmException, DatabaseException {
			// all miners timed-out
			getMiners().forEach(miner -> miners.punish(miner, config.minerPunishmentForTimeout));

			submit(new DelayedMineNewBlockTask(LocalNodeImpl.this));
		}
	}

	/**
	 * An event fired when a new block task failed because there are no miners.
	 */
	public class NoMinersAvailableEvent extends Event {

		@Override
		public String toString() {
			return "no miners available event";
		}

		@Override @OnThread("events")
		protected void body() {}
	}
}
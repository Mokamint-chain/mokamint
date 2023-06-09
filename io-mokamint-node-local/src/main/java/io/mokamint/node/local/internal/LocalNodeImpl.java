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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.OnThread;
import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.ListenerManager;
import io.mokamint.node.ListenerManagers;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.Versions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNode;
import io.mokamint.node.local.internal.tasks.AddPeersTask;
import io.mokamint.node.local.internal.tasks.DelayedMineNewBlockTask;
import io.mokamint.node.local.internal.tasks.MineNewBlockTask;
import io.mokamint.node.local.internal.tasks.SuggestPeersTask;

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
	 * A service used to schedule periodic tasks.
	 */
	private final ScheduledExecutorService periodicTasks = Executors.newScheduledThreadPool(1);

	/**
	 * The listeners called whenever peers are added to this node.
	 */
	private final ListenerManager<Stream<Peer>> onPeersAddedListeners = ListenerManagers.mk();

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
		this.peers = new NodePeers(this);
		addSeedsAsPeers();
		periodicTasks.scheduleWithFixedDelay(peers::tryToCreateMissingRemotes, 0L, config.peerPingInterval, TimeUnit.MILLISECONDS);
		this.startDateTime = startMining();
	}

	@Override
	public void addOnPeersAddedListener(Consumer<Stream<Peer>> listener) {
		onPeersAddedListeners.addListener(listener);
	}

	@Override
	public void removeOnPeersAddedListener(Consumer<Stream<Peer>> listener) {
		onPeersAddedListeners.removeListener(listener);
	}

	@Override
	public Optional<Block> getBlock(byte[] hash) throws DatabaseException, NoSuchAlgorithmException {
		return db.get(hash);
	}

	@Override
	public Stream<PeerInfo> getPeers() {
		return peers.get();
	}

	@Override
	public void addPeer(Peer peer) throws TimeoutException, InterruptedException, IOException, IncompatiblePeerException, DatabaseException {
		if (peers.add(peer, true))
			emit(new PeersAddedEvent(Stream.of(peer)));
	}

	@Override
	public void removePeer(Peer peer) throws DatabaseException {
		peers.remove(peer);
	}

	@Override
	public void close() throws InterruptedException, DatabaseException, IOException {
		events.shutdownNow();
		tasks.shutdownNow();
		periodicTasks.shutdownNow();
		
		try {
			events.awaitTermination(2, TimeUnit.SECONDS);
			tasks.awaitTermination(2, TimeUnit.SECONDS);
			periodicTasks.awaitTermination(2, TimeUnit.SECONDS);
		}
		finally {
			try {
				db.close();
			}
			finally {
				peers.close();
			}
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
	public ChainInfo getChainInfo() throws NoSuchAlgorithmException, DatabaseException {
		var headHash = db.getHeadHash();
		if (headHash.isEmpty())
			return ChainInfos.of(0L, db.getGenesisHash(), Optional.empty()); // TODO: I should remove getGenesisHash
		else {
			var maybeHead = db.getHead();
			if (maybeHead.isEmpty())
				throw new DatabaseException("the hash of the head is set but the head block is not in the database");

			return ChainInfos.of(maybeHead.get().getHeight(), db.getGenesisHash(), headHash);
		}
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
	 * Yields the database of this node.
	 * 
	 * @return the database of this node
	 */
	Database getDatabase() {
		return db;
	}

	/**
	 * Schedules a task that will add the given peers to the node.
	 * 
	 * @param peers the peers to add
	 * @param force true if and only if the peers must be added also if the maximum number of peers
	 *              for the node has been reached
	 */
	void executeAddPeersTask(Stream<Peer> peers, boolean force) {
		var peersAsArray = peers.toArray(Peer[]::new);
		if (peersAsArray.length > 0)
			execute(new AddPeersTask(Stream.of(peersAsArray), peer -> this.peers.add(peer, force), this));
	}

	/**
	 * Adds the seeds as peers.
	 */
	private void addSeedsAsPeers() {
		executeAddPeersTask(config.seeds().map(Peers::of), true);
	}

	/**
	 * Starts the mining.
	 * 
	 * @return the start time of the blockchain
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 */
	private LocalDateTime startMining() throws NoSuchAlgorithmException, DatabaseException {
		var maybeHead = db.getHead();
		if (maybeHead.isPresent()) {
			var head = maybeHead.get();
			LocalDateTime startDateTime = db.getGenesis().get().getStartDateTimeUTC();
			LocalDateTime nextBlockStartTime = startDateTime.plus(head.getTotalWaitingTime(), ChronoUnit.MILLIS);
			execute(new MineNewBlockTask(this, head, nextBlockStartTime));
			return startDateTime;
		}
		else {
			LocalDateTime startDateTime = LocalDateTime.now(ZoneId.of("UTC"));
			emit(new BlockDiscoveryEvent(Blocks.genesis(startDateTime)));
			return startDateTime;
		}
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
	 * Callback called when an event is emitted.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param event the event
	 */
	protected void onEmit(Event event) {}

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
	public final void emit(Event event) {
		try {
			LOGGER.info("received " + event);
			onEmit(event);
			events.execute(event);
		}
		catch (RejectedExecutionException e) {
			LOGGER.info(event + " rejected, probably because the node is shutting down");
			onFail(event, e);
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
	 * Callback called when a task is scheduled.
	 * It can be useful for testing or monitoring tasks.
	 * 
	 * @param task the task
	 */
	protected void onSchedule(Task task) {}

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
	private void execute(Task task) {
		try {
			LOGGER.info("scheduling " + task);
			onSchedule(task);
			tasks.execute(task);
		}
		catch (RejectedExecutionException e) {
			LOGGER.info(task + " rejected, probably because the node is shutting down");
			onFail(task, e);
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
			return "block discovery event for block at height " + block.getHeight();
		}

		@Override @OnThread("events")
		protected void body() throws DatabaseException {
			db.setHeadHash(db.add(block));

			LocalDateTime nextBlockStartTime = startDateTime.plus(block.getTotalWaitingTime(), ChronoUnit.MILLIS);
			execute(new MineNewBlockTask(LocalNodeImpl.this, block, nextBlockStartTime));
		}
	}

	/**
	 * An event fired to signal that some peers have been added to the node.
	 */
	public class PeersAddedEvent extends Event {
		private final Peer[] peers;

		public PeersAddedEvent(Stream<Peer> peers) {
			this.peers = peers.toArray(Peer[]::new);
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
			execute(new SuggestPeersTask(getPeers(), onPeersAddedListeners::getListeners, LocalNodeImpl.this));
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

			var maybeHead = db.getHead();
			if (maybeHead.isPresent()) {
				LocalDateTime nextBlockStartTime = startDateTime.plus(maybeHead.get().getTotalWaitingTime(), ChronoUnit.MILLIS);
				execute(new DelayedMineNewBlockTask(LocalNodeImpl.this, maybeHead.get(), nextBlockStartTime, config.deadlineWaitTimeout));
			}
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
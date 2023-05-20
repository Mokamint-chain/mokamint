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

import static io.hotmoka.exceptions.UncheckConsumer.uncheck;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.OnThread;
import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.Blocks;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.Peer;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNode;
import io.mokamint.node.local.internal.tasks.DelayedMineNewBlockTask;
import io.mokamint.node.local.internal.tasks.MineNewBlockTask;

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
	private final PunishableSet<Miner> miners;

	/**
	 * The database containing the blockchain.
	 */
	private final Database db;

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
	 * The peers of the node, with the time of the last contact.
	 */
	private final PunishableSetWithValue<Peer, Long> peers;

	private final static Logger LOGGER = Logger.getLogger(LocalNodeImpl.class.getName());

	/**
	 * Creates a local node of a Mokamint blockchain, for the given application,
	 * using the given miners.
	 * 
	 * @param config the configuration of the node
	 * @param app the application
	 * @param miners the miners
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 * @throws IOException if the database is corrupted
	 * @throws URISyntaxException if some URI in the database has an illegal syntax
	 */
	public LocalNodeImpl(Config config, Application app, Miner... miners) throws NoSuchAlgorithmException, IOException, URISyntaxException {
		this.config = config;
		this.app = app;
		this.miners = PunishableSets.of(Stream.of(miners), _miner -> config.minerInitialPoints);
		this.db = new Database(config);
		// TODO: initial value in config
		this.peers = PunishableSetWithValues.adapt(PunishableSets.of(db.getPeers(), _peer -> config.minerInitialPoints, uncheck(db::addPeer), uncheck(db::removePeer)), _peer -> 0L);
		config.seeds()
			.map(Peers::of)
			.forEach(peers::add);

		Optional<Block> head = db.getHead();
		if (head.isPresent()) {
			this.startDateTime = db.getGenesis().get().getStartDateTimeUTC();
			LocalDateTime nextBlockStartTime = startDateTime.plus(head.get().getTotalWaitingTime(), ChronoUnit.MILLIS);
			execute(new MineNewBlockTask(this, head.get(), nextBlockStartTime));
		}
		else {
			this.startDateTime = LocalDateTime.now(ZoneId.of("UTC"));
			GenesisBlock genesis = Blocks.genesis(startDateTime);
			signal(new BlockDiscoveryEvent(genesis));
		}
	}

	@Override
	public Optional<Block> getBlock(byte[] hash) throws NoSuchAlgorithmException {
		return db.get(hash);
	}

	@Override
	public Stream<Peer> getPeers() {
		return peers.getElements();
	}

	@Override
	public void close() throws InterruptedException {
		events.shutdownNow();
		tasks.shutdownNow();
		
		try {
			events.awaitTermination(10, TimeUnit.SECONDS);
			tasks.awaitTermination(10, TimeUnit.SECONDS);
		}
		finally {
			db.close();
		}
	}

	@Override
	public void addPeer(Peer peer) {
		peers.add(peer);
	}

	@Override
	public void removePeer(Peer peer) {
		peers.remove(peer);
	}

	/**
	 * Yields the configuration of this node.
	 * 
	 * @return the configuration of this node
	 */
	public Config getConfig() {
		return config;
	}

	/**
	 * Yields the application this blockchain is running.
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
	public PunishableSet<Miner> getMiners() {
		return miners;
	}

	/**
	 * The type of the events processed on the event thread.
	 */
	public interface Event extends Runnable {}

	/**
	 * Signals that an event occurred. This is typically called from task,
	 * to signal that something occurred and that the node must react accordingly.
	 * Events get queued into the {@link #events} queue and eventually executed
	 * on its only thread, in order.
	 * 
	 * @param event the event that occurred
	 */
	public void signal(Event event) {
		try {
			LOGGER.info("received " + event);
			events.execute(event);
		}
		catch (RejectedExecutionException e) {
			LOGGER.info(event + " rejected, probably because the node is shutting down");
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
	}

	/**
	 * Runs the given task in one thread from the {@link #tasks} executors.
	 * 
	 * @param task the task to run
	 */
	private void execute(Task task) {
		try {
			tasks.execute(task);
		}
		catch (RejectedExecutionException e) {
			LOGGER.info("task rejected, probably because the node is shutting down");
		}
	}

	/**
	 * An event fired to signal that a block has been discovered. This might come
	 * from the node itself, if it finds a deadline and a new block by using its own miners,
	 * but also from a peer, that fund a block and whispers it to us.
	 */
	public class BlockDiscoveryEvent implements Event {
		public final Block block;

		public BlockDiscoveryEvent(Block block) {
			this.block = block;
		}

		@Override
		public String toString() {
			return "block discovery event for block at height " + block.getHeight();
		}

		@Override @OnThread("events")
		public void run() {
			try {
				db.setHeadHash(db.add(block));
				LocalDateTime nextBlockStartTime = startDateTime.plus(block.getTotalWaitingTime(), ChronoUnit.MILLIS);
				execute(new MineNewBlockTask(LocalNodeImpl.this, block, nextBlockStartTime));
			}
			catch (IOException e) {
				LOGGER.log(Level.SEVERE, "database error", e);
			}
		}
	}

	/**
	 * An event fired to signal that a miner misbehaved.
	 */
	public class MinerMisbehaviorEvent implements Event {
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
		public void run() {
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
	public class NoDeadlineFoundEvent implements Event {

		@Override
		public String toString() {
			return "no deadline found event";
		}

		@Override @OnThread("events")
		public void run() {
			// all miners timed-out
			miners.forEach(miner -> miners.punish(miner, config.minerPunishmentForTimeout));

			Optional<Block> head;
			try {
				head = db.getHead();
			}
			catch (NoSuchAlgorithmException e) {
				LOGGER.log(Level.SEVERE, "the database referes to an unknown hashing algorithm", e);
				return;
			}
			catch (IOException e) {
				LOGGER.log(Level.SEVERE, "the database is corrupted", e);
				return;
			}

			if (head.isPresent()) {
				LocalDateTime nextBlockStartTime = startDateTime.plus(head.get().getTotalWaitingTime(), ChronoUnit.MILLIS);
				execute(new DelayedMineNewBlockTask(LocalNodeImpl.this, head.get(), nextBlockStartTime, config.deadlineWaitTimeout));
			}
		}
	}

	/**
	 * An event fired when a new block task failed because there are no miners.
	 */
	public class NoMinersAvailableEvent implements Event {

		@Override
		public String toString() {
			return "no miners available event";
		}

		@Override @OnThread("events")
		public void run() {}
	}
}
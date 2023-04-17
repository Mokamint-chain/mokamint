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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.OnThread;
import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.api.Node;
import io.mokamint.node.local.internal.blockchain.Block;
import io.mokamint.node.local.internal.blockchain.GenesisBlock;
import io.mokamint.node.local.internal.tasks.MineNewBlockTask;
import io.mokamint.node.local.internal.tasks.UncheckedInterruptedException;

/**
 * A local node of a Mokamint blockchain.
 */
@ThreadSafe
public class LocalNodeImpl implements Node {

	/**
	 * The application executed by the blockchain.
	 */
	private final Application app;

	/**
	 * The miners connected to the node.
	 */
	private final Miners miners;

	/**
	 * The genesis block of the blockchain.
	 */
	private final GenesisBlock genesis;

	/**
	 * The single executor of the events. Events get queued into this queue and run in order
	 * on that executor, called the event thread.
	 */
	private final ExecutorService events = Executors.newSingleThreadExecutor();

	/**
	 * The executors of tasks. There might be more tasks in execution at the same time.
	 */
	private final ExecutorService tasks = Executors.newCachedThreadPool();

	private final static Logger LOGGER = Logger.getLogger(LocalNodeImpl.class.getName());

	/**
	 * Creates a local node of a Mokamint blockchain, for the given application,
	 * using the given miners.
	 *
	 * @param app the application
	 * @param miners the miners
	 */
	public LocalNodeImpl(Application app, Miner... miners) {
		this.app = app;
		this.miners = new Miners(Stream.of(miners));
		this.genesis = Block.genesis(LocalDateTime.now(ZoneId.of("UTC")));

		// for now, everything starts with the discovery of the genesis block
		signal(new BlockDiscoveryEvent(genesis));
	}

	@Override
	public void close() {
		events.shutdownNow();
		tasks.shutdownNow();
		
		try {
			events.awaitTermination(10, TimeUnit.SECONDS);
			tasks.awaitTermination(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			throw new UncheckedInterruptedException(e);
		}
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
	public Miners getMiners() {
		return miners;
	}

	/**
	 * The type of the events processed on the event thread.
	 */
	private interface Event extends Runnable {}

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
		 * 
		 * @return the node running this task
		 */
		protected final LocalNodeImpl getNode() {
			return LocalNodeImpl.this;
		}
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
		private final Block block;

		public BlockDiscoveryEvent(Block block) {
			this.block = block;
		}

		@Override
		public String toString() {
			return "block discovery event for block at height " + block.getHeight();
		}

		@Override @OnThread("events")
		public void run() {
			LocalDateTime startTime = genesis.getStartDateTimeUTC().plus(block.getTotalWaitingTime(), ChronoUnit.MILLIS);
			execute(new MineNewBlockTask(LocalNodeImpl.this, block, startTime));
		}
	}

	/**
	 * An event fired to signal that a miner misbehaved.
	 */
	public class MinerMisbehaviorEvent implements Event {
		protected final Miner miner;
		protected final int points;

		/**
		 * Creates an event that expresses a miner's misbehavior.
		 * 
		 * @param miner the miner that misbehaved
		 * @param points how many points should be removed for this misbehavior
		 */
		public MinerMisbehaviorEvent(Miner miner, int points) {
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
	public class MinerTimeoutEvent extends MinerMisbehaviorEvent {

		public MinerTimeoutEvent(Miner miner) {
			super(miner, Miners.MINER_PUNISHMENT_FOR_TIMEOUT);
		}

		@Override
		public String toString() {
			return "miner timeout event [-" + points + " points] for miner " + miner.toString();
		}
	}

	/**
	 * An event fired to signal that the connection to a miner timed-out.
	 */
	public class MinerComputedIllegalDeadlineEvent extends MinerMisbehaviorEvent {

		public MinerComputedIllegalDeadlineEvent(Miner miner) {
			super(miner, Miners.MINER_PUNISHMENT_FOR_ILLEGAL_DEADLINE);
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

		public NoDeadlineFoundEvent() {}

		@Override
		public String toString() {
			return "no deadline found event";
		}

		@Override @OnThread("events")
		public void run() {
			// it behaves as like all miners timed-out
			miners.forEach(miner -> miners.punish(miner, Miners.MINER_PUNISHMENT_FOR_TIMEOUT));
		}
	}

	/**
	 * An event fired when a new block task failed because there are no miners.
	 */
	public class NoMinersAvailableEvent implements Event {

		public NoMinersAvailableEvent() {}

		@Override
		public String toString() {
			return "no miners available event";
		}

		@Override @OnThread("events")
		public void run() {
		}
	}
}
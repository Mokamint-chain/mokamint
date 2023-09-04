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

package io.mokamint.node.local.internal.blockchain;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Event;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.NodeMiners;
import io.mokamint.node.messages.WhisperBlockMessages;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;

/**
 * A task that mines a new block, above a previous block.
 * It requests a deadline to the miners of the node
 * and waits for the best deadline to expire.
 * Once expired, it builds the block and signals a new block discovery to the node.
 * This task assumes that the blockchain is not empty.
 */
public class MineNewBlockTask implements Task {
	private final static Logger LOGGER = Logger.getLogger(MineNewBlockTask.class.getName());

	/**
	 * The start of the log messages generated by this task.
	 */
	private volatile String logPrefix;

	/**
	 * The description of this task.
	 */
	private volatile String toString;

	/**
	 * The node performing the mining.
	 */
	private final LocalNodeImpl node;

	/**
	 * The blockchain of the node.
	 */
	private final Blockchain blockchain;

	/**
	 * The configuration of the node running this task.
	 */
	private final Config config;

	/**
	 * The application running in the node.
	 */
	private final Application app;

	/**
	 * The miners of the node.
	 */
	private final NodeMiners miners;

	/**
	 * Creates a task that mines a new block.
	 * 
	 * @param node the node performing the mining
	 */
	MineNewBlockTask(LocalNodeImpl node) {
		this.node = node;
		this.blockchain = node.getBlockchain();
		this.config = node.getConfig();
		this.app = node.getApplication();
		this.miners = node.getMiners();
		this.logPrefix = "";
		this.toString = "next block mining";
	}

	@Override
	public String logPrefix() {
		return logPrefix;
	}

	@Override
	public String toString() {
		return toString;
	}

	@Override
	public void body() throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException, InterruptedException {
		if (blockchain.isEmpty())
			LOGGER.log(Level.SEVERE, "cannot mine on an empty blockchain");
		else if (miners.get().count() == 0L)
			node.submit(new NoMinersAvailableEvent());
		else
			new Run();
	}

	/**
	 * An event fired when a new block task failed because there are no miners.
	 */
	public class NoMinersAvailableEvent implements Event {

		private NoMinersAvailableEvent() {}

		@Override
		public String toString() {
			return "no miners available event";
		}

		@Override
		public void body() {}

		@Override
		public String logPrefix() {
			return logPrefix;
		}
	}

	/**
	 * An event fired when a new block task timed-out without finding a single deadline,
	 * despite having at least a miner available.
	 */
	public class NoDeadlineFoundEvent implements Event {

		private NoDeadlineFoundEvent() {}

		@Override
		public String toString() {
			return "no deadline found event";
		}

		@Override
		public void body() {
			// all miners timed-out
			miners.get().forEach(miner -> miners.punish(miner, config.minerPunishmentForTimeout));
			node.submit(new DelayedMineNewBlockTask(node));
		}

		@Override
		public String logPrefix() {
			return logPrefix;
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
		private MinerMisbehaviorEvent(Miner miner, long points) {
			this.miner = miner;
			this.points = points;
		}

		@Override
		public String toString() {
			return "miner " + miner + " misbehavior event [-" + points + " points]";
		}

		@Override
		public void body() {
			miners.punish(miner, points);
		}

		@Override
		public String logPrefix() {
			return logPrefix;
		}
	}

	/**
	 * An event fired to signal that the connection to a miner timed-out.
	 */
	public class IllegalDeadlineEvent extends MinerMisbehaviorEvent {

		private IllegalDeadlineEvent(Miner miner) {
			super(miner, config.minerPunishmentForIllegalDeadline);
		}

		@Override
		public String toString() {
			return "miner " + miner + " computed illegal deadline event [-" + points + " points]";
		}
	}

	/**
	 * An event fired to signal that a block has been mined.
	 * It adds it to the blockchain and whispers it to all peers.
	 */
	public class BlockMinedEvent implements Event {
		public final Block block;
		public final String hexBlockHash;

		private BlockMinedEvent(Block block) {
			this.block = block;
			this.hexBlockHash = block.getHexHash(config.getHashingForBlocks());
		}

		@Override
		public String toString() {
			return "block mined event for block " + hexBlockHash;
		}

		@Override
		public void body() throws DatabaseException, NoSuchAlgorithmException, VerificationException, ClosedDatabaseException {
			if (blockchain.add(block)) {
				LOGGER.info(logPrefix + "whispering block " + hexBlockHash + " to all peers");
				node.whisper(WhisperBlockMessages.of(block, UUID.randomUUID().toString()), _whisperer -> false);
			}
		}

		@Override
		public String logPrefix() {
			return logPrefix;
		}
	}

	/**
	 * Run environment.
	 */
	private class Run {

		/**
		 * The block over which mining is performed.
		 */
		private final Block previous;

		/**
		 * The height of the new block that is being mined.
		 */
		private final long heightOfNewBlock;

		/**
		 * The hexadecimal representation of the hash of the parent block of the
		 * block being mined by this task.
		 */
		private final String previousHex;

		/**
		 * The moment when the previous block has been mined. From that moment we
		 * count the time to wait for the deadline.
		 */
		private final LocalDateTime startTime;

		/**
		 * The description of the deadline required for the next block.
		 */
		private final DeadlineDescription description;

		/**
		 * The best deadline computed so far. This is empty until a first deadline is found. Since more miners
		 * might work for a node, this deadline might change more than once, to increasingly better deadlines.
		 */
		private final ImprovableDeadline currentDeadline = new ImprovableDeadline();

		/**
		 * The waker used to wait for a deadline to expire.
		 */
		private final Waker waker = new Waker();

		/**
		 * Set to true when the task has completed, also in the case when
		 * it could not find any deadline.
		 */
		private final boolean done;

		private Run() throws InterruptedException, DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException {
			this.previous = blockchain.getHead().get();
			this.heightOfNewBlock = previous.getHeight() + 1;
			this.previousHex = previous.getHexHash(config.getHashingForBlocks());
			logPrefix = "height " + heightOfNewBlock + ": ";
			toString = "next block mining on top of block " + previousHex;
			this.startTime = blockchain.getGenesis().get().getStartDateTimeUTC().plus(previous.getTotalWaitingTime(), ChronoUnit.MILLIS);
			this.description = previous.getNextDeadlineDescription(config.getHashingForGenerations(), config.getHashingForDeadlines());

			try {
				requestDeadlineToEveryMiner();
				waitUntilFirstDeadlineArrives();
				waitUntilDeadlineExpires();
				createNewBlock().ifPresent(this::informNodeAboutNewBlock);
			}
			catch (TimeoutException e) {
				LOGGER.warning(logPrefix + MineNewBlockTask.this + ": timed out while waiting for a deadline");
				node.submit(new NoDeadlineFoundEvent());
			}
			finally {
				turnWakerOff();
				this.done = true;
			}
		}

		private void requestDeadlineToEveryMiner() {
			LOGGER.info(logPrefix + "asking miners for a deadline: " + description);
			miners.get().forEach(miner -> miner.requestDeadline(description, deadline -> onDeadlineComputed(deadline, miner)));
		}

		private void waitUntilFirstDeadlineArrives() throws InterruptedException, TimeoutException {
			currentDeadline.await(config.deadlineWaitTimeout, MILLISECONDS);
		}

		private void informNodeAboutNewBlock(Block block) {
			node.submit(new BlockMinedEvent(block));
		}

		/**
		 * Called by miners when they find a deadline.
		 * 
		 * @param deadline the deadline that has just been computed
		 * @param miner the miner that found the deadline
		 */
		private void onDeadlineComputed(Deadline deadline, Miner miner) {
			LOGGER.info(logPrefix + "received deadline " + deadline);

			if (done)
				LOGGER.info(logPrefix + "discarding deadline " + deadline + " since it arrived too late");
			else if (miners.get().noneMatch(m -> m == miner)) // TODO: should I really discard it?
				LOGGER.info(logPrefix + "discarding deadline " + deadline + " since its miner is unknown");
			else if (currentDeadline.isWorseThan(deadline)) {
				if (deadline.isLegalFor(description, app)) {
					if (currentDeadline.updateIfWorseThan(deadline)) {
						LOGGER.info(logPrefix + "improved deadline to " + deadline);
						setWaker(deadline);
					}
					else
						LOGGER.info(logPrefix + "discarding deadline " + deadline + " since it's not better than the current deadline");
				}
				else {
					LOGGER.info(logPrefix + "discarding deadline " + deadline + " since it's illegal");
					node.submit(new IllegalDeadlineEvent(miner));
				}
			}
			else
				LOGGER.info(logPrefix + "discarding deadline " + deadline + " since it's not better than the current deadline");
		}

		private void waitUntilDeadlineExpires() throws InterruptedException {
			waker.await();
		}

		/**
		 * Creates the new block. This might be missing if it realizes that it would be worse
		 * than the current head of the blockchain: useless to execute and verify the transactions
		 * if it does not win the race.
		 * 
		 * @return the block, if any
		 * @throws DatabaseException if the database is corrupted
		 * @throws ClosedDatabaseException if the database is already closed
		 */
		private Optional<Block> createNewBlock() throws DatabaseException, ClosedDatabaseException {
			var deadline = currentDeadline.get().get(); // here, we know that a deadline has been computed
			var nextBlock = previous.getNextBlockDescription(deadline, config.targetBlockCreationTime, config.hashingForBlocks, config.hashingForDeadlines);

			var powerOfHead = blockchain.getPowerOfHead();
			if (powerOfHead.isPresent() && powerOfHead.get().compareTo(nextBlock.getPower()) >= 0) {
				LOGGER.info(logPrefix + "not creating block on top of " + previousHex + " since it would not improve the head");
				return Optional.empty();
			}

			return Optional.of(nextBlock);
		}

		/**
		 * Sets a waker at the expiration of the given deadline.
		 * 
		 * @param deadline the deadline
		 */
		private void setWaker(Deadline deadline) {
			long millisecondsToWait = deadline.getMillisecondsToWaitFor(previous.getAcceleration());
			long millisecondsAlreadyPassed = Duration.between(startTime, LocalDateTime.now(ZoneId.of("UTC"))).toMillis();
			long stillToWait = millisecondsToWait - millisecondsAlreadyPassed;
			if (waker.set(stillToWait))
				LOGGER.info(logPrefix + "set up a waker in " + stillToWait + " ms");
		}

		private void turnWakerOff() {
			waker.shutdownNow();
		}
	}
}
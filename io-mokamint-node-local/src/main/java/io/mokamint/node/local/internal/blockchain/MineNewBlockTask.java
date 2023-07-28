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

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.OnThread;
import io.hotmoka.crypto.Hex;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.local.internal.Database;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;

/**
 * A task that mines a new block, above a previous block.
 * It requests a deadline to the miners of the node
 * and waits for the best deadline to expire.
 * Once expired, it builds the block and signals a new block discovery to the node.
 */
public class MineNewBlockTask extends Task {
	private final static Logger LOGGER = Logger.getLogger(MineNewBlockTask.class.getName());
	private final static BigInteger _20 = BigInteger.valueOf(20L);
	private final static BigInteger _100 = BigInteger.valueOf(100L);

	/**
	 * The database of the node.
	 */
	private final Database db;

	/**
	 * The block over which mining is performed.
	 */
	private Optional<Block> previous;

	/**
	 * Creates a task that mines a new block.
	 * 
	 * @param node the node for which this task is working
	 * @param db the database of the node
	 * @param previous the previous block, if any; otherwise a genesis block is mined
	 */
	public MineNewBlockTask(LocalNodeImpl node, Database db, Optional<Block> previous) {
		node.super();

		this.db = db;
		this.previous = previous;
	}

	@Override
	public String toString() {
		if (previous.isEmpty())
			return "height 0: mine genesis block";
		else
			return "height " + (previous.get().getHeight() + 1) + ": mine next block";
	}

	@Override @OnThread("tasks")
	protected void body() {
		try {
			if (previous.isPresent()) {
				if (node.getMiners().count() == 0L)
					node.submit(node.new NoMinersAvailableEvent());
				else
					new Run(previous.get());
			}
			else {
				var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")));
				LOGGER.info("height 0: finished mining the genesis block " +
					Hex.toHexString(genesis.getHash(node.getConfig().getHashingForBlocks())));
				node.submit(node.new BlockDiscoveryEvent(genesis));
			}
		}
		catch (InterruptedException e) {
			LOGGER.log(Level.WARNING, this + " interrupted");
			Thread.currentThread().interrupt();
		}
		catch (TimeoutException e) {
			LOGGER.warning(this + ": timed out while waiting for a deadline");
			node.submit(node.new NoDeadlineFoundEvent(previous.get()));
		}
		catch (NoSuchAlgorithmException e) {
			LOGGER.log(Level.SEVERE, this + ": the database contains a node that refers to an unknown hashing algorithm", e);
		}
		catch (DatabaseException e) {
			LOGGER.log(Level.SEVERE, this + ": the database seems corrupted", e);
		}
	}

	/**
	 * Run environment.
	 */
	private class Run {

		/**
		 * The target block creation time that will be aimed, in milliseconds.
		 */
		private final BigInteger targetBlockCreationTime;

		/**
		 * The block for which a subsequent block is being mined.
		 */
		private final Block previous;

		/**
		 * The height of the new block that is being mined.
		 */
		private final long heightOfNewBlock;

		/**
		 * The start of the log messages for this block creation.
		 */
		private final String logIntro;

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

		private Run(Block previous) throws InterruptedException, TimeoutException, DatabaseException, NoSuchAlgorithmException {
			this.previous = previous;
			this.heightOfNewBlock = previous.getHeight() + 1;
			this.logIntro = "height " + heightOfNewBlock + ": ";
			this.previousHex = Hex.toHexString(previous.getHash(node.getConfig().getHashingForBlocks()));
			this.startTime = db.getGenesis().get().getStartDateTimeUTC().plus(previous.getTotalWaitingTime(), ChronoUnit.MILLIS);
			this.targetBlockCreationTime = BigInteger.valueOf(node.getConfig().getTargetBlockCreationTime());
			var hashingForGenerations = node.getConfig().getHashingForGenerations();
			this.description = previous.getNextDeadlineDescription(hashingForGenerations, node.getConfig().getHashingForDeadlines());
			LOGGER.info(logIntro + "started mining new block on top of " + previousHex);

			try {
				requestDeadlineToEveryMiner();
				waitUntilFirstDeadlineArrives();
				waitUntilDeadlineExpires();
				informNodeAboutNewBlock(createNewBlock());
			}
			finally {
				turnWakerOff();
				this.done = true;
			}
		}

		private void requestDeadlineToEveryMiner() {
			LOGGER.info(logIntro + "asking miners for a deadline: " + description);

			class DeadlineRequest {
				private final Miner miner;

				private DeadlineRequest(Miner miner) {
					this.miner = miner;

					miner.requestDeadline(description, this::onDeadlineComputed);
				}

				private void onDeadlineComputed(Deadline deadline) {
					Run.this.onDeadlineComputed(deadline, miner);
				}
			}

			node.getMiners().forEach(DeadlineRequest::new);
		}

		private void waitUntilFirstDeadlineArrives() throws InterruptedException, TimeoutException {
			currentDeadline.await(node.getConfig().deadlineWaitTimeout, MILLISECONDS);
		}

		private void informNodeAboutNewBlock(Block block) {
			LOGGER.info(logIntro + "finished mining new block on top of " + previousHex);
			node.submit(node.new BlockDiscoveryEvent(block));
		}

		/**
		 * Called by miners when they find a deadline.
		 * 
		 * @param deadline the deadline that has just been computed
		 * @param miner the miner that found the deadline
		 */
		@OnThread("miner")
		private void onDeadlineComputed(Deadline deadline, Miner miner) {
			LOGGER.info(logIntro + "received deadline " + deadline);

			if (done)
				LOGGER.info(logIntro + "discarding deadline " + deadline + " since it arrived too late");
			else if (node.getMiners().noneMatch(m -> m == miner)) // TODO: should I really discard it?
				LOGGER.info(logIntro + "discarding deadline " + deadline + " since its miner is unknown");
			else if (currentDeadline.isWorseThan(deadline)) {
				if (isLegal(deadline)) {
					if (currentDeadline.updateIfWorseThan(deadline)) {
						LOGGER.info(logIntro + "improved deadline to " + deadline);
						setWaker(deadline);
					}
					else
						LOGGER.info(logIntro + "discarding deadline " + deadline + " since it's not better than the current deadline");
				}
				else {
					LOGGER.info(logIntro + "discarding deadline " + deadline + " since it's illegal");
					node.submit(node.new IllegalDeadlineEvent(miner));
				}
			}
			else
				LOGGER.info(logIntro + "discarding deadline " + deadline + " since it's not better than the current deadline");
		}

		private void waitUntilDeadlineExpires() throws InterruptedException {
			waker.await();
		}

		/**
		 * Determines if a deadline is legal, that is,
		 * it is actually a deadline for the expected description,
		 * it is valid and its prolog is valid for the application. All these conditions
		 * should always hold, if miners behave correctly.
		 * 
		 * @param deadline the deadline to check
		 * @return true if and only if the deadline is legal
		 */
		private boolean isLegal(Deadline deadline) {
			return deadline.matches(description)
				&& deadline.isValid()
				&& node.getApplication().prologIsValid(deadline.getProlog());
		}

		private Block createNewBlock() {
			var deadline = currentDeadline.get().get(); // here, we know that a deadline has been computed
			var powerForNewBlock = computePower(deadline);
			var waitingTimeForNewBlock = millisecondsToWaitFor(deadline);
			var weightedWaitingTimeForNewBlock = computeWeightedWaitingTime(waitingTimeForNewBlock);
			var totalWaitingTimeForNewBlock = computeTotalWaitingTime(waitingTimeForNewBlock);
			var accelerationForNewBlock = computeAcceleration(weightedWaitingTimeForNewBlock);
			var hashOfPreviousBlock = previous.getHash(node.getConfig().getHashingForBlocks());

			return Blocks.of(heightOfNewBlock, powerForNewBlock, totalWaitingTimeForNewBlock,
				weightedWaitingTimeForNewBlock, accelerationForNewBlock, deadline, hashOfPreviousBlock);
		}

		private BigInteger computePower(Deadline deadline) {
			byte[] valueAsBytes = deadline.getValue();
			var value = new BigInteger(1, valueAsBytes);
			return previous.getPower().add
				(BigInteger.TWO.shiftLeft(node.getConfig().getHashingForDeadlines().length() * 8)
						.divide(value.add(BigInteger.ONE)));
		}

		private long computeTotalWaitingTime(long waitingTime) {
			return previous.getTotalWaitingTime() + waitingTime;
		}

		private long computeWeightedWaitingTime(long waitingTime) {
			var previousWeightedWaitingTime_95 = previous.getWeightedWaitingTime() * 95L;
			var waitingTime_5 = waitingTime * 5L;
			return (previousWeightedWaitingTime_95 + waitingTime_5) / 100L;
		}

		/**
		 * Computes the acceleration for the new block, in order to get closer to the target creation time.
		 * 
		 * @param weightedWaitingTimeForNewBlock the weighted waiting time for the new block
		 * @return the acceleration for the new block
		 */
		private BigInteger computeAcceleration(long weightedWaitingTimeForNewBlock) {
			var oldAcceleration = previous.getAcceleration();
			var delta = oldAcceleration
					.multiply(BigInteger.valueOf(weightedWaitingTimeForNewBlock))
					.divide(targetBlockCreationTime)
					.subtract(oldAcceleration);

			var acceleration = oldAcceleration.add(delta.multiply(_20).divide(_100));
			if (acceleration.signum() == 0)
				acceleration = BigInteger.ONE; // acceleration must be strictly positive

			return acceleration;
		}

		private long millisecondsToWaitFor(Deadline deadline) {
			byte[] valueAsBytes = deadline.getValue();
			var value = new BigInteger(1, valueAsBytes);
			value = value.divide(previous.getAcceleration());
			byte[] newValueAsBytes = value.toByteArray();
			// we recreate an array of the same length as at the beginning
			var dividedValueAsBytes = new byte[valueAsBytes.length];
			System.arraycopy(newValueAsBytes, 0, dividedValueAsBytes,
				dividedValueAsBytes.length - newValueAsBytes.length,
				newValueAsBytes.length);
			// we take the first 8 bytes of the divided value
			var firstEightBytes = new byte[] {
				dividedValueAsBytes[0], dividedValueAsBytes[1], dividedValueAsBytes[2], dividedValueAsBytes[3],
				dividedValueAsBytes[4], dividedValueAsBytes[5], dividedValueAsBytes[6], dividedValueAsBytes[7]
			};
			// TODO: theoretically, there might be an overflow when converting into long
			return new BigInteger(1, firstEightBytes).longValue();
		}

		/**
		 * Sets a waker at the expiration of the given deadline.
		 * 
		 * @param deadline the deadline
		 */
		private void setWaker(Deadline deadline) {
			long millisecondsToWait = millisecondsToWaitFor(deadline);
			long millisecondsAlreadyPassed = Duration.between(startTime, LocalDateTime.now(ZoneId.of("UTC"))).toMillis();
			long stillToWait = millisecondsToWait - millisecondsAlreadyPassed;
			if (waker.set(stillToWait))
				LOGGER.info(logIntro + "set up a waker in " + stillToWait + " ms");
		}

		private void turnWakerOff() {
			waker.shutdownNow();
		}
	}
}
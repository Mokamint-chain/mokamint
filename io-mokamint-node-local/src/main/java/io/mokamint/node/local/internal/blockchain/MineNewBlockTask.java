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

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.mokamint.miner.api.Miner;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.OnAddedTransactionHandler;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.mempool.Mempool;
import io.mokamint.node.local.internal.miners.Miners;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import io.mokamint.nonce.api.IllegalDeadlineException;

/**
 * A task that mines a new block, above a previous block.
 * It requests a deadline to the miners of the node
 * and waits for the best deadline to expire.
 * Once expired, it builds the block and signals a new block discovery to the node.
 * This task assumes that the blockchain is not empty.
 */
public class MineNewBlockTask implements Task {

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
	private final LocalNodeConfig config;

	/**
	 * The miners of the node.
	 */
	private final Miners miners;

	private final static Logger LOGGER = Logger.getLogger(MineNewBlockTask.class.getName());

	/**
	 * Creates a task that mines a new block.
	 * 
	 * @param node the node performing the mining
	 */
	public MineNewBlockTask(LocalNodeImpl node) {
		this.node = node;
		this.blockchain = node.getBlockchain();
		this.config = node.getConfig();
		this.miners = node.getMiners();
	}

	@Override
	public void body() throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException, InterruptedException, InvalidKeyException, SignatureException, VerificationException {
		if (blockchain.isEmpty())
			LOGGER.log(Level.SEVERE, "mining: cannot mine on an empty blockchain");
		else if (miners.get().count() == 0L) {
			LOGGER.log(Level.WARNING, "mining: cannot mine because this node currently has no miners attached");
			blockchain.onNoMinersAvailable();
		}
		else {
			var headHash = blockchain.getHeadHash().get();
			Optional<Block> previous = blockchain.getBlock(headHash);
			// if somebody else is mining over the same block, it is useless to do the same
			if (previous.isPresent() && !blockchain.isMiningOver(previous.get()))
				new Run(previous.get(), headHash);
		}
	}

	/**
	 * Run environment.
	 */
	private class Run implements OnAddedTransactionHandler {

		/**
		 * The block over which mining is performed.
		 */
		private final Block previous;

		/**
		 * The mempool containing the transactions that can be added to the new block.
		 */
		private final Mempool mempool;

		/**
		 * The height of the new block that is being mined.
		 */
		private final long heightOfNewBlock;

		/**
		 * A message describing the height of the block being mined. Used in logs.
		 */
		private final String heightMessage;

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
		 * The set of miners that did not answer so far with a legal deadline.
		 */
		private Set<Miner> minersThatDidNotAnswer = ConcurrentHashMap.newKeySet();

		/**
		 * Set to true when the task has completed, also in the case when
		 * it could not find any deadline.
		 */
		private final boolean done;

		private Run(Block previous, byte[] hashOfPrevious) throws InterruptedException, DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException, InvalidKeyException, SignatureException, VerificationException {
			this.previous = previous;
			this.mempool = blockchain.getMempoolAt(hashOfPrevious);
			this.heightOfNewBlock = previous.getDescription().getHeight() + 1;
			this.previousHex = previous.getHexHash(config.getHashingForBlocks());
			this.heightMessage = "mining: height " + heightOfNewBlock + ": ";
			this.startTime = blockchain.getGenesis().get().getStartDateTimeUTC().plus(previous.getDescription().getTotalWaitingTime(), ChronoUnit.MILLIS);
			this.description = previous.getNextDeadlineDescription(config.getHashingForGenerations(), config.getHashingForDeadlines());

			try {
				blockchain.onMiningStarted(previous, this);
				requestDeadlineToEveryMiner();
				waitUntilFirstDeadlineArrives();
				waitUntilDeadlineExpires();
				var maybeBlock = createNewBlock();
				if (maybeBlock.isPresent()) {
					var block = maybeBlock.get();
					blockchain.onBlockMined(block);
					addNodeToBlockchain(block);
				}
			}
			catch (TimeoutException e) {
				LOGGER.warning(heightMessage + "no deadline found (timed out while waiting for a deadline)");
				blockchain.scheduleDelayedMining();
				blockchain.onNoDeadlineFound(previous);
			}
			finally {
				turnWakerOff();
				punishMinersThatDidNotAnswer();
				this.done = true;
				blockchain.onMiningCompleted(previous, this);
			}
		}

		@Override
		public void add(Transaction transaction) throws NoSuchAlgorithmException, ClosedDatabaseException, DatabaseException {
			try {
				mempool.add(transaction);
			}
			catch (RejectedTransactionException e) {
				LOGGER.warning("transaction " + node.getConfig().getHashingForTransactions().getHasher(Transaction::toByteArray).hash(transaction) + " has been rejected: " + e.getMessage());
			}
		}

		private void requestDeadlineToEveryMiner() {
			miners.get().forEach(this::requestDeadlineTo);
		}

		private void requestDeadlineTo(Miner miner) {
			if (Thread.currentThread().isInterrupted()) {
				LOGGER.info(heightMessage + "not creating block on top of " + previousHex + " since the task has been interrupted");
				return;
			}

			LOGGER.info(heightMessage + "asking miner " + miner.getUUID() + " for a deadline: " + description);
			minersThatDidNotAnswer.add(miner);
			miner.requestDeadline(description, deadline -> onDeadlineComputed(deadline, miner));
		}

		private void waitUntilFirstDeadlineArrives() throws InterruptedException, TimeoutException {
			currentDeadline.await(config.getDeadlineWaitTimeout(), MILLISECONDS);
		}

		private void addNodeToBlockchain(Block block) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException {
			if (blockchain.add(block))
				blockchain.scheduleWhisperingWithoutAddition(block);
		}

		/**
		 * Called by miners when they find a deadline.
		 * 
		 * @param deadline the deadline that has just been computed
		 * @param miner the miner that found the deadline
		 */
		private void onDeadlineComputed(Deadline deadline, Miner miner) {
			LOGGER.info(heightMessage + "miner " + miner.getUUID() + " sent deadline " + deadline);

			if (done)
				LOGGER.warning(heightMessage + "discarding deadline " + deadline + " since it arrived too late");
			else {
				try {
					deadline.matchesOrThrow(description, IllegalDeadlineException::new);
					blockchain.check(deadline);

					// we increase the points of the miner, but only for the first deadline that it provides
					if (minersThatDidNotAnswer.remove(miner))
						miners.pardon(miner, config.getMinerPunishmentForTimeout());

					if (!currentDeadline.isWorseThan(deadline))
						LOGGER.info(heightMessage + "discarding deadline " + deadline + " since it is not better than the current deadline");
					else {
						if (currentDeadline.updateIfWorseThan(deadline)) {
							LOGGER.info(heightMessage + "improved deadline to " + deadline);
							setWaker(deadline);
						}
						else
							LOGGER.info(heightMessage + "discarding deadline " + deadline + " since it is not better than the current deadline");
					}
				}
				catch (IllegalDeadlineException e) {
					LOGGER.warning(heightMessage + "discarding deadline " + deadline + " since it is illegal: " + e.getMessage());
					blockchain.onIllegalDeadlineComputed(deadline, miner);

					long points = config.getMinerPunishmentForIllegalDeadline();
					LOGGER.warning(heightMessage + "miner " + miner.getUUID() + " computed an illegal deadline event [-" + points + " points]");

					try {
						miners.punish(miner, points);
					}
					catch (IOException e2) {
						LOGGER.log(Level.SEVERE, heightMessage + "cannot punish miner " + miner.getUUID() + " that computed an illegal deadline", e2);
					}
				}
			}
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
		 * @throws SignatureException if the block could not be signed
		 * @throws InvalidKeyException if the private key of the node is invalid
		 */
		private Optional<Block> createNewBlock() throws DatabaseException, ClosedDatabaseException, InvalidKeyException, SignatureException {
			var deadline = currentDeadline.get().get(); // here, we know that a deadline has been computed
			var description = previous.getNextBlockDescription(deadline, config.getTargetBlockCreationTime(), config.getHashingForBlocks(), config.getHashingForDeadlines());
			var powerOfHead = blockchain.getPowerOfHead();
			if (powerOfHead.isPresent() && powerOfHead.get().compareTo(description.getPower()) >= 0) {
				LOGGER.info(heightMessage + "not creating block on top of " + previousHex + " since it would not improve the head");
				return Optional.empty();
			}

			// TODO: transactions should be added here
			var nextBlock = Blocks.of(description, Stream.empty(), node.getKeys().getPrivate());

			if (Thread.currentThread().isInterrupted()) {
				LOGGER.info(heightMessage + "not creating block on top of " + previousHex + " since the task has been interrupted");
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
			long millisecondsToWait = deadline.getMillisecondsToWaitFor(previous.getDescription().getAcceleration());
			long millisecondsAlreadyPassed = Duration.between(startTime, LocalDateTime.now(ZoneId.of("UTC"))).toMillis();
			long stillToWait = millisecondsToWait - millisecondsAlreadyPassed;
			if (waker.set(stillToWait))
				LOGGER.info(heightMessage + "set up a waker in " + stillToWait + " ms");
		}

		private void turnWakerOff() {
			waker.shutdownNow();
		}

		private void punishMinersThatDidNotAnswer() {
			minersThatDidNotAnswer.forEach(this::punishMinerThatDidNotAnswer);
		}

		private void punishMinerThatDidNotAnswer(Miner miner) {
			try {
				miners.punish(miner, config.getMinerPunishmentForTimeout());
			}
			catch (IOException e) {
				LOGGER.log(Level.SEVERE, heightMessage + "cannot punish miner " + miner + " that did not answer: " + e.getMessage());
			}
		}
	}
}
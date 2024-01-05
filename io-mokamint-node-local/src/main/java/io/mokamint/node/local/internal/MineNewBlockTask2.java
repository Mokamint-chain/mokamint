/*
Copyright 2024 Fausto Spoto

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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.mokamint.miner.api.Miner;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.Mempool.TransactionEntry;
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
public class MineNewBlockTask2 {

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

	private final PriorityBlockingQueue<TransactionEntry> mempool = new PriorityBlockingQueue<>(100, Comparator.reverseOrder());

	private final static Logger LOGGER = Logger.getLogger(MineNewBlockTask2.class.getName());

	/**
	 * The block over which mining is performed.
	 */
	private final Block previous;

	/**
	 * A message describing the height of the block being mined. Used in logs.
	 */
	private final String heightMessage;

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
	private final Set<Miner> minersThatDidNotAnswer = ConcurrentHashMap.newKeySet();

	/**
	 * The task that executes the transactions from the mempool, while waiting for the deadline to expire.
	 * This is an infinite task, hence it must be cancelled explicitly when the deadline expires.
	 */
	private final TransactionsExecutionTask transactionExecutor;

	/**
	 * Set to true when the task has completed, also in the case when
	 * it could not find any deadline.
	 */
	private volatile boolean done;

	/**
	 * True if and only if a new block has been committed to blockchain.
	 */
	private boolean committed;

	/**
	 * Creates a task that mines a new block.
	 * 
	 * @param node the node performing the mining
	 * @throws ClosedDatabaseException 
	 * @throws {@link DatabaseException}
	 * @throws NoSuchAlgorithmException
	 * @throws {@link RejectedExecutionException} if the node is shutting down 
	 */
	public MineNewBlockTask2(LocalNodeImpl node, Block previous) throws DatabaseException, ClosedDatabaseException, RejectedExecutionException {
		this.node = node;
		this.previous = previous;
		this.blockchain = node.getBlockchain();
		this.config = node.getConfig();
		this.miners = node.getMiners();
		this.startTime = blockchain.getGenesis().get().getStartDateTimeUTC().plus(previous.getDescription().getTotalWaitingTime(), ChronoUnit.MILLIS);
		this.heightMessage = "mining: height " + (previous.getDescription().getHeight() + 1) + ": ";
		this.description = previous.getNextDeadlineDescription(config.getHashingForGenerations(), config.getHashingForDeadlines());
		this.transactionExecutor = new TransactionsExecutionTask(node, mempool::take, previous);
	}

	public void run() throws InterruptedException, NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException, InvalidKeyException, SignatureException {
		Future<?> transactionExecutionFuture = node.scheduleTransactionExecutor(transactionExecutor);

		try {
			stopIfInterrupted();
			node.getMempoolTransactionsAt(previous).forEach(mempool::add);
			node.onMiningStarted(previous);
			requestDeadlineToEveryMiner();
			waitUntilFirstDeadlineArrives();
			waitUntilDeadlineExpires();
			stopTransactionExecutor(transactionExecutionFuture);
			var block = createNewBlock();
			commitIfBetterThanHead(block);
		}
		catch (TimeoutException e) {
			LOGGER.warning(heightMessage + "no deadline found (timed out while waiting for a deadline)");
			node.onNoDeadlineFound(previous);
		}
		finally {
			cleanUp(transactionExecutionFuture);
		}
	}

	/**
	 * Adds the given transaction entry to the mempool of the mining task.
	 * 
	 * @param entry the entry to add
	 * @throws NoSuchAlgorithmException if some block in blockchain refers to an unknown cryptographic algorithm
	 * @throws ClosedDatabaseException if the database is closed
	 * @throws DatabaseException if the database is corrupted
	 */
	public void add(TransactionEntry entry) throws NoSuchAlgorithmException, ClosedDatabaseException, DatabaseException {
		if (blockchain.getTransactionAddress(previous, entry.getHash()).isEmpty())
			synchronized (mempool) {
				if (!mempool.contains(entry) && mempool.size() < config.getMempoolSize())
					mempool.offer(entry);
			}
	}

	private void requestDeadlineToEveryMiner() throws InterruptedException {
		for (Miner miner: miners.get().toArray(Miner[]::new))
			requestDeadlineTo(miner);
	}

	private void waitUntilFirstDeadlineArrives() throws InterruptedException, TimeoutException {
		currentDeadline.await(config.getDeadlineWaitTimeout(), MILLISECONDS);
	}

	private void waitUntilDeadlineExpires() throws InterruptedException {
		waker.await();
	}

	/**
	 * Stops the transaction executor. This can be called many times, since it does nothing after the first call.
	 */
	private void stopTransactionExecutor(Future<?> transactionExecutionFuture) {
		transactionExecutionFuture.cancel(true);
	}

	/**
	 * Creates the new block, with the transactions that have been processed by the {@link #transactionExecutor}.
	 * 
	 * @return the block
	 * @throws SignatureException if the block could not be signed
	 * @throws InvalidKeyException if the private key of the node is invalid
	 * @throws InterruptedException if the current thread gets interrupted
	 */
	private Block createNewBlock() throws InvalidKeyException, SignatureException, InterruptedException {
		stopIfInterrupted();
		var deadline = currentDeadline.get().get(); // here, we know that a deadline has been computed
		this.done = true; // further deadlines that might arrive later from the miners are not useful anymore
		var description = previous.getNextBlockDescription(deadline, config.getTargetBlockCreationTime(), config.getHashingForBlocks(), config.getHashingForDeadlines());
		var processedTransactions = transactionExecutor.getProcessedTransactions(deadline);
		return Blocks.of(description, processedTransactions.getTransactions(), processedTransactions.getStateHash(), node.getKeys().getPrivate());
	}

	/**
	 * Commits the given block, if it is better than the current head.
	 *
	 * @param block the block
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is closed
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws NoSuchAlgorithmException if some block refers to an unknown cryptographic algorithm
	 */
	private void commitIfBetterThanHead(Block block) throws DatabaseException, ClosedDatabaseException, InterruptedException, NoSuchAlgorithmException {
		if (blockchain.headIsLessPowerfulThan(block)) {
			transactionExecutor.commitBlock();
			committed = true;
			node.onMined(block);
			addNodeToBlockchain(block);
		}
		else
			LOGGER.info(heightMessage + "not adding any block on top of " + previous.getHexHash(config.getHashingForBlocks()) + " since it would not improve the head");
	}

	/**
	 * Cleans up everything at the end of mining.
	 * 
	 * @throws InterruptedException if the operation gets interrupted
	 */
	private void cleanUp(Future<?> transactionExecutorFuture) throws InterruptedException {
		this.done = true;
		stopTransactionExecutor(transactionExecutorFuture);
		if (!committed)
			transactionExecutor.abortBlock();

		turnWakerOff();
		punishMinersThatDidNotAnswer();
		node.onMiningCompleted(previous);
	}

	private void requestDeadlineTo(Miner miner) throws InterruptedException {
		stopIfInterrupted();
		LOGGER.info(heightMessage + "asking miner " + miner.getUUID() + " for a deadline: " + description);
		minersThatDidNotAnswer.add(miner);
		miner.requestDeadline(description, deadline -> onDeadlineComputed(deadline, miner));
	}

	private void addNodeToBlockchain(Block block) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException, InterruptedException {
		stopIfInterrupted();
		// we do not require to verify the block, since we trust that we create verifiable blocks only
		if (blockchain.addVerified(block))
			node.whisperWithoutAddition(block);
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
				node.check(deadline);

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
				node.onIllegalDeadlineComputed(deadline, miner);

				long points = config.getMinerPunishmentForIllegalDeadline();
				LOGGER.warning(heightMessage + "miner " + miner.getUUID() + " computed an illegal deadline event [-" + points + " points]");
				node.punish(miner, points);
			}
		}
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
		var points = config.getMinerPunishmentForTimeout();
		minersThatDidNotAnswer.forEach(miner -> node.punish(miner, points));
	}

	/**
	 * Checks if the current thread has been interrupted and, in that case, throws an exception.
	 * 
	 * @throws InterruptedException if and only if the current thread has been interrupted
	 */
	private static void stopIfInterrupted() throws InterruptedException {
		if (Thread.currentThread().isInterrupted())
			throw new InterruptedException("Interrupted");
	}
}
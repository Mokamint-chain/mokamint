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
import java.security.SignatureException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.Mempool.TransactionEntry;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineValidityCheckException;
import io.mokamint.nonce.api.IllegalDeadlineException;

/**
 * A block miner above a previous block. It requests a deadline to the miners of the node
 * and waits for the best deadline to expire. Once expired, it builds the block and adds it to the blockchain.
 */
public class BlockMiner {

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
	private final MinersSet miners;

	/**
	 * The mempool used to fill the block with transactions.
	 */
	private final PriorityBlockingQueue<TransactionEntry> mempool = new PriorityBlockingQueue<>(100, Comparator.reverseOrder());

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
	private final LocalDateTime creationTimeOfPrevious;

	/**
	 * The challenge of the deadline required for the next block.
	 */
	private final Challenge challenge;

	/**
	 * The best deadline computed so far. This is empty until a first deadline is found. Since more miners
	 * might work for a node, this deadline might change more than once, to increasingly better deadlines.
	 */
	private final ImprovableDeadline currentDeadline = new ImprovableDeadline();

	/**
	 * A semaphore used to wait for the arrival of the first deadline from the miners.
	 */
	private final Semaphore endOfDeadlineArrivalPeriod = new Semaphore(0);

	/**
	 * A semaphore used to wait for the end of the deadline.
	 */
	private final Semaphore endOfWaitingPeriod = new Semaphore(0);

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
	 * True if and only if a new block has been committed.
	 */
	private boolean committed;

	/**
	 * Set to true when the task has completed, also in the case when it could not find any deadline.
	 */
	private volatile boolean done;

	private volatile boolean interrupted;

	private final static Logger LOGGER = Logger.getLogger(BlockMiner.class.getName());

	/**
	 * Creates a task that mines a new block. It assumes that the blockchain of the node is non-empty.
	 * 
	 * @param node the node performing the mining
	 * @throws InterruptedException if the thread running this code gets interrupted
	 * @throws TimeoutException if the application of the node timed out
	 * @throws NodeException if the node is misbehaving
	 */
	public BlockMiner(LocalNodeImpl node) throws InterruptedException, TimeoutException, NodeException {
		this.node = node;
		this.blockchain = node.getBlockchain();
		this.previous = blockchain.getHead().get();
		this.config = node.getConfig();
		this.miners = node.getMiners();
		this.creationTimeOfPrevious = blockchain.creationTimeOf(previous).get();
		this.heightMessage = "mining: height " + (previous.getDescription().getHeight() + 1) + ": ";
		this.challenge = previous.getDescription().getNextChallenge();
		this.transactionExecutor = new TransactionsExecutionTask(node, mempool::take, previous, creationTimeOfPrevious);
	}

	/**
	 * Mines the new block.
	 * 
	 * @throws InterruptedException if the thread running this code gets interrupted
	 * @throws TimeoutException if some operation timed out
	 * @throws RejectedExecutionException if the node is shutting down 
	 * @throws NodeException if the node is misbehaving
	 */
	public void mine() throws NodeException, InterruptedException, TimeoutException, RejectedExecutionException {
		LOGGER.info("mining: starting mining over block " + previous.getHexHash());
		transactionExecutor.start();

		try {
			if (interrupted)
				return;

			node.forEachMempoolTransactionAt(previous, mempool::add);
			node.onMiningStarted(previous);
			requestDeadlineToEveryMiner();

			if (interrupted)
				return;

			if (!waitUntilFirstDeadlineArrives()) {
				LOGGER.warning(heightMessage + "no deadline found (timed out while waiting for a deadline)");
				node.onNoDeadlineFound(previous);
				return;
			}

			waitUntilDeadlineExpires();

			if (interrupted)
				return;

			var block = createNewBlock();

			if (interrupted)
				return;

			if (block.isPresent())
				commitIfBetterThanHead(block.get());
		}
		finally {
			cleanUp();
		}
	}

	/**
	 * Adds the given transaction entry to the mempool of the mining task.
	 * 
	 * @param entry the entry to add
	 * @throws NodeException if the node is misbehaving
	 */
	public void add(TransactionEntry entry) throws NodeException {
		if (blockchain.getTransactionAddress(previous, entry.getHash()).isEmpty())
			synchronized (mempool) {
				if (!mempool.contains(entry) && mempool.size() < config.getMempoolSize())
					mempool.offer(entry);
			}
	}

	public void interrupt() {
		interrupted = true;
		endOfDeadlineArrivalPeriod.release();
		endOfWaitingPeriod.release();
		waker.turnOff();
	}

	private void requestDeadlineToEveryMiner() throws InterruptedException {
		for (var miner: miners.get().toArray(Miner[]::new))
			requestDeadlineTo(miner);
	}

	private boolean waitUntilFirstDeadlineArrives() throws InterruptedException {
		return endOfDeadlineArrivalPeriod.tryAcquire(config.getDeadlineWaitTimeout(), MILLISECONDS);
	}

	private void waitUntilDeadlineExpires() throws InterruptedException {
		endOfWaitingPeriod.acquire();
	}

	/**
	 * Creates the new block, with the transactions that have been processed by the {@link #transactionExecutor}.
	 * 
	 * @return the block; this might be missing if some transaction could not be delivered successfully
	 * @throws TimeoutException if the application did not answer in time
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<NonGenesisBlock> createNewBlock() throws InterruptedException, TimeoutException, NodeException {
		var deadline = currentDeadline.get(); // here, we know that a deadline has been computed
		transactionExecutor.stop();
		this.done = true; // further deadlines that might arrive later from the miners are not useful anymore
		var description = previous.getNextBlockDescription(deadline);
		
		try {
			var processedTransactions = transactionExecutor.getProcessedTransactions(deadline);
			if (processedTransactions.isPresent())
				return Optional.of(Blocks.of(description, processedTransactions.get().getSuccessfullyDeliveredTransactions(), processedTransactions.get().getStateId(), node.getKeys().getPrivate()));
			else
				return Optional.empty();
		}
		catch (ApplicationException | UnknownGroupIdException | InvalidKeyException | SignatureException e) {
			throw new NodeException(e);
		}
	}

	/**
	 * Commits the given block, if it is better than the current head.
	 *
	 * @param block the block
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws TimeoutException if the application did not provide an answer in time
	 * @throws NodeException if the node is misbehaving
	 */
	private void commitIfBetterThanHead(NonGenesisBlock block) throws InterruptedException, TimeoutException, NodeException {
		// it is theoretically possible that head and block have exactly the same power:
		// this might lead to temporary forks, when a node follows one chain and another node
		// follows another chain, both with the same power. However, such forks would be
		// subsequently resolved, when a further block will expand either of the chains
		if (blockchain.isBetterThanHead(block)) {
			try {
				transactionExecutor.commitBlock();
			}
			catch (ApplicationException | UnknownGroupIdException e) {
				throw new NodeException(e);
			}

			committed = true;
			node.onMined(block);
			addToBlockchain(block);
		}
		else
			LOGGER.info(heightMessage + "not adding any block on top of " + previous.getHexHash() + " since it would not improve the head");
	}

	/**
	 * Cleans up everything at the end of mining.
	 * 
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws TimeoutException if some operation timed out
	 * @throws NodeException if the node is misbehaving
	 */
	private void cleanUp() throws InterruptedException, TimeoutException, NodeException {
		done = true;
		transactionExecutor.stop();

		try {
			if (!committed)
				transactionExecutor.abortBlock();

			node.onMiningCompleted(previous);
		}
		catch (UnknownGroupIdException | ApplicationException e) {
			throw new NodeException(e);
		}
		finally {
			punishMinersThatDidNotAnswer();
		}
	}

	private void requestDeadlineTo(Miner miner) throws InterruptedException {
		if (!interrupted) {
			LOGGER.info(heightMessage + "challenging miner " + miner.getUUID() + " with: " + challenge);
			minersThatDidNotAnswer.add(miner);
			miner.requestDeadline(challenge, deadline -> onDeadlineComputed(deadline, miner));
		}
	}

	private void addToBlockchain(Block block) throws InterruptedException, TimeoutException, NodeException {
		// we do not require to verify the block, since we trust that we create verifiable blocks only
		if (!interrupted && blockchain.addVerified(block))
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
			LOGGER.warning(heightMessage + "discarding belated deadline " + deadline);
		else {
			try {
				deadline.getChallenge().matchesOrThrow(challenge, IllegalDeadlineException::new);
				node.check(deadline);

				// we increase the points of the miner, but only for the first deadline that it provides
				if (minersThatDidNotAnswer.remove(miner))
					miners.pardon(miner, config.getMinerPunishmentForTimeout());

				currentDeadline.updateIfWorseThan(deadline);
			}
			catch (IllegalDeadlineException e) {
				LOGGER.warning(heightMessage + "discarding illegal deadline " + deadline + ": " + e.getMessage());
				node.onIllegalDeadlineComputed(deadline, miner);
				long points = config.getMinerPunishmentForIllegalDeadline();
				node.punish(miner, points, "it provided an illegal deadline");
			}
			catch (TimeoutException | DeadlineValidityCheckException e) {
				LOGGER.log(Level.SEVERE, heightMessage + "discarding uncheckable deadline " + deadline + ": " + e.getMessage());
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Sets a waker at the expiration of the given deadline.
	 * 
	 * @param deadline the deadline
	 */
	private void setWaker(Deadline deadline) {
		long millisecondsToWait = deadline.getMillisecondsToWait(previous.getDescription().getAcceleration());
		long millisecondsAlreadyPassed = Duration.between(creationTimeOfPrevious, LocalDateTime.now(ZoneId.of("UTC"))).toMillis();
		long stillToWait = millisecondsToWait - millisecondsAlreadyPassed;
		waker.set(stillToWait);
	}

	private void punishMinersThatDidNotAnswer() {
		var points = config.getMinerPunishmentForTimeout();
		minersThatDidNotAnswer.forEach(miner -> node.punish(miner, points, "it didn't answer to the challenge"));
	}

	/**
	 * A wrapper for a deadline, that can progressively improved.
	 */
	@ThreadSafe
	private class ImprovableDeadline {

		@GuardedBy("this")
		private Deadline deadline;

		/**
		 * Updates this deadline if the given deadline is better.
		 * 
		 * @param other the given deadline
		 */
		private synchronized void updateIfWorseThan(Deadline other) {
			if (deadline == null || other.compareByValue(deadline) < 0) {
				deadline = other;
				endOfDeadlineArrivalPeriod.release();
				LOGGER.info(heightMessage + "improved deadline to " + deadline);
				setWaker(deadline);
			}
			else
				LOGGER.info(heightMessage + "discarding not improving deadline " + deadline);
		}

		private synchronized Deadline get() {
			return deadline;
		}
	}

	/**
	 * A synchronization primitive that allows to await a waker.
	 * The waker can be set many times. Setting a new waker replaces the
	 * previous one, that gets discarded.
	 */
	@ThreadSafe
	private class Waker {

		/**
		 * The current future of the waiting task, if any.
		 */
		@GuardedBy("this")
		private Future<?> future;

		/**
		 * Sets a waker at the given time distance from now. If the waker was already set,
		 * it gets replaced with the new timeout. If this object was already shut down, it does nothing.
		 * 
		 * @param millisecondsToWait the timeout to wait for
		 */
		private synchronized void set(long millisecondsToWait) {
			turnOff();

			if (millisecondsToWait <= 0)
				endOfWaitingPeriod.release();
			else {
				try {
					future = node.submit(() -> taskBody(millisecondsToWait), "waker set in " + millisecondsToWait + " ms");
					LOGGER.info(heightMessage + "set up a waker in " + millisecondsToWait + " ms");
				}
				catch (RejectedExecutionException e) {
					LOGGER.warning(heightMessage + "could not set up a next waker, probably because the node is shutting down");
				}
			}
		}

		private void taskBody(long millisecondsToWait) {
			try {
				Thread.sleep(millisecondsToWait);
				endOfWaitingPeriod.release();
			}
			catch (InterruptedException e) {
				// we avoid throwing the exception, since it would result in an ugly warning message in the logs, since a task has been interrupted...
				// but interruption for this task is in most cases expected: it means that it has been cancelled in {@link #turnOff()} since
				// the head of the blockchain changed and it is better to restart mining on top of it
				Thread.currentThread().interrupt();
			}
		}

		private synchronized void turnOff() {
			if (future != null)
				future.cancel(true);
		}
	}
}
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

import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.mokamint.application.api.Application;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.ApplicationTimeoutException;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionRejectedException;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.Mempool.TransactionEntry;
import io.mokamint.nonce.api.Deadline;

/**
 * A task that executes transactions taken from a queue.
 * It works while a block mining task waits for a deadline.
 * Once that deadline expires, all transactions executed up to the moment
 * get added to the freshly mined block.
 */
public class TransactionsExecutionTask implements Task {

	/**
	 * The node for which transactions are being executed.
	 */
	private final LocalNodeImpl node;

	/**
	 * The block over which the transactions are being executed.
	 * That is, the initial state of the execution is the final state after this block.
	 */
	private final Block previous;

	/**
	 * The application running in the node executing the transactions.
	 */
	private final Application app;

	/**
	 * The source of transactions to execute. It is guaranteed that these transactions
	 * are different from those contained in the blockchain from {@link #previous}
	 * towards the genesis block. It is guaranteed also that these transactions pass
	 * the {@link Application#checkTransaction(Transaction)} test.
	 */
	private final Source source;

	/**
	 * The transactions that have been successfully executed up to now, in order of execution.
	 */
	private final List<Transaction> successfullyDeliveredTransactions = new ArrayList<>();

	/**
	 * The transactions that have been executed with this executor but whose delivery failed
	 * with a {@link TransactionRejectedException}.
	 */
	private final Set<Transaction> rejectedTransactions = new HashSet<>();

	/**
	 * The maximal size allowed for the transactions' table of a block. This task
	 * ensures that the {@link #successfullyDeliveredTransactions} have a cumulative size that is never
	 * larger than this constant.
	 */
	private final long maxSize;

	/**
	 * The {@link #app} identifier of the transactions' execution performed by this task.
	 */
	private final int id;

	private final CountDownLatch done = new CountDownLatch(1);

	private final Object stopLock = new Object();

	private volatile Future<?> future;

	private final static Logger LOGGER = Logger.getLogger(TransactionsExecutionTask.class.getName());

	 /**
	  * A source of transactions to execute.
	  */
	public interface Source {
	
		/**
		 * Takes a transaction to execute. It blocks to wait for new transactions
		 * if this source is currently empty.
		 * 
		 * @return the transaction entry read from this source
		 * @throws InterruptedException if the current thread is interrupted while blocked
		 *                              waiting for a new transaction to arrive
		 */
		TransactionEntry take() throws InterruptedException;
	}

	/**
	 * Creates a transaction execution task.
	 * 
	 * @param node the node the task is working for
	 * @param source the source of the transactions to execute
	 * @param previous the block over which the execution is performed
	 * @param creationTimeOfPrevious the creation time of {@code previous}
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws ApplicationTimeoutException if the application timed out
	 * @throws ClosedNodeException if the node is already closed
	 * @throws ClosedApplicationException  if the application is already closed
	 * @throws UnknownStateException if the state at the end of {@code previous} is not available
	 */
	public TransactionsExecutionTask(LocalNodeImpl node, Source source, Block previous, LocalDateTime creationTimeOfPrevious) throws InterruptedException, ApplicationTimeoutException, ClosedNodeException, UnknownStateException, ClosedApplicationException {
		this.node = node;
		this.previous = previous;
		this.maxSize = node.getConfig().getMaxBlockSize();
		this.app = node.getApplication();
		this.source = source;

		try {
			this.id = app.beginBlock(previous.getDescription().getHeight() + 1, creationTimeOfPrevious, previous.getStateId());
		}
		catch (TimeoutException e) {
			throw new ApplicationTimeoutException(e);
		}
	}

	public void start() throws TaskRejectedExecutionException {
		future = node.submit(this, "transactions execution over block " + previous.getHexHash());
	}

	public void stop() {
		synchronized (stopLock) {
			future.cancel(true);
		}
	}

	@Override
	public void body() {
		long sizeUpToNow = 0L;

		try {
			// infinite loop: this task is expected to be interrupted by the mining task that has spawned it
			while (true)
				sizeUpToNow = processNextTransaction(source.take(), sizeUpToNow);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			// no warning log: interruption is the standard way of terminating this task
		}
		catch (ApplicationTimeoutException | MisbehavingApplicationException | ClosedApplicationException e) {
			LOGGER.warning("mining: transactions execution stops here because of an application problem: " + e.getMessage());
		}
		finally {
			// this allows to commit or abort the execution in the database of the application
			// (if any) and to access the set of processed transactions
			done.countDown();
		}
	}

	/**
	 * Waits for this task to terminate and yields the block including the transactions processed by this task,
	 * on top of {@link #previous}, once the mining task has found a deadline.
	 * 
	 * @param deadline the deadline found by the mining task during the execution of the transactions
	 * @return the block
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 * @throws MisbehavingApplicationException if the application is misbehaving
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws SignatureException if the block could not be signed with the key of the node
	 * @throws InvalidKeyException if the key of the node is invalid
	 */
	public NonGenesisBlock getBlock(Deadline deadline) throws InterruptedException, ApplicationTimeoutException, MisbehavingApplicationException, ClosedApplicationException, InvalidKeyException, SignatureException {
		done.await();

		byte[] finalStateId;

		try {
			finalStateId = app.endBlock(id, deadline);
		}
		catch (TimeoutException e) {
			throw new ApplicationTimeoutException(e);
		}
		catch (UnknownGroupIdException e) {
			throw new MisbehavingApplicationException(e);
		}

		return Blocks.of(previous.getNextBlockDescription(deadline), successfullyDeliveredTransactions.stream(), finalStateId, node.getKeys().getPrivate());
	}

	/**
	 * Waits for this task to terminate and commits the final state of the execution
	 * of its processed transactions, in the database of the application (if any).
	 * 
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 * @throws MisbehavingApplicationException if the node is misbehaving if the application is misbehaving
	 * @throws ClosedApplicationException if the application is already closed
	 */
	void commitBlock() throws InterruptedException, ApplicationTimeoutException, MisbehavingApplicationException, ClosedApplicationException {
		done.await();

		try {
			app.commitBlock(id);
		}
		catch (TimeoutException e) {
			throw new ApplicationTimeoutException(e);
		}
		catch (UnknownGroupIdException e) {
			throw new MisbehavingApplicationException(e);
		}
	}

	/**
	 * Waits for this task to terminate and aborts the execution of its processed transactions,
	 * so that it does not modify the database of the application (if any).
	 * 
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws ApplicationTimeoutException if the application did not provide an answer in time
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws MisbehavingApplicationException if the application is misbehaving
	 */
	void abortBlock() throws InterruptedException, ApplicationTimeoutException, ClosedApplicationException, MisbehavingApplicationException {
		try {
			done.await();
		}
		finally {
			try {
				app.abortBlock(id);
			}
			catch (TimeoutException e) {
				throw new ApplicationTimeoutException(e);
			}
			catch (UnknownGroupIdException e) {
				throw new MisbehavingApplicationException(e);
			}
		}
	}

	private long processNextTransaction(TransactionEntry next, long sizeUpToNow) throws InterruptedException, ApplicationTimeoutException, ClosedApplicationException, MisbehavingApplicationException {
		if (Thread.currentThread().isInterrupted())
			throw new InterruptedException("Interrupted");

		var tx = next.getTransaction();

		// the following might actually occur if a transaction arrives during the execution of this task
		// and it was already processed with this task
		if (!successfullyDeliveredTransactions.contains(tx) && !rejectedTransactions.contains(tx)) {
			int txSize = tx.size();

			// if the following condition does not hold, the transaction is not included in the block that we are mining
			// and disappears from the mempool of this object; this does not mean that it is lost, since
			// it remains in the mempool of the node and will have a chance to be selected later at the next block(s)
			if (sizeUpToNow + txSize <= maxSize) {
				// synchronization guarantees that requests to stop the execution
				// leave the transactions list aligned with the state of the application
				synchronized (stopLock) {
					try {
						app.deliverTransaction(id, tx);
					}
					catch (TransactionRejectedException e) {
						// if tx is rejected, then it is just ignored
						LOGGER.warning("mining: delivery of transaction " + next + " rejected: " + e.getMessage());
						// we also remove the transaction from the mempool of the node
						node.remove(next);
						rejectedTransactions.add(tx);
						return sizeUpToNow;
					}
					catch (UnknownGroupIdException e) {
						throw new MisbehavingApplicationException(e);
					}
					catch (TimeoutException e) {
						throw new ApplicationTimeoutException(e);
					}

					successfullyDeliveredTransactions.add(tx);
				}

				return sizeUpToNow + txSize;
			}
		}

		return sizeUpToNow;
	}
}
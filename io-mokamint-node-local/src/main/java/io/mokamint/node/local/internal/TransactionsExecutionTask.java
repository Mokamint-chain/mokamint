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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.Immutable;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.Mempool.TransactionEntry;
import io.mokamint.nonce.api.Deadline;

/**
 * A task that executes the transactions taken from a queue.
 * It works while a block mining task waits for a deadline.
 * Once that deadline expires, all transactions executed by this task
 * can be added to the freshly mined block.
 */
public class TransactionsExecutionTask implements Task {

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
	 * The transactions that have been executed up to now, in order of execution.
	 */
	private final List<Transaction> transactions = new ArrayList<>();

	/**
	 * The maximal size allowed for the transactions' table of a block. This task
	 * ensures that the {@link #transactions} have a cumulative size that is never
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

	/**
	 * True if and only if at least a transaction delivery failed.
	 */
	private volatile boolean deliveryFailed;

	private final static Logger LOGGER = Logger.getLogger(TransactionsExecutionTask.class.getName());

	public TransactionsExecutionTask(LocalNodeImpl node, Source source, Block previous) throws DatabaseException, ClosedDatabaseException, UnknownStateException, TimeoutException, InterruptedException, ApplicationException {
		this.node = node;
		this.previous = previous;
		this.maxSize = node.getConfig().getMaxBlockSize();
		this.app = node.getApplication();
		this.source = source;
		this.id = app.beginBlock(previous.getDescription().getHeight() + 1, previous.getStateId(), node.getBlockchain().creationTimeOf(previous));
	}

	public void start() throws RejectedExecutionException {
		this.future = node.scheduleTransactionExecutor(this);
	}

	public void stop() {
		synchronized (stopLock) {
			future.cancel(true);
		}
	}

	/**
	 * Yields the block over which the transactions are executed.
	 * 
	 * @return the block
	 */
	public Block getPrevious() {
		return previous;
	}

	@Override
	public void body() throws ApplicationException, TimeoutException {
		long sizeUpToNow = 0L;

		try {
			// infinite loop: this task is expected to be interrupted by the mining task that has spawned it
			while (!Thread.currentThread().isInterrupted())
				sizeUpToNow = processNextTransaction(source.take(), sizeUpToNow);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			// we do not throw the exception further but rather stop working:
			// interruption is the standard way of terminating this task
		}
		finally {
			// this allows to commit or abort the execution in the database of the application
			// (if any) and to access the set of processed transactions
			done.countDown();
		}
	}

	/**
	 * Waits for this task to terminate and yields the transactions processed by this task,
	 * once the mining task has found a deadline.
	 * 
	 * @param deadline the deadline found by the mining task during the execution of the transactions
	 * @return the processed transactions, if any; this might be missing if some transaction delivery failed
	 * @throws InterruptedException if the current thread is interrupted while waiting for the termination of this task
	 *                              or for the application
	 * @throws TimeoutException if the application did not provide an answer in time
	 * @throws ApplicationException if the application is misbehaving
	 */
	public Optional<ProcessedTransactions> getProcessedTransactions(Deadline deadline) throws InterruptedException, TimeoutException, ApplicationException {
		done.await();

		if (deliveryFailed)
			return Optional.empty();
		else
			return Optional.of(new ProcessedTransactions(transactions, app.endBlock(id, deadline)));
	}

	/**
	 * Waits for this task to terminate and commits the final state of the execution
	 * of its processed transactions, in the database of the application (if any).
	 * 
	 * @throws InterruptedException if the current thread is interrupted while waiting for the termination of this task
	 *                              or for the application
	 * @throws TimeoutException if the application did not provide an answer in time
	 * @throws ApplicationException if the application is misbehaving
	 */
	public void commitBlock() throws InterruptedException, TimeoutException, ApplicationException {
		done.await();
		app.commitBlock(id);
	}

	/**
	 * Waits for this task to terminate and aborts the execution of its processed transactions,
	 * so that it does not modify the database of the application (if any).
	 * 
	 * @throws InterruptedException if the current thread is interrupted while waiting for the termination of this task
	 *                              or for the application
	 * @throws TimeoutException if the application did not provide an answer in time
	 * @throws ApplicationException if the application is misbehaving
	 */
	public void abortBlock() throws InterruptedException, TimeoutException, ApplicationException {
		done.await();
		app.abortBlock(id);
	}

	private long processNextTransaction(TransactionEntry next, long sizeUpToNow) throws TimeoutException, InterruptedException, ApplicationException {
		var tx = next.getTransaction();

		if (transactions.contains(tx))
			// this might actually occur if a transaction arrives during the execution of this task
			// and it was already processed with this task
			return sizeUpToNow;

		int txSize = tx.size();
		if (sizeUpToNow + txSize <= maxSize) {
			try {
				// synchronization guarantees that requests to stop the execution
				// leave the transactions list aligned with the state of the application 
				synchronized (stopLock) {
					if (Thread.currentThread().isInterrupted())
						throw new InterruptedException("Interrupted");

					try {
						app.deliverTransaction(tx, id);
					}
					catch (TimeoutException | InterruptedException | ApplicationException | RuntimeException e) {
						deliveryFailed = true;
						throw e;
					}

					transactions.add(tx);
				}
				sizeUpToNow += txSize;
			}
			catch (RejectedTransactionException e) {
				// if tx is rejected by deliverTransaction, then it is just dropped
				LOGGER.log(Level.WARNING, "delivery of transaction " + next + " rejected: " + e.getMessage());
			}
		}

		return sizeUpToNow;
	}

	/**
	 * A sequence of transactions processed with an executor. It is guaranteed that
	 * they are all different, they are different from those in blockchain from
	 * {@link TransactionsExecutionTask#previous} towards the genesis block, they
	 * all pass the {@link Application#checkTransaction(Transaction)} test and they
	 * all lead to a successful {@link Application#deliverTransaction(Transaction, int)}
	 * execution. Moreover, it is guaranteed that {@link ProcessedTransactions#stateHash}
	 * is the hash of the state resulting after the execution of
	 * {@link ProcessedTransactions#transactions}, in order, from the final state
	 * of {@link TransactionsExecutionTask#previous}, including potential coinbase
	 * transactions executed at the end of all transactions (if the application adds them
	 * inside its {@link Application#endBlock(int, Deadline)} method).
	 */
	@Immutable
	public static class ProcessedTransactions {
		private final Transaction[] transactions;
		private final byte[] stateHash;

		private ProcessedTransactions(List<Transaction> transactions, byte[] stateHash) {
			this.transactions = transactions.toArray(Transaction[]::new);
			this.stateHash = stateHash.clone();
		}

		/**
		 * Yields the transactions processed with an executor, in order.
		 * 
		 * @return the transactions
		 */
		public Stream<Transaction> getTransactions() {
			return Stream.of(transactions);
		}

		/**
		 * Yields the final state resulting at the end of the execution of the transactions,
		 * including potential coinbase transactions, if the application uses them.
		 * 
		 * @return the final state
		 */
		public byte[] getStateId() {
			return stateHash.clone();
		}
	}
}
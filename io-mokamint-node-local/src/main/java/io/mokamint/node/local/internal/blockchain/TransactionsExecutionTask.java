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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.Immutable;
import io.mokamint.application.api.Application;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.mempool.Mempool.TransactionEntry;
import io.mokamint.nonce.api.Deadline;

/**
 * A task that executes the transactions taken from a queue.
 * It works while a block mining task looks for a deadline.
 * Once the deadline expires, all transactions executed by this task
 * can be added to the new block.
 */
public class TransactionsExecutionTask implements Task {

	public interface Source {
		TransactionEntry take() throws InterruptedException;
	}

	private final Application app;
	private final Source source;

	/**
	 * The transactions that have been executed up to now.
	 */
	private final List<Transaction> transactions = new ArrayList<>();

	private final long maxSize;

	private final int id;

	private final CountDownLatch done = new CountDownLatch(1);

	private final static Logger LOGGER = Logger.getLogger(TransactionsExecutionTask.class.getName());

	public TransactionsExecutionTask(LocalNodeImpl node, Source source, Block previous) throws DatabaseException, ClosedDatabaseException {
		this.maxSize = node.getConfig().getMaxBlockSize();
		this.app = node.getApplication();
		this.source = source;
		this.id = app.beginBlock(previous.getDescription().getHeight() + 1, previous.getStateHash(), node.getBlockchain().creationTimeOf(previous));
	}

	@Override
	public void body() {
		long sizeUpToNow = 0L;

		try {
			while (!Thread.currentThread().isInterrupted())
				sizeUpToNow = processNextTransaction(source.take(), sizeUpToNow);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			done.countDown();
		}
	}

	public ProcessedTransactions getProcessedTransactions(Deadline deadline) throws InterruptedException {
		done.await();
		var finalStateHash = app.endBlock(id, deadline);
		return new ProcessedTransactions(transactions, finalStateHash);
	}

	public void commitBlock() throws InterruptedException {
		done.await();
		app.commitBlock(id);
	}

	public void abortBlock() throws InterruptedException {
		done.await();
		app.abortBlock(id);
	}

	private long processNextTransaction(TransactionEntry next, long sizeUpToNow) {
		var tx = next.getTransaction();

		if (transactions.contains(tx))
			// this might actually occur if a transaction arrives during the execution of this task,
			// which was already processed with this task
			return sizeUpToNow;

		int txSize = tx.size();
		if (sizeUpToNow + txSize <= maxSize) {
			try {
				app.deliverTransaction(tx, id);
				transactions.add(tx);
				sizeUpToNow += txSize;
			}
			catch (RejectedTransactionException e) {
				LOGGER.log(Level.WARNING, "delivery of transaction " + next + " rejected: " + e.getMessage());
			}
			catch (RuntimeException e) {
				LOGGER.log(Level.SEVERE, "delivery of transaction " + next + " failed", e);
			}
		}

		return sizeUpToNow;
	}

	@Immutable
	public static class ProcessedTransactions {
		private final Transaction[] transactions;
		private final byte[] stateHash;

		private ProcessedTransactions(List<Transaction> transactions, byte[] stateHash) {
			this.transactions = transactions.toArray(Transaction[]::new);
			this.stateHash = stateHash.clone();
		}

		public Stream<Transaction> getTransactions() {
			return Stream.of(transactions);
		}

		public byte[] getStateHash() {
			return stateHash.clone();
		}
	}
}
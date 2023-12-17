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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.Immutable;
import io.mokamint.application.api.Application;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.mempool.Mempool.TransactionEntry;

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

	private final LocalNodeImpl node;
	private final Application app;
	private final Source source;

	/**
	 * The transactions that have been executed up to now.
	 */
	@GuardedBy("itself")
	private final List<Transaction> transactions = new ArrayList<>();

	@GuardedBy("this.transactions")
	private byte[] stateHash;

	private final static Logger LOGGER = Logger.getLogger(TransactionsExecutionTask.class.getName());

	public TransactionsExecutionTask(LocalNodeImpl node, Source source, byte[] initialStateHash) {
		this.node = node;
		this.app = node.getApplication();
		this.source = source;
		this.stateHash = initialStateHash.clone();
	}

	@Override
	public void body() {
		new Body();
	}

	private class Body {
		private TransactionEntry next;
		private long sizeUpToNow = 0L;
		private final int id = counter.getAndIncrement();
		private final static AtomicInteger counter = new AtomicInteger();

		private Body() {
			app.beginBlock(id);

			try {
				while (!Thread.currentThread().isInterrupted()) {
					moveToNextTransaction();
					processNextTransaction();
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			finally {
				app.endBlock(id);
			}
		}

		private void moveToNextTransaction() throws InterruptedException {
			next = source.take();
		}

		private void processNextTransaction() {
			var tx = next.getTransaction();
			
			synchronized (transactions) {
				if (transactions.contains(tx))
					// this might actually occur if a transaction arrives during the execution of this task,
					// which was already processed with this task
					return;
			}

			int txSize = tx.size();
			if (sizeUpToNow + txSize <= node.getConfig().getMaxBlockSize()) {
				try {
					// TODO: define class StateHash
					byte[] currentStateHash;
					synchronized (transactions) {
						currentStateHash = stateHash;
					}

					byte[] nextStateHash = app.deliverTransaction(tx, id, currentStateHash);

					synchronized (transactions) {
						transactions.add(tx);
						stateHash = nextStateHash;
					}

					sizeUpToNow += txSize;
				}
				catch (RejectedTransactionException e) {
					LOGGER.log(Level.WARNING, "delivery of transaction " + next + " rejected: " + e.getMessage());
				}
				catch (RuntimeException e) {
					LOGGER.log(Level.SEVERE, "delivery of transaction " + next + " failed", e);
				}
			}
		}
	}

	public ProcessedTransactions getProcessedTransactions() {
		synchronized (transactions) {
			return new ProcessedTransactions(transactions, stateHash);
		}
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
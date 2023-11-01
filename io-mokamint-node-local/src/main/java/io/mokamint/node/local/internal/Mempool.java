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

/**
 * 
 */
package io.mokamint.node.local.internal;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.mokamint.application.api.Application;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;

/**
 * The mempool of a Mokamint node. It contains transactions that are available
 * to be processed and included in the blocks of the blockchain.
 */
@ThreadSafe
public class Mempool {

	/**
	 * The node having this mempool.
	 */
	private final LocalNodeImpl node;

	/**
	 * The application running in the node.
	 */
	private final Application app;

	/**
	 * The hasher of the transactions.
	 */
	private final Hasher<Transaction> hasher;

	/**
	 * The container of the transactions inside this mempool.
	 */
	@GuardedBy("itself")
	private final SortedSet<TransactionEntry> mempool = new TreeSet<>();

	private final static long MAX_MEMPOOL_SIZE = 100_000L;

	/**
	 * Creates a mempool for the given node.
	 * 
	 * @param node the node
	 */
	public Mempool(LocalNodeImpl node) {
		this.node = node;
		this.app = node.getApplication();

		try {
			this.hasher = HashingAlgorithms.sha256().getHasher(Transaction::toByteArray);
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Schedules the addition of the given transaction to this mempool.
	 * This method returns immediately. Eventually, the transaction will be
	 * checked for validity and, if valid, added to this mempool.
	 * 
	 * @param transaction the transaction to add
	 */
	public void scheduleAddition(Transaction transaction) {
		
		class AddToMempoolTask implements Task {

			@Override
			public void body() {
				var entry = new TransactionEntry(transaction, 0L, hasher);

				synchronized (mempool) {
					if (mempool.size() < MAX_MEMPOOL_SIZE)
						mempool.add(entry);
				}
			}

			@Override
			public String toString() {
				return "transaction addition to the mempool";
			}

			@Override
			public String logPrefix() {
				return "[mempool] ";
			}
		}

		node.submit(new AddToMempoolTask());
	}

	/**
	 * An entry in the container of the transactions in this mempool.
	 * It contains the transaction itself and extra information about the transaction.
	 */
	private static class TransactionEntry implements Comparable<TransactionEntry> {
		private final Transaction transaction;
		private final long priority;
		private final byte[] hash;

		private TransactionEntry(Transaction transaction, long priority, Hasher<Transaction> hasher) {
			this.transaction = transaction;
			this.priority = priority;
			this.hash = hasher.hash(transaction);
		}

		@Override
		public int compareTo(TransactionEntry other) {
			int diff = Long.compare(priority, other.priority);
			if (diff != 0)
				return diff;
			else
				return Arrays.compare(hash, other.hash);
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof TransactionEntry te && Arrays.equals(hash, te.hash);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(hash);
		}

		@Override
		public String toString() {
			return Hex.toHexString(hash);
		}
	}
}
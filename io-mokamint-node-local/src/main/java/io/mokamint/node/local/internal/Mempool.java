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

import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.mokamint.application.api.Application;
import io.mokamint.node.TransactionInfos;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionInfo;

/**
 * The mempool of a Mokamint node. It contains transactions that are available
 * to be processed and included in the blocks of the blockchain.
 */
@ThreadSafe
public class Mempool {

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
		this.app = node.getApplication();
		this.hasher = node.getConfig().getHashingForTransactions().getHasher(Transaction::toByteArray);
	}

	/**
	 * Checks the validity of the given transaction and, if valid, adds it to this mempool.
	 * 
	 * @param transaction the transaction to add
	 * @return information about the transaction that has been added
	 */
	public TransactionInfo add(Transaction transaction) throws RejectedTransactionException {
		byte[] hash = hasher.hash(transaction);
		if (!app.checkTransaction(transaction))
			throw new RejectedTransactionException("Invalid transaction " + Hex.toHexString(hash));

		long priority;

		try {
			priority = app.getPriority(transaction);
		}
		catch (RejectedTransactionException e) {
			throw new RejectedTransactionException("Cannot compute the priority of transaction " + Hex.toHexString(hash), e);
		}

		var entry = new TransactionEntry(transaction, priority, hash);

		synchronized (mempool) {
			if (mempool.size() < MAX_MEMPOOL_SIZE) { // TODO
				mempool.add(entry);
				return entry.getInfo();
			}
			else
				throw new RejectedTransactionException("Mempool overflow: all its " + MAX_MEMPOOL_SIZE + " slots are full");
		}
	}

	/**
	 * An entry in the container of the transactions in this mempool.
	 * It contains the transaction itself and extra information about the transaction.
	 */
	private static class TransactionEntry implements Comparable<TransactionEntry> {
		private final Transaction transaction;
		private final long priority;
		private final byte[] hash;

		private TransactionEntry(Transaction transaction, long priority, byte[] hash) {
			this.transaction = transaction;
			this.priority = priority;
			this.hash = hash;
		}

		private TransactionInfo getInfo() {
			return TransactionInfos.of(hash, priority);
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
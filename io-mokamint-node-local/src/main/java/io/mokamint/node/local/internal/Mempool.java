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

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.node.MempoolEntries;
import io.mokamint.node.MempoolInfos;
import io.mokamint.node.MempoolPortions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.api.MempoolPortion;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionRejectedException;
import io.mokamint.node.local.ApplicationTimeoutException;

/**
 * The mempool of a Mokamint node. It contains transactions that are available
 * to be processed and eventually included in the new blocks mined for a blockchain.
 * Transactions are kept and processed in decreasing order of priority.
 */
@ThreadSafe
public class Mempool {

	/**
	 * The node having this mempool.
	 */
	private final LocalNodeImpl node;

	/**
	 * The blockchain of the node having this mempool.
	 */
	private final Blockchain blockchain;

	/**
	 * The application running in the node having this mempool.
	 */
	private final Application app;

	/**
	 * The hasher of the transactions.
	 */
	private final Hasher<Transaction> hasher;

	/**
	 * The base block of the mempool: the transactions inside {@link #mempool}
	 * have arrived after the creation of this block. If missing, the transactions
	 * have arrived after the creation of the blockchain itself.
	 */
	@GuardedBy("this.mempool")
	private Optional<Block> base;

	/**
	 * The container of the transactions inside this mempool. They are kept ordered by decreasing priority.
	 */
	@GuardedBy("itself")
	private final SortedSet<TransactionEntry> mempool;

	private final static Logger LOGGER = Logger.getLogger(Mempool.class.getName());

	/**
	 * Creates a mempool for the given node, initially empty and without a base.
	 * 
	 * @param node the node
	 */
	public Mempool(LocalNodeImpl node) {
		this.node = node;
		this.blockchain = node.getBlockchain();
		this.app = node.getApplication();
		this.hasher = node.getConfigInternal().getHashingForTransactions().getHasher(io.mokamint.node.api.Transaction::toByteArray);
		this.base = Optional.empty();
		this.mempool = new TreeSet<>(Comparator.reverseOrder()); // decreasing priority
	}

	/**
	 * Creates a clone of the given mempool.
	 * 
	 * @param parent the mempool to clone
	 */
	public Mempool(Mempool parent) {
		this.node = parent.node;
		this.blockchain = parent.blockchain;
		this.app = parent.app;
		this.hasher = parent.hasher;

		synchronized (parent.mempool) {
			this.base = parent.base;
			this.mempool = new TreeSet<>(parent.mempool);
		}
	}

	/**
	 * Sets a new base for this mempool. Calling P the highest predecessor block of both
	 * the current base and the {@code newBase}, this method will add all transactions
	 * in the blocks from the current base to P (excluded) back in this mempool and remove
	 * all transactions in the blocks from P (excluded) to {@code newBase} in this mempool.
	 * 
	 * @param newBase the new base that must be set for this mempool
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 * @throws MisbehavingApplicationException if the application is misbehaving
	 * @throws ClosedApplicationException if the application is already closed
	 */
	public void rebaseAt(Block newBase) throws InterruptedException, ApplicationTimeoutException, ClosedApplicationException, MisbehavingApplicationException {
		synchronized (mempool) {
			blockchain.rebase(this, newBase);
		}
	}

	/**
	 * Adds the given transaction to this mempool, after checking its validity.
	 * 
	 * @param transaction the transaction to add
	 * @return the transaction entry added to the mempool
	 * @throws TransactionRejectedException if the transaction has been rejected; this happens,
	 *                                      for instance, if the application considers the
	 *                                      transaction as invalid or if its priority cannot be computed
	 *                                      or if the transaction is already contained in the blockchain or mempool
	 * @throws NodeException if the node is misbehaving
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws ApplicationTimeoutException if the application connected to the Mokamint node is unresponsive
	 */
	public TransactionEntry add(Transaction transaction) throws TransactionRejectedException, NodeException, InterruptedException, ApplicationTimeoutException {
		try {
			app.checkTransaction(transaction);
			var entry = mkTransactionEntry(transaction);
			int maxSize = node.getConfig().getMempoolSize();

			synchronized (mempool) {
				if (base.isPresent() && blockchain.getTransactionAddress(base.get(), entry.hash).isPresent())
					// the transaction was already in blockchain
					throw new TransactionRejectedException("Repeated transaction " + entry);
				else if (mempool.contains(entry))
					// the transaction was already in the mempool
					throw new TransactionRejectedException("Repeated transaction " + entry);
				else if (mempool.size() >= maxSize)
					throw new TransactionRejectedException("Cannot add transaction " + entry + ": all " + maxSize + " slots of the mempool are full");
				else
					mempool.add(entry);
			}

			LOGGER.info("mempool: added transaction " + entry);
			node.onAdded(transaction);

			return entry;
		}
		catch (ClosedApplicationException e) { // TODO
			throw new NodeException(e);
		}
		catch (TimeoutException e) {
			throw new ApplicationTimeoutException(e);
		}
	}

	public TransactionEntry mkTransactionEntry(Transaction transaction) throws TransactionRejectedException, ClosedApplicationException, ApplicationTimeoutException, InterruptedException {
		long priority;

		try {
			priority = app.getPriority(transaction);
		}
		catch (TimeoutException e) {
			throw new ApplicationTimeoutException(e);
		}

		return new TransactionEntry(transaction,  priority, hasher.hash(transaction));
	}

	public void remove(TransactionEntry entry) {
		synchronized (mempool) {
			mempool.remove(entry);
		}
	}

	/**
	 * Performs an action for each transaction in this mempool, in decreasing priority order.
	 * 
	 * @param the action
	 */
	public void forEachTransaction(Consumer<TransactionEntry> action) {
		synchronized (mempool) {
			mempool.stream().forEachOrdered(action);
		}
	}

	/**
	 * Yields information about this mempool.
	 * 
	 * @return the information
	 */
	public MempoolInfo getInfo() {
		long size;
		
		synchronized (mempool) {
			size = mempool.size();
		}
	
		return MempoolInfos.of(size);
	}

	/**
	 * Yields a portion of this mempool.
	 * 
	 * @param start the initial entry slot to return
	 * @param count the maximal number of slots to return
	 * @return the portion from {@code start} (included) to {@code start + length} (excluded)
	 */
	public MempoolPortion getPortion(int start, int count) {
		if (start < 0 || count <= 0)
			return MempoolPortions.of(Stream.empty());

		synchronized (mempool) {
			return MempoolPortions.of(mempool.stream().skip(start).limit(count).map(TransactionEntry::toMempoolEntry));
		}
	}

	Optional<Block> getBase() {
		synchronized (mempool) {
			return base;
		}
	}

	void update(Block newBase, Stream<TransactionEntry> toAdd, Stream<TransactionEntry> toRemove) {
		synchronized (mempool) {
			toAdd.forEach(mempool::add);
			toRemove.forEach(mempool::remove);
			this.base = Optional.of(newBase);
		}
	}

	/**
	 * An entry in the mempool. It contains the transaction itself, its priority and its hash.
	 */
	public final static class TransactionEntry implements Comparable<TransactionEntry> {
		private final Transaction transaction;
		private final long priority;
		private final byte[] hash;
	
		private TransactionEntry(Transaction transaction, long priority, byte[] hash) {
			this.transaction = transaction;
			this.priority = priority;
			this.hash = hash;
		}

		/**
		 * Yields the transaction inside this entry.
		 * 
		 * @return the transaction
		 */
		public Transaction getTransaction() {
			return transaction;
		}
	
		/**
		 * Yields the hash of the transaction inside this entry.
		 * 
		 * @return the hash
		 */
		public byte[] getHash() {
			return hash.clone();
		}
	
		@Override
		public int compareTo(TransactionEntry other) {
			int diff = Long.compare(priority, other.priority);
			return diff != 0 ? diff : Arrays.compare(hash, other.hash);
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

		/**
		 * Yields a {@link MempoolEntry} derived from this, by projecting on hash and priority.
		 * 
		 * @return the {@link MempoolEntry}
		 */
		public MempoolEntry toMempoolEntry() {
			return MempoolEntries.of(hash, priority);
		}
	}
}
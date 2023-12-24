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

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.mokamint.application.api.Application;
import io.mokamint.node.MempoolEntries;
import io.mokamint.node.MempoolInfos;
import io.mokamint.node.MempoolPortions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.api.MempoolPortion;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;

/**
 * The mempool of a Mokamint node. It contains transactions that are available
 * to be processed and then included in the new blocks mined for a blockchain.
 * Transactions are kept and processed in decreasing order of priority.
 */
@ThreadSafe
public class Mempool {

	/**
	 * The blockchain of the node having this mempool.
	 */
	private final Blockchain blockchain;

	/**
	 * The application running in the node having this mempool.
	 */
	private final Application app;

	/**
	 * The maximal size of the mempool (number of slots).
	 */
	private final int maxSize;

	/**
	 * The hasher of the transactions.
	 */
	private final Hasher<Transaction> hasher;

	/**
	 * The hash of the base block of the mempool: the transactions inside {@link #mempool}
	 * have arrived after the creation of this block. If missing, the transactions
	 * have arrived after the creation of the blockchain itself.
	 */
	@GuardedBy("this.mempool")
	private Optional<byte[]> base;

	/**
	 * The container of the transactions inside this mempool. They are kept ordered
	 * by decreasing priority.
	 */
	@GuardedBy("itself")
	private final SortedSet<TransactionEntry> mempool;

	/**
	 * The container of the transactions inside this mempool, as a list. This is used as 
	 * a snapshot of {@link #mempool}, for optimization of the {@link #getPortion(int, int)} method.
	 */
	@GuardedBy("this.mempool")
	private List<TransactionEntry> mempoolAsList;

	private final static Logger LOGGER = Logger.getLogger(Mempool.class.getName());

	/**
	 * Creates a mempool for the given node, initially empty and without a base.
	 * 
	 * @param node the node
	 */
	public Mempool(LocalNodeImpl node) {
		this.blockchain = node.getBlockchain();
		this.app = node.getApplication();
		this.maxSize = node.getConfig().getMempoolSize();
		this.hasher = node.getHasherForTransactions();
		this.base = Optional.empty();
		this.mempool = new TreeSet<>(Comparator.reverseOrder()); // decreasing priority
	}

	/**
	 * Creates a clone of the given mempool.
	 * 
	 * @param parent the mempool to clone
	 */
	public Mempool(Mempool parent) {
		this.blockchain = parent.blockchain;
		this.app = parent.app;
		this.maxSize = parent.maxSize;
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
	 * @throws NoSuchAlgorithmException if some block in the blockchain uses refers to an unknown
	 *                                  cryptographic algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public void rebaseAt(byte[] newBase) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		synchronized (mempool) {
			var oldBase = base.orElse(null);
			base = Optional.of(newBase);

			if (oldBase == null)
				// if the base is empty, there is nothing to add and only to remove
				while (!mempool.isEmpty() && getBlock(newBase) instanceof NonGenesisBlock ngb) {
					removeAll(ngb.getTransactions().collect(Collectors.toCollection(HashSet::new)));
					newBase = ngb.getHashOfPreviousBlock();
				}
			else {
				var oldBlock = getBlock(oldBase);
				var newBlock = getBlock(newBase);
				Set<Transaction> toRemove = new HashSet<>();
				Set<TransactionEntry> toAdd = new HashSet<>();

				while (newBlock.getDescription().getHeight() > oldBlock.getDescription().getHeight()) {
					if (newBlock instanceof NonGenesisBlock ngb) {
						addAllTransactions(toRemove, ngb);
						newBase = ngb.getHashOfPreviousBlock();
						newBlock = getBlock(newBase);
					}
					else
						throw new DatabaseException("The database contains a genesis block " + Hex.toHexString(newBase) + " at height " + newBlock.getDescription().getHeight());
				}

				while (newBlock.getDescription().getHeight() < oldBlock.getDescription().getHeight()) {
					if (oldBlock instanceof NonGenesisBlock ngb) {
						addAllTransactionEntries(toAdd, ngb);
						oldBase = ngb.getHashOfPreviousBlock();
						oldBlock = getBlock(oldBase);
					}
					else
						throw new DatabaseException("The database contains a genesis block " + Hex.toHexString(oldBase) + " at height " + oldBlock.getDescription().getHeight());
				}

				while (!Arrays.equals(newBase, oldBase)) {
					if (newBlock.getDescription().getHeight() == 0 || oldBlock.getDescription().getHeight() == 0)
						throw new DatabaseException("Cannot identify a shared ancestor block between " + Hex.toHexString(oldBase) + " and " + Hex.toHexString(newBase));

					if (newBlock instanceof NonGenesisBlock ngb1) {
						addAllTransactions(toRemove, ngb1);
						newBase = ngb1.getHashOfPreviousBlock();
						newBlock = getBlock(newBase);
					}
					else
						throw new DatabaseException("The database contains a genesis block " + Hex.toHexString(newBase) + " at height " + newBlock.getDescription().getHeight());

					if (oldBlock instanceof NonGenesisBlock ngb2) {
						addAllTransactionEntries(toAdd, ngb2);
						oldBase = ngb2.getHashOfPreviousBlock();
						oldBlock = getBlock(oldBase);
					}
					else
						throw new DatabaseException("The database contains a genesis block " + Hex.toHexString(oldBase) + " at height " + oldBlock.getDescription().getHeight());				
				}

				if (!toAdd.isEmpty() && mempool.addAll(toAdd))
					mempoolAsList = null; // invalidation

				if (!mempool.isEmpty())
					removeAll(toRemove);
			}
		}
	}

	/**
	 * Adds the transaction entries from the given block to the given set.
	 * 
	 * @param set the set where the transactions must be added
	 * @param block the block
	 * @throws DatabaseException if the database is corrupted
	 */
	private void addAllTransactionEntries(Set<TransactionEntry> set, NonGenesisBlock block) throws DatabaseException {
		for (int pos = 0; pos < block.getTransactionsCount(); pos++)
			set.add(intoTransactionEntry(block.getTransaction(pos)));
	}

	/**
	 * Adds the transactions from the given block to the given set.
	 * 
	 * @param set the set where the transactions must be added
	 * @param block the block
	 * @throws DatabaseException if the database is corrupted
	 */
	private void addAllTransactions(Set<Transaction> set, NonGenesisBlock block) throws DatabaseException {
		block.getTransactions().forEach(set::add);
	}

	/**
	 * Expands the given transaction into a transaction entry, by filling its priority and hash.
	 * 
	 * @param transaction the transaction
	 * @return the resulting transaction entry
	 * @throws DatabaseException if the database is corrupted
	 */
	private TransactionEntry intoTransactionEntry(Transaction transaction) throws DatabaseException {
		try {
			return new TransactionEntry(transaction, app.getPriority(transaction), hasher.hash(transaction));
		}
		catch (RejectedTransactionException e) {
			// the database contains a block with a rejected transaction: it should not be there!
			throw new DatabaseException(e);
		}
	}

	private void removeAll(Set<Transaction> toRemove) {
		new HashSet<>(mempool).stream()
			.filter(entry -> toRemove.contains(entry.transaction))
			.forEach(this::remove);
	}

	private void remove(TransactionEntry entry) {
		if (mempool.remove(entry))
			mempoolAsList = null; // invalidation
	}

	private Block getBlock(byte[] hash) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		return blockchain.getBlock(hash).orElseThrow(() -> new DatabaseException("Missing block with hash " + Hex.toHexString(hash)));
	}

	/**
	 * Adds the given transaction to this mempool, after checking its validity.
	 * 
	 * @param transaction the transaction to add
	 * @return the mempool entry where the transaction has been added
	 * @throws RejectedTransactionException if the transaction has been rejected; this happens,
	 *                                      for instance, if the application considers the
	 *                                      transaction as invalid or if its priority cannot be computed
	 *                                      or if the transaction is already contained in the blockchain or mempool
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws NoSuchAlgorithmException if the database contains a block referring to an unknown cryptographic algorithm
	 */
	public MempoolEntry add(Transaction transaction) throws RejectedTransactionException, NoSuchAlgorithmException, ClosedDatabaseException, DatabaseException {
		byte[] hash = hasher.hash(transaction);
		String hexHash = Hex.toHexString(hash);

		try {
			app.checkTransaction(transaction);
		}
		catch (RejectedTransactionException e) {
			throw new RejectedTransactionException("Check failed for transaction " + hexHash + ": " + e.getMessage());
		}

		long priority;

		try {
			priority = app.getPriority(transaction);
		}
		catch (RejectedTransactionException e) {
			throw new RejectedTransactionException("Cannot compute the priority of transaction " + hexHash + ": " + e.getMessage());
		}

		var entry = new TransactionEntry(transaction, priority, hash);

		synchronized (mempool) {
			if (base.isPresent() && blockchain.getTransactionAddress(base.get(), hash).isPresent())
				//the transaction was already in the blockchain
				throw new RejectedTransactionException("Repeated transaction " + hexHash);

			if (mempool.size() < maxSize) {
				if (!mempool.add(entry))
					// the transaction was already in the mempool
					throw new RejectedTransactionException("Repeated transaction " + hexHash);

				mempoolAsList = null; // invalidation
			}
			else
				throw new RejectedTransactionException("Cannot add transaction " + hexHash + ": all " + maxSize + " slots of the mempool are full");
		}

		LOGGER.info("mempool: added transaction " + hexHash);

		return entry.getMempoolEntry();
	}

	/**
	 * Yields the transactions inside this mempool.
	 * 
	 * @return the transactions
	 */
	public Stream<TransactionEntry> getTransactions() {
		return mempool.stream();
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
		if (start < 0L || count <= 0)
			return MempoolPortions.of(Stream.empty());

		List<TransactionEntry> list;

		synchronized (mempool) {
			if (start >= mempool.size())
				return MempoolPortions.of(Stream.empty());

			if (mempoolAsList == null)
				mempoolAsList = new ArrayList<>(mempool);
	
			list = mempoolAsList;
		}

		Stream<MempoolEntry> entries = IntStream.range(start, Math.min(start + count, list.size()))
			.mapToObj(list::get)
			.map(TransactionEntry::getMempoolEntry);

		return MempoolPortions.of(entries);
	}

	/**
	 * An entry in the mempool. It contains the transaction itself, its priority and its hash.
	 */
	public static class TransactionEntry implements Comparable<TransactionEntry> {
		private final Transaction transaction;
		private final long priority;
		private final byte[] hash;

		protected TransactionEntry(Transaction transaction, long priority, byte[] hash) {
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

		/**
		 * Yields a {@link MempoolEntry} derived from this, by projecting on hash and priority.
		 * 
		 * @return the {@link MempoolEntry}
		 */
		private MempoolEntry getMempoolEntry() {
			return MempoolEntries.of(hash, priority);
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
	}
}
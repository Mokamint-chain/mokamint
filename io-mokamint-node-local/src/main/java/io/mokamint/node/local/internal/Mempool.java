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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.node.DatabaseException;
import io.mokamint.node.MempoolEntries;
import io.mokamint.node.MempoolInfos;
import io.mokamint.node.MempoolPortions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.api.MempoolPortion;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionRejectedException;

/**
 * The mempool of a Mokamint node. It contains transactions that are available
 * to be processed and then included in the new blocks mined for a blockchain.
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
	 * The maximal size of the mempool (number of slots).
	 */
	private final int maxSize;

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

	/**
	 * The hashing algorithm for the blocks.
	 */
	private final HashingAlgorithm hashingForBlocks;

	private final static Logger LOGGER = Logger.getLogger(Mempool.class.getName());

	/**
	 * Creates a mempool for the given node, initially empty and without a base.
	 * 
	 * @param node the node
	 */
	public Mempool(LocalNodeImpl node) {
		this.node = node;
		this.blockchain = node.getBlockchain();
		this.hashingForBlocks = node.getConfig().getHashingForBlocks();
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
	protected Mempool(Mempool parent) {
		this.node = parent.node;
		this.blockchain = parent.blockchain;
		this.hashingForBlocks = parent.hashingForBlocks;
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
	 * @throws NodeException if the node is misbehaving
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if the application did not provide an answer in time
	 * @throws ApplicationException if the application is not working properly
	 */
	public void rebaseAt(Block newBase) throws NodeException, DatabaseException, ClosedDatabaseException, TimeoutException, InterruptedException, ApplicationException {
		new RebaseAt(newBase);
	}

	/**
	 * Adds the given transaction to this mempool, after checking its validity.
	 * 
	 * @param transaction the transaction to add
	 * @return the mempool entry where the transaction has been added
	 * @throws TransactionRejectedException if the transaction has been rejected; this happens,
	 *                                      for instance, if the application considers the
	 *                                      transaction as invalid or if its priority cannot be computed
	 *                                      or if the transaction is already contained in the blockchain or mempool
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws NodeException if the node is misbehaving
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if the application did not provide an answer in time
	 * @throws ApplicationException if the application is not working properly
	 */
	public MempoolEntry add(Transaction transaction) throws TransactionRejectedException, NodeException, ClosedDatabaseException, DatabaseException, TimeoutException, InterruptedException, ApplicationException {
		byte[] hash = hasher.hash(transaction);
		String hexHash = Hex.toHexString(hash);
	
		app.checkTransaction(transaction);
		long priority = app.getPriority(transaction);
		var entry = new TransactionEntry(transaction, priority, hash);
	
		synchronized (mempool) {
			if (base.isPresent() && blockchain.getTransactionAddress(base.get(), hash).isPresent())
				//the transaction was already in the blockchain
				throw new TransactionRejectedException("Repeated transaction " + hexHash);
	
			if (mempool.size() < maxSize) {
				if (!mempool.add(entry))
					// the transaction was already in the mempool
					throw new TransactionRejectedException("Repeated transaction " + hexHash);
	
				mempoolAsList = null; // invalidation
			}
			else
				throw new TransactionRejectedException("Cannot add transaction " + hexHash + ": all " + maxSize + " slots of the mempool are full");
		}
	
		LOGGER.info("mempool: added transaction " + hexHash);
		node.onAdded(transaction);
	
		return entry.getMempoolEntry();
	}

	protected void remove(TransactionEntry entry) {
		synchronized (mempool) {
			if (mempool.remove(entry))
				mempoolAsList = null; // invalidation
		}
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

	/**
	 * The algorithm for rebasing this mempool at a given, new base.
	 */
	private class RebaseAt {
		private Block newBlock;
		private Block oldBlock;
		private final Set<Transaction> toRemove = new HashSet<>();
		private final Set<TransactionEntry> toAdd = new HashSet<>();

		private RebaseAt(final Block newBase) throws NodeException, DatabaseException, ClosedDatabaseException, TimeoutException, InterruptedException, ApplicationException {
			this.newBlock = newBase;

			synchronized (mempool) {
				oldBlock = base.orElse(null);

				if (oldBlock == null)
					removeAllTransactionsFromNewBaseToGenesis(); // if the base is empty, there is nothing to add and only to remove
				else {
					// first we move new and old bases to the same height
					while (newBlock.getDescription().getHeight() > oldBlock.getDescription().getHeight())
						markToRemoveAllTransactionsInNewBlockAndMoveItBackwards();

					while (newBlock.getDescription().getHeight() < oldBlock.getDescription().getHeight())
						markToAddAllTransactionsInOldBlockAndMoveItBackwards();

					// then we continue towards the genesis, until they meet
					while (!reachedSharedAncestor()) {
						markToRemoveAllTransactionsInNewBlockAndMoveItBackwards();
						markToAddAllTransactionsInOldBlockAndMoveItBackwards();
					}

					if (!toAdd.isEmpty() && mempool.addAll(toAdd))
						mempoolAsList = null; // invalidation

					if (!mempool.isEmpty())
						removeAll(toRemove);
				}

				base = Optional.of(newBase);
			}
		}

		private boolean reachedSharedAncestor() throws DatabaseException {
			if (newBlock.equals(oldBlock))
				return true;
			else if (newBlock instanceof GenesisBlock || oldBlock instanceof GenesisBlock)
				throw new DatabaseException("Cannot identify a shared ancestor block between " + oldBlock.getHexHash(hashingForBlocks)
					+ " and " + newBlock.getHexHash(hashingForBlocks));
			else
				return false;
		}

		private void markToRemoveAllTransactionsInNewBlockAndMoveItBackwards() throws DatabaseException, NodeException, ClosedDatabaseException {
			if (newBlock instanceof NonGenesisBlock ngb) {
				markAllTransactionsAsToRemove(ngb);
				newBlock = getBlock(ngb.getHashOfPreviousBlock());
			}
			else
				throw new DatabaseException("The database contains a genesis block " + newBlock.getHexHash(hashingForBlocks) + " at height " + newBlock.getDescription().getHeight());
		}

		private void markToAddAllTransactionsInOldBlockAndMoveItBackwards() throws DatabaseException, NodeException, ClosedDatabaseException, TimeoutException, InterruptedException, ApplicationException {
			if (oldBlock instanceof NonGenesisBlock ngb) {
				markAllTransactionsAsToAdd(ngb);
				oldBlock = getBlock(ngb.getHashOfPreviousBlock());
			}
			else
				throw new DatabaseException("The database contains a genesis block " + oldBlock.getHexHash(hashingForBlocks) + " at height " + oldBlock.getDescription().getHeight());
		}

		private void removeAllTransactionsFromNewBaseToGenesis() throws NodeException, DatabaseException, ClosedDatabaseException {
			while (!mempool.isEmpty() && newBlock instanceof NonGenesisBlock ngb) {
				removeAll(ngb.getTransactions().collect(Collectors.toCollection(HashSet::new)));
				newBlock = getBlock(ngb.getHashOfPreviousBlock());
			}
		}

		/**
		 * Adds the transaction entries to those that must be added to the mempool.
		 * 
		 * @param block the block
		 * @throws DatabaseException if the database is corrupted
		 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
		 * @throws TimeoutException if the application did not provide an answer in time
		 * @throws ApplicationException if the application is not working properly
		 */
		private void markAllTransactionsAsToAdd(NonGenesisBlock block) throws DatabaseException, TimeoutException, InterruptedException, ApplicationException {
			for (int pos = 0; pos < block.getTransactionsCount(); pos++)
				toAdd.add(intoTransactionEntry(block.getTransaction(pos)));
		}

		/**
		 * Adds the transactions from the given block to those that must be removed from the mempool.
		 * 
		 * @param block the block
		 * @throws DatabaseException if the database is corrupted
		 */
		private void markAllTransactionsAsToRemove(NonGenesisBlock block) throws DatabaseException {
			block.getTransactions().forEach(toRemove::add);
		}

		/**
		 * Expands the given transaction into a transaction entry, by filling its priority and hash.
		 * 
		 * @param transaction the transaction
		 * @return the resulting transaction entry
		 * @throws DatabaseException if the database is corrupted
		 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
		 * @throws TimeoutException if the application did not provide an answer in time
		 * @throws ApplicationException if the application is not working properly
		 */
		private TransactionEntry intoTransactionEntry(Transaction transaction) throws DatabaseException, TimeoutException, InterruptedException, ApplicationException {
			try {
				return new TransactionEntry(transaction, app.getPriority(transaction), hasher.hash(transaction));
			}
			catch (TransactionRejectedException e) {
				// the database contains a block with a rejected transaction: it should not be there!
				throw new DatabaseException(e);
			}
		}

		private void removeAll(Set<Transaction> toRemove) {
			new HashSet<>(mempool).stream()
				.filter(entry -> toRemove.contains(entry.transaction))
				.forEach(Mempool.this::remove);
		}

		private Block getBlock(byte[] hash) throws NodeException, DatabaseException, ClosedDatabaseException {
			return blockchain.getBlock(hash).orElseThrow(() -> new DatabaseException("Missing block with hash " + Hex.toHexString(hash)));
		}
	}
}
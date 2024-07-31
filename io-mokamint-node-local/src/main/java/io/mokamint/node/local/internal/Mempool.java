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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
import io.hotmoka.exceptions.CheckSupplier;
import io.hotmoka.exceptions.UncheckFunction;
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
	 * @throws NodeException if the node is misbehaving
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if some operation timed out
	 */
	public void rebaseAt(Block newBase) throws NodeException, InterruptedException, TimeoutException {
		synchronized (mempool) {
			RebaseAt rebaseAt = CheckSupplier.check(NodeException.class, InterruptedException.class, TimeoutException.class, () ->
				blockchain.getEnvironment().computeInReadonlyTransaction(UncheckFunction.uncheck(txn -> new RebaseAt(txn, newBase)))
			);

			rebaseAt.updateMempool();
		}
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
	 * @throws NodeException if the node is misbehaving
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if some operation timed out
	 */
	public MempoolEntry add(Transaction transaction) throws TransactionRejectedException, NodeException, InterruptedException, TimeoutException {
		byte[] hash = hasher.hash(transaction);
		String hexHash = Hex.toHexString(hash);

		try {
			app.checkTransaction(transaction);
			var entry = new TransactionEntry(transaction, app.getPriority(transaction), hash);
			int maxSize = node.getConfig().getMempoolSize();

			synchronized (mempool) {
				if (base.isPresent() && blockchain.getTransactionAddress(base.get(), hash).isPresent())
					// the transaction was already in blockchain
					throw new TransactionRejectedException("Repeated transaction " + hexHash);
				else if (mempool.contains(entry))
					// the transaction was already in the mempool
					throw new TransactionRejectedException("Repeated transaction " + hexHash);
				else if (mempool.size() >= maxSize)
					throw new TransactionRejectedException("Cannot add transaction " + hexHash + ": all " + maxSize + " slots of the mempool are full");
				else
					mempool.add(entry);
			}

			LOGGER.info("mempool: added transaction " + hexHash);
			node.onAdded(transaction);

			return entry.getMempoolEntry();
		}
		catch (ApplicationException e) {
			throw new NodeException(e);
		}
	}

	public void remove(TransactionEntry entry) {
		synchronized (mempool) {
			mempool.remove(entry);
		}
	}

	/**
	 * Performs an action for each transaction in this mempool.
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
		if (start < 0L || count <= 0)
			return MempoolPortions.of(Stream.empty());
	
		synchronized (mempool) {
			return MempoolPortions.of(mempool.stream().skip(start).limit(count).map(TransactionEntry::getMempoolEntry));
		}
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
		private final io.hotmoka.xodus.env.Transaction txn;
		private final Block newBase;
		private Block newBlock;
		private Block oldBlock;
		private final Set<Transaction> toRemove = new HashSet<>();
		private final Set<TransactionEntry> toAdd = new HashSet<>();

		private RebaseAt(io.hotmoka.xodus.env.Transaction txn, Block newBase) throws NodeException, InterruptedException, TimeoutException {
			this.txn = txn;
			this.newBase = newBase;
			this.newBlock = newBase;
			this.oldBlock = base.orElse(null);

			rebase();
		}

		private void updateMempool() {
			mempool.addAll(toAdd);

			if (!mempool.isEmpty())
				removeAll(toRemove);

			base = Optional.of(newBase);
		}

		private void rebase() throws NodeException, InterruptedException, TimeoutException {
			if (oldBlock == null)
				markToRemoveAllTransactionsFromNewBaseToGenesis(); // if the base is empty, there is nothing to add and only to remove
			else {
				// first we move new and old bases to the same height
				while (newBlock.getDescription().getHeight() > oldBlock.getDescription().getHeight())
					markToRemoveAllTransactionsInNewBlockAndMoveItBackwards();

				while (newBlock.getDescription().getHeight() < oldBlock.getDescription().getHeight())
					markToAddAllTransactionsInOldBlockAndMoveItBackwards();

				// then we continue backwards, until they meet
				while (!reachedSharedAncestor()) {
					markToRemoveAllTransactionsInNewBlockAndMoveItBackwards();
					markToAddAllTransactionsInOldBlockAndMoveItBackwards();
				}
			}
		}

		private boolean reachedSharedAncestor() throws NodeException {
			if (newBlock.equals(oldBlock))
				return true;
			else if (newBlock instanceof GenesisBlock || oldBlock instanceof GenesisBlock)
				throw new DatabaseException("Cannot identify a shared ancestor block between " + oldBlock.getHexHash(node.getConfig().getHashingForBlocks())
					+ " and " + newBlock.getHexHash(node.getConfig().getHashingForBlocks()));
			else
				return false;
		}

		private void markToRemoveAllTransactionsInNewBlockAndMoveItBackwards() throws NodeException {
			if (newBlock instanceof NonGenesisBlock ngb) {
				markAllTransactionsAsToRemove(ngb);
				newBlock = getBlock(ngb.getHashOfPreviousBlock());
			}
			else
				throw new DatabaseException("The database contains a genesis block " + newBlock.getHexHash(node.getConfig().getHashingForBlocks()) + " at height " + newBlock.getDescription().getHeight());
		}

		private void markToAddAllTransactionsInOldBlockAndMoveItBackwards() throws NodeException, InterruptedException, TimeoutException {
			if (oldBlock instanceof NonGenesisBlock ngb) {
				markAllTransactionsAsToAdd(ngb);
				oldBlock = getBlock(ngb.getHashOfPreviousBlock());
			}
			else
				throw new DatabaseException("The database contains a genesis block " + oldBlock.getHexHash(node.getConfig().getHashingForBlocks()) + " at height " + oldBlock.getDescription().getHeight());
		}

		private void markToRemoveAllTransactionsFromNewBaseToGenesis() throws NodeException {
			while (!mempool.isEmpty() && newBlock instanceof NonGenesisBlock ngb) {
				markAllTransactionsAsToRemove(ngb);
				newBlock = getBlock(ngb.getHashOfPreviousBlock());
			}
		}

		/**
		 * Adds the transaction entries to those that must be added to the mempool.
		 * 
		 * @param block the block
		 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
		 * @throws TimeoutException if some operation timed out
		 * @throws NodeException if the node is misbehaving
		 */
		private void markAllTransactionsAsToAdd(NonGenesisBlock block) throws InterruptedException, TimeoutException, NodeException {
			for (int pos = 0; pos < block.getTransactionsCount(); pos++)
				toAdd.add(intoTransactionEntry(block.getTransaction(pos)));
		}

		/**
		 * Adds the transactions from the given block to those that must be removed from the mempool.
		 * 
		 * @param block the block
		 * @throws NodeException if the node is misbehaving
		 */
		private void markAllTransactionsAsToRemove(NonGenesisBlock block) throws NodeException {
			block.getTransactions().forEach(toRemove::add);
		}

		/**
		 * Expands the given transaction into a transaction entry, by filling its priority and hash.
		 * 
		 * @param transaction the transaction
		 * @return the resulting transaction entry
		 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
		 * @throws TimeoutException if some operation timed out
		 * @throws NodeException if the node is misbehaving
		 */
		private TransactionEntry intoTransactionEntry(Transaction transaction) throws InterruptedException, TimeoutException, NodeException {
			try {
				return new TransactionEntry(transaction, app.getPriority(transaction), hasher.hash(transaction));
			}
			catch (TransactionRejectedException e) {
				// the database contains a block with a rejected transaction: it should not be there!
				throw new DatabaseException(e);
			}
			catch (ApplicationException e) {
				// the node is misbehaving because the application it is connected to is misbehaving
				throw new NodeException(e);
			}
		}

		private void removeAll(Set<Transaction> toRemove) {
			new HashSet<>(mempool).stream()
				.filter(entry -> toRemove.contains(entry.transaction))
				.forEach(mempool::remove);
		}

		private Block getBlock(byte[] hash) throws NodeException {
			return blockchain.getBlock(txn, hash).orElseThrow(() -> new DatabaseException("Missing block with hash " + Hex.toHexString(hash)));
		}
	}
}
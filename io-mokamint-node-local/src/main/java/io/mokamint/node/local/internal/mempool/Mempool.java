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
package io.mokamint.node.local.internal.mempool;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
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
import io.mokamint.node.local.internal.AbstractMempool;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.blockchain.Blockchain;

/**
 * The mempool of a Mokamint node. It contains transactions that are available
 * to be processed and included in the blocks of the blockchain.
 */
@ThreadSafe
public class Mempool extends AbstractMempool {

	/**
	 * The blockchain of the node.
	 */
	private final Blockchain blockchain;

	/**
	 * The application running in the node.
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
	 * The container of the transactions inside this mempool.
	 */
	@GuardedBy("itself")
	private final SortedSet<TransactionEntry> mempool = new TreeSet<>();

	/**
	 * The hash of the base block of the mempool: the transactions inside {@link #mempool}
	 * have arrived after the creation of this block. If missing, the transactions
	 * have arrived after the creation of the blockchain itself.
	 */
	@GuardedBy("this.mempool")
	private Optional<byte[]> base;

	/**
	 * The container of the transactions inside this mempool, as a list.
	 */
	@GuardedBy("this.mempool")
	private List<TransactionEntry> mempoolAsList;

	private final static Logger LOGGER = Logger.getLogger(Mempool.class.getName());

	/**
	 * Creates a mempool for the given node.
	 * 
	 * @param node the node
	 */
	public Mempool(LocalNodeImpl node) {
		this(node, Optional.empty());
	}

	public Mempool(Mempool parent, byte[] newBase) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		this(parent.getNode(), Optional.of(newBase));

		Optional<byte[]> baseOfParent;

		synchronized (parent.mempool) {
			baseOfParent = parent.base;
			this.mempool.addAll(parent.mempool);
		}

		if (baseOfParent.isEmpty()) {
			while (!mempool.isEmpty()) {
				var newBlock = getBlock(newBase);
				var toRemove = newBlock.getTransactions().collect(Collectors.toCollection(HashSet::new));
				new HashSet<>(mempool).stream()
					.filter(entry -> toRemove.contains(entry.transaction))
					.forEach(mempool::remove);

				if (newBlock instanceof NonGenesisBlock ngb)
					newBase = ngb.getHashOfPreviousBlock();
				else
					break;
			}
		}
		else {
			var oldBase = baseOfParent.get();
			var oldBlock = getBlock(oldBase);
			var newBlock = getBlock(newBase);
			Set<Transaction> toRemove = new HashSet<>();
			Set<TransactionEntry> toAdd = new HashSet<>();

			while (newBlock.getDescription().getHeight() > oldBlock.getDescription().getHeight()) {
				if (newBlock instanceof NonGenesisBlock ngb) {
					newBlock.getTransactions().forEach(toRemove::add);
					newBase = ngb.getHashOfPreviousBlock();
					newBlock = getBlock(newBase);
				}
				else
					throw new DatabaseException("The database contains a genesis block " + Hex.toHexString(newBase) + " at height " + newBlock.getDescription().getHeight());
			}

			while (newBlock.getDescription().getHeight() < oldBlock.getDescription().getHeight()) {
				if (oldBlock instanceof NonGenesisBlock ngb) {
					for (int pos = 0; pos < oldBlock.getTransactionsCount(); pos++) {
						var transaction = oldBlock.getTransaction(pos);

						try {
							toAdd.add(new TransactionEntry(transaction, app.getPriority(transaction), hasher.hash(transaction)));
						}
						catch (RejectedTransactionException e) {
							// the database contains a block with a rejected transaction: it should not be there!
							throw new DatabaseException(e);
						}
					}

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
					newBlock.getTransactions().forEach(toRemove::add);
					newBase = ngb1.getHashOfPreviousBlock();
					newBlock = getBlock(newBase);
				}
				else
					throw new DatabaseException("The database contains a genesis block " + Hex.toHexString(newBase) + " at height " + newBlock.getDescription().getHeight());

				if (oldBlock instanceof NonGenesisBlock ngb2) {
					for (int pos = 0; pos < oldBlock.getTransactionsCount(); pos++) {
						var transaction = oldBlock.getTransaction(pos);

						try {
							toAdd.add(new TransactionEntry(transaction, app.getPriority(transaction), hasher.hash(transaction)));
						}
						catch (RejectedTransactionException e) {
							// the database contains a block with a rejected transaction: it should not be there!
							throw new DatabaseException(e);
						}
					}

					oldBase = ngb2.getHashOfPreviousBlock();
					oldBlock = getBlock(oldBase);
				}
				else
					throw new DatabaseException("The database contains a genesis block " + Hex.toHexString(oldBase) + " at height " + oldBlock.getDescription().getHeight());				
			}

			mempool.addAll(toAdd);

			if (!mempool.isEmpty())
				new HashSet<>(mempool).stream()
					.filter(entry -> toRemove.contains(entry.transaction))
					.forEach(mempool::remove);
		}
	}

	private Block getBlock(byte[] hash) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		return blockchain.getBlock(hash).orElseThrow(() -> new DatabaseException("Missing block with hash " + Hex.toHexString(hash)));
	}

	private Mempool(LocalNodeImpl node, Optional<byte[]> base) {
		super(node);

		this.blockchain = node.getBlockchain();
		this.app = node.getApplication();
		this.maxSize = node.getConfig().getMempoolSize();
		this.hasher = node.getConfig().getHashingForTransactions().getHasher(Transaction::toByteArray);
		this.base = base;
	}

	/**
	 * Checks the validity of the given transaction and, if valid, adds it to this mempool.
	 * 
	 * @param transaction the transaction to add
	 * @return information about the transaction that has been added
	 * @throws RejectedTransactionException if the transaction has been rejected; this happens,
	 *                                      for instance, if the application considers the
	 *                                      transaction as invalid or if its priority cannot be computed
	 */
	public MempoolEntry add(Transaction transaction) throws RejectedTransactionException {
		byte[] hash = hasher.hash(transaction);
		String hexHash = Hex.toHexString(hash);
		if (!app.checkTransaction(transaction))
			throw new RejectedTransactionException("Invalid transaction " + hexHash);

		long priority;

		try {
			priority = app.getPriority(transaction);
		}
		catch (RejectedTransactionException e) {
			throw new RejectedTransactionException("Cannot compute the priority of transaction " + hexHash, e);
		}

		scheduleWhisperingWithoutAddition(transaction);

		var entry = new TransactionEntry(transaction, priority, hash);

		synchronized (mempool) {
			if (mempool.size() < maxSize) {
				if (!mempool.add(entry))
					throw new RejectedTransactionException("Repeated transaction " + hexHash);

				mempoolAsList = null; // invalidation
			}
			else
				throw new RejectedTransactionException("Cannot add transaction " + hexHash + ": all " + maxSize + " slots of the mempool are full");
		}

		LOGGER.info("mempool: added transaction " + hexHash);

		onTransactionAdded(transaction);

		return entry.getEntry();
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
	 * @param start
	 * @param count
	 * @return
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
			.map(TransactionEntry::getEntry);

		return MempoolPortions.of(entries);
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

		private MempoolEntry getEntry() {
			return MempoolEntries.of(hash, priority);
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
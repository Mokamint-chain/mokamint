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

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.Database;
import io.mokamint.node.local.internal.LocalNodeImpl;

/**
 * The blockchain of a local node. It contains blocks rooted at a genesis block.
 * It is a tree rather than necessarily a list of blocks, since a block might
 * have more children, but only one child can lead to the head of the blockchain,
 * which is the most powerful block in the chain.
 */
@ThreadSafe
public class Blockchain {

	/**
	 * The node having this blockchain.
	 */
	private final LocalNodeImpl node;

	/**
	 * The database of the node.
	 */
	private final Database db;

	/**
	 * A cache for the genesis block, if it has been set already.
	 * Otherwise it holds {@code null}.
	 */
	private volatile Optional<GenesisBlock> genesis;

	/**
	 * A buffer where blocks without a known previous block are parked, in case
	 * their previous block arrives later.
	 */
	@GuardedBy("itself")
	private final NonGenesisBlock[] orphans = new NonGenesisBlock[20];

	/**
	 * The next insertion position inside the {@link #orphans} array.
	 */
	@GuardedBy("orphans")
	private int orphansPos;

	/**
	 * The hashing used for the blocks in the node.
	 */
	private final HashingAlgorithm<byte[]> hashingForBlocks;

	/**
	 * True if and only if the blockchain is currently performing
	 * a synchronization from the peers of the node.
	 */
	private final AtomicBoolean isSynchronizing = new AtomicBoolean(false);

	private final static Logger LOGGER = Logger.getLogger(Blockchain.class.getName());

	/**
	 * Creates the container of the blocks of a node.
	 * 
	 * @param node the node
	 */
	public Blockchain(LocalNodeImpl node) {
		this.node = node;
		this.db = node.getDatabase();
		this.hashingForBlocks = node.getConfig().getHashingForBlocks();
	}

	/**
	 * Triggers block mining on top of the current head, if this blockchain
	 * is not performing a synchronization. Otherwise, nothing happens.
	 * This method requires the blockchain to be non-empty.
	 * 
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public void startMining() throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		if (db.getHeadHash().isEmpty())
			LOGGER.warning("cannot mine on an empty blockchain");
		// if synchronization is in progress, mining will be triggered at its end
		else if (!isSynchronizing.get())
			node.submit(new MineNewBlockTask(node));
	}

	/**
	 * Triggers a synchronization of this blockchain from the peers of the node,
	 * if this blockchain is not already performing a synchronization. Otherwise, nothing happens.
	 * 
	 * @param initialHeight the height of the blockchain from where synchronization must be applied
	 */
	public void startSynchronization(long initialHeight) {
		if (!isSynchronizing.getAndSet(true))
			node.submit(new SynchronizationTask(node, initialHeight, () -> isSynchronizing.set(false)));
	}

	/**
	 * Yields the first genesis block of this blockchain, if any.
	 * 
	 * @return the genesis block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the genesis block is unknown
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<GenesisBlock> getGenesis() throws DatabaseException, ClosedDatabaseException, NoSuchAlgorithmException {
		// we use a cache to avoid repeated access for reading the genesis block
		if (genesis != null)
			return genesis;

		Optional<byte[]> maybeGenesisHash = db.getGenesisHash();
		if (maybeGenesisHash.isEmpty())
			return Optional.empty();

		Optional<Block> maybeGenesis = db.getBlock(maybeGenesisHash.get());
		if (maybeGenesis.isPresent()) {
			Block genesis = maybeGenesis.get();
			if (genesis instanceof GenesisBlock gb)
				return this.genesis = Optional.of(gb);
			else
				throw new DatabaseException("the genesis hash is set but it refers to a non-genesis block in the database");
		}
		else
			throw new DatabaseException("the genesis hash is set but it is not in the database");
	}

	/**
	 * Yields the head block of this blockchain, if any.
	 * 
	 * @return the head block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<Block> getHead() throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		Optional<byte[]> maybeHeadHash = db.getHeadHash();
		if (maybeHeadHash.isEmpty())
			return Optional.empty();

		Optional<Block> maybeBlock = db.getBlock(maybeHeadHash.get());
		if (maybeBlock.isPresent())
			return maybeBlock;
		else
			throw new DatabaseException("the head hash is set but it is not in the database");
	}

	/**
	 * Yields information about the current best chain.
	 * 
	 * @return the information
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public ChainInfo getChainInfo() throws DatabaseException, ClosedDatabaseException {
		return db.getChainInfo();
	}

	/**
	 * Yields the hashes of the blocks in the best chain, starting at height {@code start}
	 * (inclusive) and ending at height {@code start + count} (exclusive). The result
	 * might actually be shorter if the best chain is shorter than {@code start + count} blocks.
	 * 
	 * @param start the height of the first block whose hash is returned
	 * @param count how many hashes (maximum) must be reported
	 * @return the hashes, in order
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Stream<byte[]> getChain(long start, long count) throws DatabaseException, ClosedDatabaseException {
		return db.getChain(start, count);
	}

	/**
	 * Determines if this blockchain contains a block with the given hash.
	 * 
	 * @param hash the hash of the block
	 * @return true if and only if that condition holds
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean containsBlock(byte[] hash) throws DatabaseException, ClosedDatabaseException {
		return db.containsBlock(hash);
	}

	/**
	 * Adds the given block to the database of blocks of this node.
	 * If the block was already in the database, nothing happens.
	 * 
	 * @param block the block to add
	 * @return true if the block has been actually added to the tree of blocks
	 *         rooted at the genesis block, false otherwise.
	 *         There are a few situations when the result can be false. For instance,
	 *         if {@code block} was already in the tree, or if {@code block} is
	 *         a genesis block but a genesis block is already present in the tree, or
	 *         if {@code block} has no previous block already in the tree (it is orphaned),
	 *         or if the block has a previous block in the tree but it cannot be
	 *         correctly verified as a legal child of that previous block
	 * @throws DatabaseException if the block cannot be added, because the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 * @throws VerificationException if {@code block} cannot be added since it does not respect all
	 *                               consensus rules
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean add(final Block block) throws DatabaseException, NoSuchAlgorithmException, VerificationException, ClosedDatabaseException {
		boolean added = false, addedToOrphans = false;
		var updatedHead = new AtomicReference<Block>();

		// we use a working set, since the addition of a single block might
		// trigger the further addition of orphan blocks, recursively
		var ws = new ArrayList<Block>();
		ws.add(block);
	
		do {
			Block cursor = ws.remove(ws.size() - 1);
	
			if (!db.containsBlock(cursor.getHash(hashingForBlocks))) { // optimization check, to avoid repeated verification
				if (cursor instanceof NonGenesisBlock ngb) {
					Optional<Block> previous = db.getBlock(ngb.getHashOfPreviousBlock());
					if (previous.isEmpty()) {
						putAmongOrphans(ngb);
						if (block == ngb)
							addedToOrphans = true;
					}
					else
						added |= add(ngb, previous, block == ngb, ws, updatedHead);
				}
				else
					added |= add(cursor, Optional.empty(), block == cursor, ws, updatedHead);
			}
		}
		while (!ws.isEmpty());

		Block newHead = updatedHead.get();
		if (newHead != null)
			startMining();
		else if (addedToOrphans) {
			var head = getHead();
			if (head.isEmpty())
				startSynchronization(0L);
			else if (head.get().getPower().compareTo(block.getPower()) < 0)
				// the block was better than our current head, but misses a previous block:
				// we synchronize from the upper portion (1000 blocks deep) of the blockchain, upwards
				startSynchronization(Math.max(0L, head.get().getHeight() - 1000L));
		}

		return added;
	}

	private boolean add(Block block, Optional<Block> previous, boolean first, ArrayList<Block> ws, AtomicReference<Block> updatedHead)
			throws DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException, VerificationException {

		try {
			verify(block, previous);

			if (db.add(block, updatedHead)) {
				getOrphansWithParent(block).forEach(ws::add);
				if (first)
					return true;
			}
		}
		catch (VerificationException e) {
			if (first)
				throw e;
			else
				LOGGER.warning("discarding orphan block " + block.getHexHash(hashingForBlocks) + " since does not pass verification: " + e.getMessage());
		}

		return false;
	}

	private void verify(Block block, Optional<Block> previous) throws VerificationException {
		if (block instanceof GenesisBlock gb)
			verify(gb);
		else
			verify((NonGenesisBlock) block, previous.get());
	}

	private void verify(GenesisBlock block) throws VerificationException {
	}

	private void verify(NonGenesisBlock block, Block previous) throws VerificationException {
	}

	/**
	 * Adds the given block to {@link #orphans}.
	 * 
	 * @param block the block to add
	 */
	private void putAmongOrphans(NonGenesisBlock block) {
		synchronized (orphans) {
			if (Stream.of(orphans).anyMatch(block::equals))
				// it is already inside the array: it is better not to waste a slot
				return;

			orphansPos = (orphansPos + 1) % orphans.length;
			orphans[orphansPos] = block;
		}
	}

	/**
	 * Yields the orphans having the given parent.
	 * 
	 * @param parent the parent
	 * @return the orphans whose previous block is {@code parent}, if any
	 */
	private Stream<NonGenesisBlock> getOrphansWithParent(Block parent) {
		byte[] hashOfParent = parent.getHash(hashingForBlocks);

		synchronized (orphans) {
			return Stream.of(orphans)
					.filter(Objects::nonNull)
					.filter(orphan -> Arrays.equals(orphan.getHashOfPreviousBlock(), hashOfParent));
		}
	}
}
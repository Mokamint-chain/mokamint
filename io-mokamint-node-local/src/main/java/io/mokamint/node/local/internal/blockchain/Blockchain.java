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
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.local.internal.Database;
import io.mokamint.node.local.internal.LocalNodeImpl;

/**
 * The blockchain of a local node. It contains blocks rooted at a genesis block.
 * It is a tree rather than necessarily a list of blocks, since a node might
 * have more children, but only one child can lead to the head of the blockchain,
 * which is the most powerful block in the chain.
 */
@ThreadSafe
public class Blockchain {

	/**
	 * The node.
	 */
	private final LocalNodeImpl node;

	/**
	 * The database of the node.
	 */
	private final Database db;

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
	 * Creates the container of the blocks of a node.
	 * 
	 * @param node the node
	 * @param db the database of the node
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 */
	public Blockchain(LocalNodeImpl node, Database db) throws NoSuchAlgorithmException, DatabaseException {
		this.node = node;
		this.hashingForBlocks = node.getConfig().getHashingForBlocks();
		this.db = db;
		synchronize();
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
	 */
	public boolean add(Block block) throws DatabaseException, NoSuchAlgorithmException {
		boolean added = false, first = true;
		var headChanged = new AtomicBoolean(false);
	
		// we use a working set, since the addition of a single block might
		// trigger the further addition of orphan blocks, recursively
		var ws = new ArrayList<Block>();
		ws.add(block);
	
		do {
			Block cursor = ws.remove(ws.size() - 1);
	
			if (!db.contains(cursor)) { // optimization check, to avoid repeated verification
				if (cursor instanceof NonGenesisBlock ngb) {
					Optional<Block> previous = db.getBlock(ngb.getHashOfPreviousBlock());
					if (previous.isEmpty())
						putAmongOrphans(ngb);
					else {
						if (verify(ngb, previous.get()) && db.add(cursor, headChanged)) {
							getOrphansWithParent(cursor).forEach(ws::add);
							if (first)
								added = true;
						}
					}
				}
				else if (verify((GenesisBlock) block) && db.add(cursor, headChanged)) {
					getOrphansWithParent(cursor).forEach(ws::add);
					if (first)
						added = true;
				}
			}
	
			first = false;
		}
		while (!ws.isEmpty());
	
		if (headChanged.get())
			mineNextBlock();

		return added;
	}

	/**
	 * Starts a mining task for the next block, on top of the head of the chain. If the chain is empty,
	 * the task will start mining a genesis block. If a mining task was already running when this method is
	 * called, that previous mining task gets interrupted and replaced with this new mining task.
	 */
	public void mineNextBlock() {
		node.submit(new MineNewBlockTask(node, db));
	}

	private boolean verify(GenesisBlock block) {
		return true;
	}

	private boolean verify(NonGenesisBlock block, Block previous) {
		return true;
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

	/**
	 * Synchronizes this blockchain by contacting the peers and downloading the most
	 * powerful chain among theirs.
	 */
	private void synchronize() {
		
	}
}
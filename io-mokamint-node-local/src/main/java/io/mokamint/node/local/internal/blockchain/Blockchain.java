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

import static io.hotmoka.exceptions.CheckSupplier.check;
import static io.hotmoka.exceptions.UncheckFunction.uncheck;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.application.api.Application;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.Database;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Event;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.NodeMiners;

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
	 * The configuration of the node having this blockchain.
	 */
	private final Config config;

	/**
	 * The database of the node.
	 */
	private final Database db;

	/**
	 * The application running in the node.
	 */
	private final Application app;

	/**
	 * The miners of the node.
	 */
	private final NodeMiners miners;

	/**
	 * Code that can be used to spawn new tasks.
	 */
	private final Consumer<Task> taskSpawner;

	/**
	 * Code that can be used to spawn new events.
	 */
	private final Consumer<Event> eventSpawner;

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
	 * @param app the application running in the node
	 * @param miners the miners of the node
	 * @param taskSpawner code that can be used to spawn new tasks
	 * @param eventSpawner code that can be used to spawn events
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 */
	public Blockchain(LocalNodeImpl node, Database db, Application app, NodeMiners miners, Consumer<Task> taskSpawner, Consumer<Event> eventSpawner) throws NoSuchAlgorithmException, DatabaseException {
		this.node = node;
		this.config = db.getConfig();
		this.hashingForBlocks = config.getHashingForBlocks();
		this.db = db;
		this.app = app;
		this.miners = miners;
		this.taskSpawner = taskSpawner;
		this.eventSpawner = eventSpawner;
		synchronize();
	}

	/**
	 * Yields the configuration of the node having this blockchain.
	 * 
	 * @return the configuration
	 */
	public Config getConfig() {
		return config;
	}
	
	/**
	 * Yields the first genesis block of this blockchain, if any.
	 * 
	 * @return the genesis block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the genesis block is unknown
	 * @throws DatabaseException if the database is corrupted
	 */
	public Optional<GenesisBlock> getGenesis() throws NoSuchAlgorithmException, DatabaseException {
		Optional<byte[]> maybeGenesisHash = db.getGenesisHash();

		return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
			maybeGenesisHash
				.map(uncheck(hash -> db.getBlock(hash).orElseThrow(() -> new DatabaseException("the genesis hash is set but it is not in the database"))))
				.map(uncheck(block -> castToGenesis(block).orElseThrow(() -> new DatabaseException("the genesis hash is set but it refers to a non-genesis block in the database"))))
		);
	}

	/**
	 * Yields the head block of this blockchain, if any.
	 * 
	 * @return the head block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws DatabaseException if the database is corrupted
	 */
	public Optional<Block> getHead() throws NoSuchAlgorithmException, DatabaseException {
		Optional<byte[]> maybeHeadHash = db.getHeadHash();
	
		return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
			maybeHeadHash
				.map(uncheck(hash -> db.getBlock(hash).orElseThrow(() -> new DatabaseException("the head hash is set but it is not in the database"))))
		);
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
					if (previous.isEmpty())
						putAmongOrphans(ngb);
					else {
						if (verify(ngb, previous.get()) && db.add(cursor, updatedHead)) {
							getOrphansWithParent(cursor).forEach(ws::add);
							if (first)
								added = true;
						}
					}
				}
				else if (verify((GenesisBlock) block) && db.add(cursor, updatedHead)) {
					getOrphansWithParent(cursor).forEach(ws::add);
					if (first)
						added = true;
				}
			}
	
			first = false;
		}
		while (!ws.isEmpty());
	
		Block newHead = updatedHead.get();
		if (newHead != null)
			mineNextBlock(Optional.of(newHead));

		return added;
	}

	/**
	 * Starts a mining task for the next block, on top of a previous block.
	 * If a mining task was already running when this method is
	 * called, that previous mining task gets interrupted and replaced with this new mining task.
	 * 
	 * @param previous the previous block; if missing, the genesis block is mined
	 */
	public void mineNextBlock(Optional<Block> previous) {
		taskSpawner.accept(new MineNewBlockTask(node, this, previous, app, miners, taskSpawner, eventSpawner));
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

	private static Optional<GenesisBlock> castToGenesis(Block block) {
		return block instanceof GenesisBlock gb ? Optional.of(gb) : Optional.empty();
	}

	/**
	 * Synchronizes this blockchain by contacting the peers and downloading the most
	 * powerful chain among theirs.
	 */
	private void synchronize() {
		
	}
}
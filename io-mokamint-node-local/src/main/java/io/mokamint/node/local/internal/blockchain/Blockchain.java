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

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.internal.AbstractBlockchain;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.OnAddedTransactionHandler;
import io.mokamint.node.local.internal.mempool.Mempool.TransactionEntry;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.IllegalDeadlineException;

/**
 * The blockchain of a local node. It contains blocks rooted at a genesis block.
 * It is a tree and not necessarily a list of blocks, since a block might
 * have more children, but only one child can lead to the head of the blockchain,
 * which is the most powerful block in the chain.
 */
@ThreadSafe
public class Blockchain extends AbstractBlockchain implements AutoCloseable {

	/**
	 * The hashing algorithm used for the blocks.
	 */
	private final HashingAlgorithm hashingForBlocks;

	/**
	 * The database where the blocks are persisted.
	 */
	private final BlocksDatabase db;

	/**
	 * A cache for the genesis block, if it has been set already.
	 * Otherwise it holds {@code null}.
	 */
	private volatile Optional<GenesisBlock> genesisCache;

	/**
	 * A buffer where blocks without a known previous block are parked, in case
	 * their previous block arrives later.
	 */
	@GuardedBy("itself")
	private final NonGenesisBlock[] orphans;

	/**
	 * The next insertion position inside the {@link #orphans} array.
	 */
	@GuardedBy("orphans")
	private int orphansPos;

	private final static Logger LOGGER = Logger.getLogger(Blockchain.class.getName());

	/**
	 * Creates the blockchain of a node. It restores the block from the persistent
	 * state of the blocks database, if any.
	 * 
	 * @param node the node
	 * @throws DatabaseException if the database of blocks is corrupted
	 */
	public Blockchain(LocalNodeImpl node) throws DatabaseException {
		super(node);

		var config = node.getConfig();

		this.hashingForBlocks = config.getHashingForBlocks();
		this.orphans = new NonGenesisBlock[config.getOrphansMemorySize()];
		this.db = new BlocksDatabase(node);
	}

	@Override
	public void close() throws InterruptedException, DatabaseException {
		db.close();
	}

	/**
	 * Yields the hash of the first genesis block that has been added to this database, if any.
	 * 
	 * @return the hash of the genesis block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<byte[]> getGenesisHash() throws DatabaseException, ClosedDatabaseException {
		return db.getGenesisHash();
	}

	/**
	 * Yields the first genesis block of this blockchain, if any.
	 * 
	 * @return the genesis block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<GenesisBlock> getGenesis() throws DatabaseException, ClosedDatabaseException {
		// we use a cache to avoid repeated access for reading the genesis block
		if (genesisCache != null)
			return genesisCache;

		Optional<GenesisBlock> result = db.getGenesis();

		if (result.isPresent())
			genesisCache = result;

		return result;
	}

	/**
	 * Determines if this blockchain is empty.
	 * 
	 * @return true if and only if this blockchain is empty
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean isEmpty() throws DatabaseException, ClosedDatabaseException {
		return db.getGenesisHash().isEmpty();
	}

	/**
	 * Yields the hash of the head block of the blockchain, if it has been set already.
	 * 
	 * @return the hash of the head block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<byte[]> getHeadHash() throws DatabaseException, ClosedDatabaseException {
		return db.getHeadHash();
	}

	/**
	 * Yields the head block of this blockchain, if any.
	 * 
	 * @return the head block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the head is unknown
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<Block> getHead() throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		return db.getHead();
	}

	/**
	 * Yields the height of the head block of this blockchain, if any.
	 * 
	 * @return the height
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public OptionalLong getHeightOfHead() throws DatabaseException, ClosedDatabaseException {
		return db.getHeightOfHead();
	}

	/**
	 * Yields the power of the head block of this blockchain, if any.
	 * 
	 * @return the power
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<BigInteger> getPowerOfHead() throws DatabaseException, ClosedDatabaseException {
		return db.getPowerOfHead();
	}

	/**
	 * Yields the block with the given hash, if it is contained in this blockchain.
	 * 
	 * @param hash the hash
	 * @return the block, if any
	 * @throws NoSuchAlgorithmException if the block uses an unknown hashing or signature algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<Block> getBlock(byte[] hash) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		return db.getBlock(hash);
	}

	/**
	 * Yields the description of the block with the given hash, if it is contained in this blockchain.
	 * 
	 * @param hash the hash
	 * @return the description of the block, if any
	 * @throws NoSuchAlgorithmException if the block uses an unknown hashing or signature algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<BlockDescription> getBlockDescription(byte[] hash) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		return db.getBlockDescription(hash);
	}

	/**
	 * Yields the hashes of the blocks that follow the block with the given hash, if any.
	 * 
	 * @param hash the hash of the parent block
	 * @return the hashes
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Stream<byte[]> getForwards(byte[] hash) throws DatabaseException, ClosedDatabaseException {
		return db.getForwards(hash);
	}

	/**
	 * Yields the transaction with the given hash, if it is contained in some block of the best chain of the blockchain.
	 * 
	 * @param hash the hash of the transaction to search
	 * @return the transaction, if any
	 * @throws NoSuchAlgorithmException if the block containing the transaction refers to an unknown hashing or signature algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<Transaction> getTransaction(byte[] hash) throws ClosedDatabaseException, DatabaseException, NoSuchAlgorithmException {
		return db.getTransaction(hash);
	}

	/**
	 * Yields the address of the transaction with the given hash, if it is contained in some block
	 * of the best chain of this database.
	 * 
	 * @param hash the hash of the transaction to search
	 * @return the transaction address, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<TransactionAddress> getTransactionAddress(byte[] hash) throws ClosedDatabaseException, DatabaseException {
		return db.getTransactionAddress(hash);
	}

	/**
	 * Yields the address of the transaction with the given hash, if it is contained in the
	 * chain from the block with hash {@code blockHash} towards the genesis block of this blockchain.
	 * 
	 * @param blockHash the hash of the block
	 * @param hash the hash of the transaction to search
	 * @return the transaction address, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database refers to an unknown hashing algorithm
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<TransactionAddress> getTransactionAddress(byte[] blockHash, byte[] hash) throws ClosedDatabaseException, DatabaseException, NoSuchAlgorithmException {
		return db.getTransactionAddress(blockHash, hash);
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
	public Stream<byte[]> getChain(long start, int count) throws DatabaseException, ClosedDatabaseException {
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
	 * If the block was already in the database, nothing happens. Note that the addition
	 * of a block might actually induce the addition of more, orphan blocks,
	 * if the block is recognized as the previous block of an orphan block.
	 * The addition of a block might change the head of the best chain, in which case
	 * mining will be started by this method. Moreover, if the block is an orphan
	 * with higher power than the current head, its addition might trigger the synchronization
	 * of the chain from the peers of the node.
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
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown cryptographic algorithm
	 * @throws VerificationException if {@code block} cannot be added since it does not respect all consensus rules
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean add(Block block) throws DatabaseException, NoSuchAlgorithmException, VerificationException, ClosedDatabaseException {
		boolean added = false, addedToOrphans = false;
		var updatedHead = new AtomicReference<byte[]>();

		// we use a working set, since the addition of a single block might
		// trigger the further addition of orphan blocks, recursively
		var ws = new ArrayList<Block>();
		ws.add(block);
	
		do {
			Block cursor = ws.remove(ws.size() - 1);
			byte[] hashOfCursor = cursor.getHash(hashingForBlocks);
			if (!containsBlock(hashOfCursor)) { // optimization check, to avoid repeated verification
				if (cursor instanceof NonGenesisBlock ngb) {
					Optional<Block> previous = getBlock(ngb.getHashOfPreviousBlock());
					if (previous.isEmpty()) {
						putAmongOrphans(ngb);
						if (block == ngb)
							addedToOrphans = true;
					}
					else
						added |= add(ngb, hashOfCursor, previous, block == ngb, ws, updatedHead);
				}
				else
					added |= add(cursor, hashOfCursor, Optional.empty(), block == cursor, ws, updatedHead);
			}
		}
		while (!ws.isEmpty());

		byte[] newHeadHash = updatedHead.get();
		if (newHeadHash != null) {
			rebaseMempoolAt(newHeadHash);
			onHeadChanged(newHeadHash);
			scheduleMining();
		}
		else if (addedToOrphans) {
			var powerOfHead = getPowerOfHead();
			if (powerOfHead.isEmpty())
				scheduleSynchronization(0L);
			else if (powerOfHead.get().compareTo(block.getDescription().getPower()) < 0)
				// the block was better than our current head, but misses a previous block:
				// we synchronize from the upper portion (1000 blocks deep) of the blockchain, upwards
				scheduleSynchronization(Math.max(0L, getHeightOfHead().getAsLong() - 1000L));
		}

		return added;
	}

	/**
	 * Initializes this blockchain, which must be empty. It adds a genesis block.
	 * 
	 * @throws DatabaseException if the database is corrupted
	 * @throws AlreadyInitializedException if this blockchain is already initialized (non-empty)
	 * @throws InvalidKeyException if the key of the node is invalid
	 * @throws SignatureException if the genesis block could not be signed
	 */
	public void initialize() throws DatabaseException, AlreadyInitializedException, InvalidKeyException, SignatureException {
		try {
			if (!isEmpty())
				throw new AlreadyInitializedException("init cannot be required for an already initialized blockchain");
	
			var node = getNode();
			var config = node.getConfig();
			var keys = node.getKeys();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), keys.getPublic());
			var genesis = Blocks.genesis(description, node.getApplication().getInitialStateHash(), keys.getPrivate());

			add(genesis);
		}
		catch (NoSuchAlgorithmException | ClosedDatabaseException | VerificationException e) {
			// the database cannot be closed at this moment;
			// moreover, the genesis should be created correctly, hence should be verifiable
			LOGGER.log(Level.SEVERE, "blockchain: unexpected exception", e);
			throw new RuntimeException("Unexpected exception", e);
		}
	}

	@Override
	protected Stream<TransactionEntry> getMempoolTransactionsAt(byte[] newHeadHash) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		return super.getMempoolTransactionsAt(newHeadHash);
	}

	@Override
	protected void onBlockMined(Block block) {
		super.onBlockMined(block);
	}

	@Override
	protected boolean isMiningOver(Block previous) {
		return super.isMiningOver(previous);
	}

	@Override
	protected void onMiningStarted(Block previous, OnAddedTransactionHandler handler) {
		super.onMiningStarted(previous, handler);
	}

	@Override
	protected void onMiningCompleted(Block previous, OnAddedTransactionHandler handler) {
		super.onMiningCompleted(previous, handler);
	}

	@Override
	protected void onSynchronizationCompleted() {
		super.onSynchronizationCompleted();
	}

	@Override
	protected void onNoDeadlineFound(Block previous) {
		super.onNoDeadlineFound(previous);
	}

	@Override
	protected void onIllegalDeadlineComputed(Deadline deadline, Miner miner) {
		super.onIllegalDeadlineComputed(deadline, miner);
	}

	@Override
	protected void onNoMinersAvailable() {
		super.onNoMinersAvailable();
	}

	@Override
	protected void scheduleWhisperingWithoutAddition(Block block) {
		super.scheduleWhisperingWithoutAddition(block);
	}

	@Override
	protected void scheduleDelayedMining() {
		super.scheduleDelayedMining();
	}

	@Override
	protected void check(Deadline deadline) throws IllegalDeadlineException {
		super.check(deadline);
	}

	private boolean add(Block block, byte[] hashOfBlock, Optional<Block> previous, boolean first, List<Block> ws, AtomicReference<byte[]> updatedHead)
			throws DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException, VerificationException {

		try {
			new BlockVerification(getNode(), block, previous);

			if (db.add(block, updatedHead)) {
				onBlockAdded(block);
				getOrphansWithParent(block, hashOfBlock).forEach(ws::add);
				if (first)
					return true;
			}
		}
		catch (VerificationException e) {
			if (first)
				throw e;
			else
				LOGGER.warning("blockchain: discarding block " + Hex.toHexString(hashOfBlock) + " since it does not pass verification: " + e.getMessage());
		}

		return false;
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
	 * @param hashOfParent the hash of {@code parent}
	 * @return the orphans whose previous block is {@code parent}, if any
	 */
	private Stream<NonGenesisBlock> getOrphansWithParent(Block parent, byte[] hashOfParent) {
		synchronized (orphans) {
			return Stream.of(orphans)
					.filter(Objects::nonNull)
					.filter(orphan -> Arrays.equals(orphan.getHashOfPreviousBlock(), hashOfParent));
		}
	}
}
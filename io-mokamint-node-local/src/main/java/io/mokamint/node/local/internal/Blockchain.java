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

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.DatabaseException;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.nonce.api.DeadlineValidityCheckException;

/**
 * The blockchain of a local node. It contains blocks rooted at a genesis block.
 * It is a tree and not necessarily a list of blocks, since a block might
 * have more children, but only one child can lead to the head of the blockchain,
 * which is the most powerful block in the chain.
 */
@ThreadSafe
public class Blockchain implements AutoCloseable {

	/**
	 * The node having this blockchain.
	 */
	private final LocalNodeImpl node;

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
		this.node = node;
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
	 * Determines if the given block is more powerful than the current head.
	 * 
	 * @param block the block
	 * @return true if and only if the current head is missing or {@code block} is more powerful
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean headIsLessPowerfulThan(Block block) throws DatabaseException, ClosedDatabaseException {
		return db.getPowerOfHead().map(power -> power.compareTo(block.getDescription().getPower()) < 0).orElse(true);
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
	 * Yields the starting block of the non-frozen part of the history of this blockchain, if any.
	 * 
	 * @return the starting block, if any
	 * @throws NoSuchAlgorithmException if the starting block uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<Block> getStartOfNonFrozenPart() throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		return db.getStartOfNonFrozenPart();
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
	 * chain from the given {@code block} towards the genesis block of this blockchain.
	 * 
	 * @param block the block
	 * @param hash the hash of the transaction to search
	 * @return the transaction address, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database refers to an unknown hashing algorithm
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<TransactionAddress> getTransactionAddress(Block block, byte[] hash) throws ClosedDatabaseException, DatabaseException, NoSuchAlgorithmException {
		return db.getTransactionAddress(block, hash);
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
	 * Yields the creation time of the given block. It assumes that, if {@code block}
	 * is not a genesis block, then the blockchain is not empty.
	 * 
	 * @param block the block whose creation time is computed
	 * @return the creation time of {@code block}
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 */
	public LocalDateTime creationTimeOf(Block block) throws DatabaseException, ClosedDatabaseException {
		if (block instanceof GenesisBlock gb)
			return gb.getStartDateTimeUTC();
		else
			return getGenesis()
				.orElseThrow(() -> new DatabaseException("The database is not empty but its genesis block is not set"))
				.getStartDateTimeUTC().plus(block.getDescription().getTotalWaitingTime(), ChronoUnit.MILLIS);
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
	 * Initializes this blockchain, which must be empty. It adds a genesis block.
	 * 
	 * @throws DatabaseException if the database is corrupted
	 * @throws AlreadyInitializedException if this blockchain is already initialized (non-empty)
	 * @throws InvalidKeyException if the key of the node is invalid
	 * @throws SignatureException if the genesis block could not be signed
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws TimeoutException if the application did not answer in time
	 * @throws ApplicationException if the application is not behaving correctly
	 */
	public void initialize() throws DatabaseException, AlreadyInitializedException, InvalidKeyException, SignatureException, TimeoutException, InterruptedException, ApplicationException {
		try {
			if (!isEmpty())
				throw new AlreadyInitializedException("init cannot be required for an already initialized blockchain");
	
			var config = node.getConfig();
			var keys = node.getKeys();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), keys.getPublic());
			var genesis = Blocks.genesis(description, node.getApplication().getInitialStateId(), keys.getPrivate());
	
			add(genesis);
		}
		catch (NoSuchAlgorithmException | ClosedDatabaseException | VerificationException | DeadlineValidityCheckException e) {
			// the database cannot be closed at this moment;
			// the genesis should be created correctly, hence should be verifiable;
			// moreover, the genesis has no deadline
			LOGGER.log(Level.SEVERE, "blockchain: unexpected exception", e);
			throw new RuntimeException("Unexpected exception", e);
		}
	}

	/**
	 * If the block was already in the database, this method performs nothing. Otherwise,
	 * it verifies the given block and adds it to the database of blocks of this node.
	 * Note that the addition
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
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws TimeoutException if the application did not answer in time
	 * @throws DeadlineValidityCheckException if the validity of the deadline could not be determined
	 * @throws ApplicationException if the application is not behaving correctly
	 */
	public boolean add(Block block) throws DatabaseException, NoSuchAlgorithmException, VerificationException, ClosedDatabaseException, TimeoutException, InterruptedException, DeadlineValidityCheckException, ApplicationException {
		return add(block, true);
	}

	/**
	 * This method behaves like {@link #add(Block)} but assumes that the given block is
	 * verified, so that it does not need further verification before being added to blockchain.
	 * 
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws TimeoutException if the application did not answer in time
	 * @throws ApplicationException if the application is not behaving correctly
	 */
	protected boolean addVerified(Block block) throws DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException, TimeoutException, InterruptedException, ApplicationException {
		try {
			return add(block, false);
		}
		catch (VerificationException | DeadlineValidityCheckException e) {
			// impossible: we did not require block verification
			throw new RuntimeException("Unexpected exception", e);
		}
	}

	/**
	 * This method behaves like {@link #add(Block)} but allows one to specify if block verification is required.
	 * 
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws TimeoutException if the application did not answer in time
	 * @throws DeadlineValidityCheckException if the validity of some deadline could not be determined
	 * @throws ApplicationException if the application is not behaving correctly
	 */
	private boolean add(Block block, boolean verify) throws DatabaseException, NoSuchAlgorithmException, VerificationException, ClosedDatabaseException, TimeoutException, InterruptedException, DeadlineValidityCheckException, ApplicationException {
		boolean added = false, addedToOrphans = false;
		var updatedHead = new AtomicReference<Block>();

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
						added |= add(ngb, hashOfCursor, verify, previous, block == ngb, ws, updatedHead);
				}
				else
					added |= add(cursor, hashOfCursor, verify, Optional.empty(), block == cursor, ws, updatedHead);
			}
		}
		while (!ws.isEmpty());

		Block newHead = updatedHead.get();
		if (newHead != null) {
			// if the head has been improved, we update the mempool by removing
			// the transactions that have been added in blockchain and potentially adding
			// other transactions (if the history changed). Note that, in general, the mempool of
			// the node might not always be aligned with the current head of the blockchain,
			// since another task might execute this same update concurrently. This is not
			// a problem, since this rebase is only an optimization, to keep the mempool small.
			// In any case, when a new mining task is spawn, its mempool gets recomputed wrt
			// the block over which mining occurs, so it will be aligned there
			node.rebaseMempoolAt(newHead);
			// TODO: blocks added to the main chain should be signaled to the application here
			var blocksAdded = new LinkedList<Block>();

			Block cursor = newHead;
			blocksAdded.addLast(cursor);

			while (!cursor.equals(block)) {
				if (cursor instanceof NonGenesisBlock ngb) {
					var maybePrevious = getBlock(ngb.getHashOfPreviousBlock());
					if (maybePrevious.isEmpty())
						throw new DatabaseException("The head has been added to a dangling path");

					cursor = maybePrevious.get();
					blocksAdded.addFirst(cursor);
				}
				else
					throw new DatabaseException("The head has been added to a disconnected path");
			}

			node.onHeadChanged(blocksAdded);
		}
		else if (addedToOrphans && headIsLessPowerfulThan(block))
			// the block was better than our current head, but its previous block is missing:
			// we synchronize from the upper portion (1000 blocks deep) of the blockchain, upwards
			node.scheduleSynchronization();

		return added;
	}

	private boolean add(Block block, byte[] hashOfBlock, boolean verify, Optional<Block> previous, boolean first, List<Block> ws, AtomicReference<Block> updatedHead)
			throws DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException, VerificationException, TimeoutException, InterruptedException, DeadlineValidityCheckException, ApplicationException {

		try {
			if (verify)
				new BlockVerification(node, block, previous);

			if (db.add(block, updatedHead)) {
				node.onAdded(block);
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
		catch (UnknownGroupIdException e) {
			// either the application is misbehaving or somebody has closed
			// the group id of the application used for verifying the transactions;
			// in any case, the node was not able to perform the operation
			throw new ApplicationException(e); // TODO: this should become a NodeException
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
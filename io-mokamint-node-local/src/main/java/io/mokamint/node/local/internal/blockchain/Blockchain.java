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
import static io.hotmoka.xodus.ByteIterable.fromByte;
import static io.hotmoka.xodus.ByteIterable.fromBytes;

import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.xodus.ByteIterable;
import io.hotmoka.xodus.ExodusException;
import io.hotmoka.xodus.env.Environment;
import io.hotmoka.xodus.env.Store;
import io.hotmoka.xodus.env.Transaction;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.ClosureLock;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Event;

/**
 * The blockchain of a local node. It contains blocks rooted at a genesis block.
 * It is a tree rather than necessarily a list of blocks, since a block might
 * have more children, but only one child can lead to the head of the blockchain,
 * which is the most powerful block in the chain.
 */
@ThreadSafe
public class Blockchain implements AutoCloseable{

	/**
	 * The node having this blockchain.
	 */
	private final LocalNodeImpl node;

	/**
	 * The Xodus environment that holds the database of blocks.
	 */
	private final Environment environment;

	/**
	 * The Xodus store that holds the blocks of the chain.
	 */
	private final Store storeOfBlocks;

	/**
	 * The Xodus store that maps each block to its immediate successor(s).
	 */
	private final Store storeOfForwards; // TODO: maybe useless?

	/**
	 * The Xodus store that holds the list of hashes of the blocks in the current best chain.
	 * It maps 0 to the genesis block, 1 to the block at height 1 in the current best chain and so on.
	 */
	private final Store storeOfChain;

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the genesis block.
	 */
	private final static ByteIterable genesis = fromByte((byte) 42);

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the head block.
	 */
	private final static ByteIterable head = fromByte((byte) 19);

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the power of the head block.
	 */
	private final static ByteIterable power = fromByte((byte) 11);

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the height of the current best chain.
	 */
	private final static ByteIterable height = fromByte((byte) 29);

	/**
	 * The lock used to block new calls when the database has been requested to close.
	 */
	private final ClosureLock closureLock = new ClosureLock();

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
	 * @throws DatabaseException if the database of blocks is corrupted
	 */
	public Blockchain(LocalNodeImpl node) throws DatabaseException {
		this.node = node;
		this.hashingForBlocks = node.getConfig().getHashingForBlocks();
		this.environment = createBlockchainEnvironment();
		this.storeOfBlocks = openStore("blocks");
		this.storeOfForwards = openStore("forwards");
		this.storeOfChain = openStore("chain");
	}

	/**
	 * Creates the container of the blocks of a node, allowing to require initialization with a new genesis block.
	 * 
	 * @param node the node
	 * @param init if the container must be initialized with a genesis block (if empty)
	 * @throws DatabaseException if the database cannot be opened, because it is corrupted
	 * @throws AlreadyInitializedException if {@code init} is true but the container has a genesis block already
	 */
	public Blockchain(LocalNodeImpl node, boolean init) throws DatabaseException, AlreadyInitializedException {
		this(node);

		if (init)
			initialize();
	}

	@Override
	public void close() throws InterruptedException, DatabaseException {
		if (closureLock.stopNewCalls()) {
			try {
				environment.close(); // the lock guarantees that there are no unfinished transactions at this moment
				LOGGER.info("closed the blockchain database");
			}
			catch (ExodusException e) {
				LOGGER.log(Level.WARNING, "failed to close the blockchain database", e);
				throw new DatabaseException("cannot close the database", e);
			}
		}
	}

	/**
	 * Triggers block mining on top of the current head, if this blockchain
	 * is not performing a synchronization. Otherwise, nothing happens.
	 * This method requires the blockchain to be non-empty.
	 */
	public void startMining() {
		// if synchronization is in progress, mining will be triggered at its end
		if (!isSynchronizing.get())
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
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			// we use a cache to avoid repeated access for reading the genesis block
			if (genesisCache != null)
				return genesisCache;

			Optional<GenesisBlock> result = check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(this::getGenesis))
			);

			if (result.isPresent())
				genesisCache = result;

			return result;
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	/**
	 * Yields the block with the given hash, if it is contained in this database.
	 * 
	 * @param hash the hash
	 * @return the block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<Block> getBlock(byte[] hash) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(txn -> getBlock(txn, hash)))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
		}
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
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(this::getHead))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	/**
	 * Yields the hash of the first genesis block that has been added to this database, if any.
	 * 
	 * @return the hash of the genesis block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<byte[]> getGenesisHash() throws DatabaseException, ClosedDatabaseException {
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getGenesisHash)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	/**
	 * Yields the hash of the head block of the blockchain in the database, if it has been set already.
	 * 
	 * @return the hash of the head block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<byte[]> getHeadHash() throws DatabaseException, ClosedDatabaseException {
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getHeadHash)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	public Optional<BigInteger> getPowerOfHead() throws DatabaseException, ClosedDatabaseException {
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getPowerOfHead)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	public OptionalLong getHeightOfHead() throws DatabaseException, ClosedDatabaseException {
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getHeightOfHead)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
		}
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
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(txn -> getForwards(txn, fromBytes(hash)))));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	/**
	 * Yields information about the current best chain.
	 * 
	 * @return the information
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public ChainInfo getChainInfo() throws DatabaseException, ClosedDatabaseException {
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getChainInfo)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
		}
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
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(txn -> getChain(txn, start, count))));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
		}
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
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return environment.computeInReadonlyTransaction(txn -> containsBlock(txn, hash));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
		}
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
	 * @throws VerificationException if {@code block} cannot be added since it does not respect all consensus rules
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean add(Block block) throws DatabaseException, NoSuchAlgorithmException, VerificationException, ClosedDatabaseException {
		boolean added = false, addedToOrphans = false;
		var updatedHead = new AtomicReference<Block>();

		// we use a working set, since the addition of a single block might
		// trigger the further addition of orphan blocks, recursively
		var ws = new ArrayList<Block>();
		ws.add(block);
	
		do {
			Block cursor = ws.remove(ws.size() - 1);
	
			if (!containsBlock(cursor.getHash(hashingForBlocks))) { // optimization check, to avoid repeated verification
				if (cursor instanceof NonGenesisBlock ngb) {
					Optional<Block> previous = getBlock(ngb.getHashOfPreviousBlock());
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

		if (updatedHead.get() != null)
			startMining();
		else if (addedToOrphans) {
			var powerOfHead = getPowerOfHead();
			if (powerOfHead.isEmpty())
				startSynchronization(0L);
			else if (powerOfHead.get().compareTo(block.getPower()) < 0)
				// the block was better than our current head, but misses a previous block:
				// we synchronize from the upper portion (1000 blocks deep) of the blockchain, upwards
				startSynchronization(Math.max(0L, getHeightOfHead().getAsLong() - 1000L));
		}

		return added;
	}

	/**
	 * An event triggered when a block gets added to the blockchain, not necessarily
	 * to the main chain, but definitely connected to the genesis block.
	 */
	public class BlockAddedEvent implements Event {

		/**
		 * The block that has been added.
		 */
		public final Block block;

		private BlockAddedEvent(Block block) {
			this.block = block;
		}

		@Override
		public void body() throws Exception {}

		@Override
		public String toString() {
			return "block added event for block " + block.getHexHash(node.getConfig().hashingForBlocks);
		}

		@Override
		public String logPrefix() {
			return "height " + block.getHeight() + ": ";
		}
	}

	/**
	 * Adds the given block to the database of blocks.
	 * If the block was already in the database, nothing happens.
	 * 
	 * @param block the block to add
	 * @param updatedHead the new head resulting from the addition, if it changed wrt the previous head;
	 *                    otherwise it is left unchanged
	 * @return true if the block has been actually added to the database, false otherwise.
	 *         There are a few situations when the result can be false. For instance,
	 *         if {@code block} was already in the database, or if {@code block} is
	 *         a genesis block but the genesis block is already set in the database
	 * @throws DatabaseException if the block cannot be added, because the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	private boolean add(Block block, AtomicReference<Block> updatedHead) throws DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException {
		closureLock.beforeCall(ClosedDatabaseException::new);
	
		try {
			return check(DatabaseException.class, NoSuchAlgorithmException.class, () -> environment.computeInTransaction(uncheck(txn -> add(txn, block, updatedHead))));
		}
		catch (ExodusException e) {
			throw new DatabaseException("cannot write block " + block.getHexHash(hashingForBlocks) + " in the database", e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	private boolean add(Block block, Optional<Block> previous, boolean first, List<Block> ws, AtomicReference<Block> updatedHead)
			throws DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException, VerificationException {

		try {
			verify(block, previous);

			if (add(block, updatedHead)) {
				getOrphansWithParent(block).forEach(ws::add);
				if (first)
					return true;
			}
		}
		catch (VerificationException e) {
			if (first)
				throw e;
			else
				LOGGER.warning("discarding orphan block " + block.getHexHash(hashingForBlocks) + " since it does not pass verification: " + e.getMessage());
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

	private Environment createBlockchainEnvironment() {
		var env = new Environment(node.getConfig().dir.resolve("blockchain").toString());
		LOGGER.info("opened the blockchain database");
		return env;
	}

	private Store openStore(String name) throws DatabaseException {
		var store = new AtomicReference<Store>();

		try {
			environment.executeInTransaction(txn -> store.set(environment.openStoreWithoutDuplicates(name, txn)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}

		LOGGER.info("opened the store of " + name + " inside the blockchain database");
		return store.get();
	}

	/**
	 * Yields the hash of the head block of the blockchain in the database, if it has been set already,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the hash of the head block, if any
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<byte[]> getHeadHash(Transaction txn) throws DatabaseException {
		try {
			return Optional.ofNullable(storeOfBlocks.get(txn, head)).map(ByteIterable::getBytes);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the hash of the first genesis block that has been added to this database, if any,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the hash of the genesis block, if any
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<byte[]> getGenesisHash(Transaction txn) throws DatabaseException {
		try {
			return Optional.ofNullable(storeOfBlocks.get(txn, genesis)).map(ByteIterable::getBytes);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the power of the head block, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the power of the head block, if any
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<BigInteger> getPowerOfHead(Transaction txn) throws DatabaseException {
		try {
			return Optional.ofNullable(storeOfBlocks.get(txn, power)).map(ByteIterable::getBytes).map(BigInteger::new);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the height of the head block, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the height of the head block, if any
	 * @throws DatabaseException if the database is corrupted
	 */
	private OptionalLong getHeightOfHead(Transaction txn) throws DatabaseException {
		try {
			ByteIterable heightBI = storeOfBlocks.get(txn, height);
			if (heightBI == null)
				return OptionalLong.empty();
			else {
				long chainHeight = bytesToLong(heightBI.getBytes());
				if (chainHeight < 0L)
					throw new DatabaseException("The database contains a negative chain length");

				return OptionalLong.of(chainHeight);
			}
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the hashes of the blocks that follow the block with the given hash, if any.
	 * 
	 * @param txn the Xodus transaction to use for the computation
	 * @param hash the hash of the parent block
	 * @return the hashes
	 * @throws DatabaseException if the database is corrupted
	 */
	private Stream<byte[]> getForwards(Transaction txn, ByteIterable hash) throws DatabaseException {
		ByteIterable forwards;

		try {
			forwards = storeOfForwards.get(txn, hash);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}

		if (forwards == null)
			return Stream.empty();
		else {
			int size = hashingForBlocks.length();
			byte[] hashes = forwards.getBytes();
			if (hashes.length % size != 0)
				throw new DatabaseException("the forward map has been corrupted");
			else if (hashes.length == size) // frequent case, worth optimizing
				return Stream.of(hashes);
			else
				return IntStream.range(0, hashes.length / size).mapToObj(n -> slice(hashes, n, size));
		}
	}

	private static byte[] slice(byte[] all, int n, int size) {
		var result = new byte[size];
		System.arraycopy(all, n * size, result, 0, size);
		return result;
	}

	/**
	 * Yields the block with the given hash, if it is contained in this database,
	 * running inside the given transaction.
	 * 
	 * @param txn the transaction
	 * @param hash the hash
	 * @return the block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<Block> getBlock(Transaction txn, byte[] hash) throws NoSuchAlgorithmException, DatabaseException {
		try {
			ByteIterable blockBI = storeOfBlocks.get(txn, fromBytes(hash));
			return blockBI == null ? Optional.empty() : Optional.of(Blocks.from(blockBI.getBytes()));
		}
		catch (ExodusException | IOException e) {
			throw new DatabaseException(e);
		}
	}

	private Optional<Block> getHead(Transaction txn) throws NoSuchAlgorithmException, DatabaseException {
		try {
			Optional<byte[]> maybeHeadHash = getHeadHash(txn);
			if (maybeHeadHash.isEmpty())
				return Optional.empty();

			Optional<Block> maybeHead = getBlock(txn, maybeHeadHash.get());
			if (maybeHead.isPresent())
				return maybeHead;
			else
				throw new DatabaseException("the head hash is set but it is not in the database");
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private Optional<GenesisBlock> getGenesis(Transaction txn) throws NoSuchAlgorithmException, DatabaseException {
		try {
			Optional<byte[]> maybeGenesisHash = getGenesisHash(txn);
			if (maybeGenesisHash.isEmpty())
				return Optional.empty();

			Optional<Block> maybeGenesis = getBlock(txn, maybeGenesisHash.get());
			if (maybeGenesis.isPresent()) {
				if (maybeGenesis.get() instanceof GenesisBlock gb)
					return Optional.of(gb);
				else
					throw new DatabaseException("the genesis hash is set but it refers to a non-genesis block in the database");
			}
			else
				throw new DatabaseException("the genesis hash is set but it is not in the database");
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private void putInStore(Transaction txn, Block block, byte[] hashOfBlock, byte[] bytesOfBlock) {
		storeOfBlocks.put(txn, fromBytes(hashOfBlock), fromBytes(bytesOfBlock));
	}

	private boolean containsBlock(Transaction txn, byte[] hashOfBlock) {
		return storeOfBlocks.get(txn, fromBytes(hashOfBlock)) != null;
	}

	private boolean updateHead(Transaction txn, NonGenesisBlock block, byte[] hashOfBlock) throws DatabaseException, NoSuchAlgorithmException {
		Optional<BigInteger> maybePowerOfHead = getPowerOfHead(txn);
		if (maybePowerOfHead.isEmpty())
			throw new DatabaseException("the database of blocks is non-empty but the power of the head is not set");

		// we choose the branch with more power
		if (block.getPower().compareTo(maybePowerOfHead.get()) > 0) {
			setHeadHash(txn, block, hashOfBlock);
			return true;
		}
		else
			return false;
	}

	/**
	 * Adds a block to the tree of blocks rooted at the genesis block (if any), running
	 * inside a given transaction. It updates the references to the genesis and to the
	 * head of the longest chain, if needed.
	 * 
	 * @param txn the transaction
	 * @param block the block to add
	 * @param updatedHead the new head resulting from the addition, if it changed wrt the previous head;
	 *                    otherwise it is left unchanged
	 * @return true if and only if the block has been added. False means that
	 *         the block was already in the tree; or that {@code block} is a genesis
	 *         block and there is already a genesis block in the tree; or that {@code block}
	 *         is a non-genesis block whose previous is not in the tree
	 * @throws NoSuchAlgorithmException if some block uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	private boolean add(Transaction txn, Block block, AtomicReference<Block> updatedHead) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		byte[] bytesOfBlock = block.toByteArray(), hashOfBlock = hashingForBlocks.hash(bytesOfBlock);

		if (containsBlock(txn, hashOfBlock)) {
			LOGGER.warning("not adding block " + block.getHexHash(hashingForBlocks) + " since it is already in the database");
			return false;
		}
		else if (block instanceof NonGenesisBlock ngb) {
			if (containsBlock(txn, ngb.getHashOfPreviousBlock())) {
				putInStore(txn, block, hashOfBlock, bytesOfBlock);
				addToForwards(txn, ngb, hashOfBlock);
				if (updateHead(txn, ngb, hashOfBlock))
					updatedHead.set(ngb);

				node.submit(new BlockAddedEvent(ngb));
				return true;
			}
			else {
				LOGGER.warning("not adding block " + block.getHexHash(hashingForBlocks) + " since its previous block is not in the database");
				return false;
			}
		}
		else {
			if (getGenesisHash(txn).isEmpty()) {
				putInStore(txn, block, hashOfBlock, bytesOfBlock);
				setGenesisHash(txn, (GenesisBlock) block, hashOfBlock);
				setHeadHash(txn, block, hashOfBlock);
				updatedHead.set(block);
				node.submit(new BlockAddedEvent(block));
				return true;
			}
			else {
				LOGGER.warning("not adding genesis block " + block.getHexHash(hashingForBlocks) + " since the database already contains a genesis block");
				return false;
			}
		}
	}

	private void setGenesisHash(Transaction txn, GenesisBlock newGenesis, byte[] newGenesisHash) {
		storeOfBlocks.put(txn, genesis, fromBytes(newGenesisHash));
		LOGGER.info("height " + newGenesis.getHeight() + ": block " + newGenesis.getHexHash(hashingForBlocks) + " set as genesis");
	}

	private void setHeadHash(Transaction txn, Block newHead, byte[] newHeadHash) throws NoSuchAlgorithmException, DatabaseException {
		storeOfBlocks.put(txn, head, fromBytes(newHeadHash));
		storeOfBlocks.put(txn, power, fromBytes(newHead.getPower().toByteArray()));
		storeOfBlocks.put(txn, height, fromBytes(longToBytes(newHead.getHeight())));
		updateChain(txn, newHead, newHeadHash);
		LOGGER.info("height " + newHead.getHeight() + ": block " + newHead.getHexHash(hashingForBlocks) + " set as head");
	}

	private void updateChain(Transaction txn, Block block, byte[] blockHash) throws NoSuchAlgorithmException, DatabaseException {
		long height = block.getHeight();
		var heightBI = fromBytes(longToBytes(height));
		var _new = fromBytes(blockHash);
		var old = storeOfChain.get(txn, heightBI);

		do {
			storeOfChain.put(txn, heightBI, _new);

			if (block instanceof NonGenesisBlock ngb) {
				if (height <= 0L)
					throw new DatabaseException("The current best chain contains the non-genesis block " + Hex.toHexString(blockHash) + " at height " + height);

				byte[] hashOfPrevious = ngb.getHashOfPreviousBlock();
				Optional<Block> previous = getBlock(txn, hashOfPrevious);
				if (previous.isEmpty())
					throw new DatabaseException("Block " + Hex.toHexString(blockHash) + " has no previous block in the database");

				blockHash = hashOfPrevious;
				block = previous.get();
				height--;
				heightBI = fromBytes(longToBytes(height));
				_new = fromBytes(blockHash);
				old = storeOfChain.get(txn, heightBI);
			}
			else if (height > 0L)
				throw new DatabaseException("The current best chain contains the genesis block " + Hex.toHexString(blockHash) + " at height " + height);
		}
		while (block instanceof NonGenesisBlock && !_new.equals(old));
	}

	private static byte[] longToBytes(long l) {
		var result = new byte[Long.BYTES];

		for (int i = Long.BYTES - 1; i >= 0; i--) {
	        result[i] = (byte) (l & 0xFF);
	        l >>= Byte.SIZE;
	    }

	    return result;
	}

	private static long bytesToLong(final byte[] b) {
	    long result = 0;

	    for (int i = 0; i < Long.BYTES; i++) {
	        result <<= Byte.SIZE;
	        result |= (b[i] & 0xFF);
	    }

	    return result;
	}

	private ChainInfo getChainInfo(Transaction txn) throws DatabaseException {
		var maybeGenesisHash = getGenesisHash(txn);
		if (maybeGenesisHash.isEmpty())
			return ChainInfos.of(0L, Optional.empty(), Optional.empty());

		var maybeHeadHash = getHeadHash(txn);
		if (maybeHeadHash.isEmpty())
			throw new DatabaseException("The hash of the genesis is set but there is no head hash set in the database");

		OptionalLong chainHeight = getHeightOfHead(txn);
		if (chainHeight.isEmpty())
			throw new DatabaseException("The hash of the genesis is set but the height of the current best chain is missing");

		return ChainInfos.of(chainHeight.getAsLong(), maybeGenesisHash, maybeHeadHash);
	}

	private Stream<byte[]> getChain(Transaction txn, long start, long count) throws DatabaseException {
		if (start < 0L || count <= 0L)
			return Stream.empty();

		OptionalLong chainHeight = getHeightOfHead(txn);
		if (chainHeight.isEmpty())
			return Stream.empty();

		ByteIterable[] hashes = LongStream.range(start, Math.min(start + count, chainHeight.getAsLong() + 1))
			.mapToObj(height -> storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(height))))
			.toArray(ByteIterable[]::new);

		if (Stream.of(hashes).anyMatch(Objects::isNull))
			throw new DatabaseException("The current best chain contains a missing element");

		return Stream.of(hashes).map(ByteIterable::getBytes);
	}

	private void addToForwards(Transaction txn, NonGenesisBlock block, byte[] hashOfBlockToAdd) {
		var hashOfPrevious = fromBytes(block.getHashOfPreviousBlock());
		var oldForwards = storeOfForwards.get(txn, hashOfPrevious);
		var newForwards = fromBytes(oldForwards != null ? concat(oldForwards.getBytes(), hashOfBlockToAdd) : hashOfBlockToAdd);
		storeOfForwards.put(txn, hashOfPrevious, newForwards);
	}

	private static byte[] concat(byte[] array1, byte[] array2) {
		var merge = new byte[array1.length + array2.length];
		System.arraycopy(array1, 0, merge, 0, array1.length);
		System.arraycopy(array2, 0, merge, array1.length, array2.length);
		return merge;
	}

	private void initialize() throws DatabaseException, AlreadyInitializedException {
		try {
			if (getGenesisHash().isPresent())
				throw new AlreadyInitializedException("init cannot be required for an already initialized blockchain");

			var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(node.getConfig().initialAcceleration));
			add(genesis, new AtomicReference<>());
		}
		catch (NoSuchAlgorithmException | ClosedDatabaseException e) {
			// the database cannot be closed at this moment
			// moreover, if the database is empty, there is no way it can contain a non-genesis block (that contains a hashing algorithm)
			LOGGER.log(Level.SEVERE, "unexpected exception", e);
			throw new RuntimeException("unexpected exception", e);
		}
	}
}
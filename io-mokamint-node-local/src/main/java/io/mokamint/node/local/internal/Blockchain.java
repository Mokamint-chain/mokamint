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

import static io.hotmoka.exceptions.CheckSupplier.check;
import static io.hotmoka.exceptions.UncheckFunction.uncheck;
import static io.hotmoka.xodus.ByteIterable.fromByte;
import static io.hotmoka.xodus.ByteIterable.fromBytes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.closeables.AbstractAutoCloseableWithLock;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.exceptions.CheckSupplier;
import io.hotmoka.exceptions.UncheckFunction;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.UnmarshallingContexts;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.xodus.ByteIterable;
import io.hotmoka.xodus.ExodusException;
import io.hotmoka.xodus.env.Environment;
import io.hotmoka.xodus.env.Store;
import io.hotmoka.xodus.env.Transaction;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.DatabaseException;
import io.mokamint.node.TransactionAddresses;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.nonce.api.DeadlineValidityCheckException;

/**
 * The blockchain is a database where the blocks are persisted. Blocks are rooted at a genesis block
 * and do not necessarily form a list but are in general a tree.
 */
public class Blockchain extends AbstractAutoCloseableWithLock<ClosedDatabaseException> {

	/**
	 * The node using this blockchain.
	 */
	private final LocalNodeImpl node;

	/**
	 * The Xodus environment that holds the database of blocks.
	 */
	private final Environment environment;

	/**
	 * The Xodus store that holds the blocks, of all chains. It maps the hash of each block to that block.
	 */
	private final Store storeOfBlocks;

	/**
	 * The Xodus store that maps the hash of each block to the immediate successor(s) of that block.
	 */
	private final Store storeOfForwards;

	/**
	 * The Xodus store that holds the list of hashes of the blocks in the current best chain.
	 * It maps 0 to the genesis block, 1 to the block at height 1 in the current best chain and so on.
	 */
	private final Store storeOfChain;

	/**
	 * The Xodus store that holds the position of the transactions in the current best chain.
	 * It maps the hash of each transaction in the chain to a pair consisting
	 * of the height of the block in the current chain where the transaction is contained
	 * and of the progressive number inside the table of transactions in that block.
	 */
	private final Store storeOfTransactions;

	/**
	 * The hashing used for the blocks in the node.
	 */
	private final HashingAlgorithm hashingForBlocks;

	/**
	 * A hasher for the transactions.
	 */
	private final Hasher<io.mokamint.node.api.Transaction> hasherForTransactions;

	/**
	 * The maximal time (in milliseconds) that history can be changed before being considered as definitely frozen;
	 * a negative value means that the history is always allowed to be changed, without limits.
	 */
	private final long maximalHistoryChangeTime;

	/**
	 * A cache for the hash of the genesis block, if it has been set already. Otherwise it holds {@code null}.
	 */
	private volatile Optional<byte[]> genesisHashCache;

	/**
	 * A cache for the genesis block, if it has been set already. Otherwise it holds {@code null}.
	 */
	private volatile Optional<GenesisBlock> genesisCache;

	/**
	 * A buffer where blocks without a known previous block are parked, in case their previous block arrives later.
	 */
	@GuardedBy("itself")
	private final NonGenesisBlock[] orphans;

	/**
	 * The next insertion position inside the {@link #orphans} array.
	 */
	@GuardedBy("orphans")
	private int orphansPos;

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the genesis block.
	 */
	private final static ByteIterable GENESIS = fromByte((byte) 0);

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the head block.
	 */
	private final static ByteIterable HASH_OF_HEAD = fromByte((byte) 1);

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the power of the head block.
	 */
	private final static ByteIterable POWER_OF_HEAD = fromByte((byte) 2);

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the height of the current best chain.
	 */
	private final static ByteIterable HEIGHT_OF_HEAD = fromByte((byte) 3);

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the state id of the head block.
	 */
	private final static ByteIterable STATE_ID_OF_HEAD = fromByte((byte) 4);

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the hash of the block where
	 * the non-frozen part of the blockchain starts.
	 */
	private final static ByteIterable HASH_OF_START_OF_NON_FROZEN_PART = fromByte((byte) 5);

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the total waiting time of the block where
	 * the non-frozen part of the blockchain starts.
	 */
	private final static ByteIterable TOTAL_WAITING_TIME_OF_START_OF_NON_FROZEN_PART = fromByte((byte) 6);

	private final static Logger LOGGER = Logger.getLogger(Blockchain.class.getName());

	/**
	 * Creates a blockchain, by opening the database of blocks.
	 * 
	 * @throws DatabaseException if the database is corrupted
	 */
	public Blockchain(LocalNodeImpl node) throws DatabaseException {
		super(ClosedDatabaseException::new);

		this.node = node;
		LocalNodeConfig config = node.getConfig();
		this.hashingForBlocks = config.getHashingForBlocks();
		this.hasherForTransactions = config.getHashingForTransactions().getHasher(io.mokamint.node.api.Transaction::toByteArray);
		this.maximalHistoryChangeTime = config.getMaximalHistoryChangeTime();
		this.orphans = new NonGenesisBlock[config.getOrphansMemorySize()];
		this.environment = createBlockchainEnvironment();
		this.storeOfBlocks = openStore("blocks");
		this.storeOfForwards = openStore("forwards");
		this.storeOfChain = openStore("chain");
		this.storeOfTransactions = openStore("transactions");
	}

	@Override
	public void close() throws InterruptedException {
		if (stopNewCalls()) {
			try {
				environment.close(); // the lock guarantees that there are no unfinished transactions at this moment
				LOGGER.info("blockchain: closed the blocks database");
			}
			catch (ExodusException e) {
				// TODO
				// not sure while this happens, it seems there might be transactions run for garbage collection,
				// that will consequently find a closed environment
				LOGGER.log(Level.WARNING, "blockchain: failed to close the blocks database", e);
				//throw new DatabaseException("Cannot close the blocks database", e);
			}
		}
	}

	/**
	 * Determines if this blockchain is empty.
	 * 
	 * @return true if and only if this blockchain is empty
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean isEmpty() throws DatabaseException, ClosedDatabaseException {
		try (var scope = mkScope()) {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::isEmpty)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the hash of the genesis block in this blockchain, if any.
	 * 
	 * @return the hash of the genesis block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<byte[]> getGenesisHash() throws DatabaseException, ClosedDatabaseException {
		// we use a cache to avoid repeated access for reading the hash of the genesis block, that is not allowed to change after being set
		if (genesisHashCache != null)
			return Optional.of(genesisHashCache.get().clone());

		Optional<byte[]> result;

		try (var scope = mkScope()) {
			result = check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getGenesisHash)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}

		if (result.isPresent())
			genesisHashCache = Optional.of(result.get().clone());

		return result;
	}

	/**
	 * Yields the genesis block in this blockchain, if any.
	 * 
	 * @return the genesis block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<GenesisBlock> getGenesis() throws DatabaseException, ClosedDatabaseException {
		// we use a cache to avoid repeated access for reading the genesis block, that is not allowed to change after being set
		if (genesisCache != null)
			return genesisCache;

		Optional<GenesisBlock> result;

		try (var scope = mkScope()) {
			result = check(DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(this::getGenesis))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}

		if (result.isPresent())
			genesisCache = Optional.of(result.get());

		return result;
	}

	/**
	 * Yields the head of the best chain in this blockchain, if any.
	 * 
	 * @return the head block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<Block> getHead() throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		try (var scope = mkScope()) {
			return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(this::getHead))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the hash of the head of the best chain in this blockchain, if any.
	 * 
	 * @return the hash of the head block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<byte[]> getHeadHash() throws DatabaseException, ClosedDatabaseException {
		try (var scope = mkScope()) {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getHeadHash)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
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
		try (var scope = mkScope()) {
			return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(this::getStartOfNonFrozenPart))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the limit time used to deliver the transactions in the non-frozen part of the history.
	 * Transactions delivered before that time have generated stores that can be considered as frozen.
	 * 
	 * @return the limit time; this is empty if the database is empty
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing or signature algorithm
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<LocalDateTime> getStartingTimeOfNonFrozenHistory() throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		try (var scope = mkScope()) {
			return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(this::getStartingTimeOfNonFrozenHistory))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the height of the head of the best chain in this blockchain, if any.
	 * 
	 * @return the height of the head block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public OptionalLong getHeightOfHead() throws DatabaseException, ClosedDatabaseException {
		try (var scope = mkScope()) {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getHeightOfHead)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the power of the head of the best chain in this blockchain, if any.
	 * 
	 * @return the power of the head block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<BigInteger> getPowerOfHead() throws DatabaseException, ClosedDatabaseException {
		try (var scope = mkScope()) {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getPowerOfHead)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
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
		try (var scope = mkScope()) {
			return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(txn -> getBlock(txn, hash)))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
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
		try (var scope = mkScope()) {
			return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(txn -> getBlockDescription(txn, hash)))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the transaction with the given hash, if it is contained in some block of the best chain of this blockchain.
	 * 
	 * @param hash the hash of the transaction to search
	 * @return the transaction, if any
	 * @throws NoSuchAlgorithmException if the block containing the transaction refers to an unknown hashing or signature algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<io.mokamint.node.api.Transaction> getTransaction(byte[] hash) throws ClosedDatabaseException, DatabaseException, NoSuchAlgorithmException {
		try (var scope = mkScope()) {
			return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(txn -> getTransaction(txn, hash)))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the address of the transaction with the given hash, if it is contained in some block
	 * of the best chain of this blockchain.
	 * 
	 * @param hash the hash of the transaction to search
	 * @return the transaction address, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<TransactionAddress> getTransactionAddress(byte[] hash) throws ClosedDatabaseException, DatabaseException {
		try (var scope = mkScope()) {
			return check(DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(txn -> getTransactionAddress(txn, hash)))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the address of the transaction with the given hash, if it is contained in the
	 * chain from the given {@code block} towards the genesis block.
	 * 
	 * @param block the block
	 * @param hash the hash of the transaction to search
	 * @return the transaction address, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database refers to an unknown hashing algorithm
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<TransactionAddress> getTransactionAddress(Block block, byte[] hash) throws ClosedDatabaseException, DatabaseException, NoSuchAlgorithmException {
		try (var scope = mkScope()) {
			return check(DatabaseException.class, NoSuchAlgorithmException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(txn -> getTransactionAddress(txn, block, hash)))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields information about the current best chain of this blockchain.
	 * 
	 * @return the information
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public ChainInfo getChainInfo() throws DatabaseException, ClosedDatabaseException {
		try (var scope = mkScope()) {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getChainInfo)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
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
	public Stream<byte[]> getChain(long start, int count) throws DatabaseException, ClosedDatabaseException {
		try (var scope = mkScope()) {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(txn -> getChain(txn, start, count))));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
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
		try (var scope = mkScope()) {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(txn -> containsBlock(txn, hash))));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
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
		return getPowerOfHead().map(power -> power.compareTo(block.getDescription().getPower()) < 0).orElse(true);
	}

	/**
	 * Yields the creation time of the given block, if it were to be added to this blockchain.
	 * For genesis blocks, their creation time is explicit in the block.
	 * For non-genesis blocks, this method adds the total waiting time of the block to the time of the genesis
	 * of this blockchain.
	 * 
	 * @param block the block whose creation time is computed
	 * @return the creation time of {@code block}; this is empty only for non-genesis blocks if the blockchain is empty
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 */
	public Optional<LocalDateTime> creationTimeOf(Block block) throws DatabaseException, ClosedDatabaseException {
		if (block instanceof GenesisBlock gb)
			return Optional.of(gb.getStartDateTimeUTC());
		else {
			var maybeGenesis = getGenesis();
			if (maybeGenesis.isEmpty())
				return Optional.empty();
			else
				return Optional.of(maybeGenesis.get().getStartDateTimeUTC().plus(block.getDescription().getTotalWaitingTime(), ChronoUnit.MILLIS));
		}
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
	 * @throws NodeException if the node is misbehaving
	 */
	public void initialize() throws DatabaseException, AlreadyInitializedException, InvalidKeyException, SignatureException, TimeoutException, InterruptedException, ApplicationException, NodeException {
		try {
			if (!isEmpty())
				throw new AlreadyInitializedException("Initialization cannot be required for an already initialized blockchain");
	
			var config = node.getConfig();
			var keys = node.getKeys();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), keys.getPublic());
			var genesis = Blocks.genesis(description, node.getApplication().getInitialStateId(), keys.getPrivate());
	
			addVerified(genesis);
		}
		catch (NoSuchAlgorithmException | ClosedDatabaseException e) {
			// the database cannot be closed at this moment
			LOGGER.log(Level.SEVERE, "blockchain: unexpected exception", e);
			throw new NodeException(e);
		}
	}

	/**
	 * If the block was already in the database, this method performs nothing. Otherwise, it verifies
	 * the given block and adds it to the database of blocks of this blockchain. Note that the addition
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
	 * @throws NodeException if the node is misbehaving
	 */
	public boolean add(Block block) throws DatabaseException, NoSuchAlgorithmException, VerificationException, ClosedDatabaseException, TimeoutException, InterruptedException, DeadlineValidityCheckException, ApplicationException, NodeException {
		return add(block, true);
	}

	/**
	 * This method behaves like {@link #add(Block)} but assumes that the given block is
	 * verified, so that it does not need further verification before being added to blockchain.
	 * 
	 * @param block the block to add
	 * @return true if the block has been actually added to the tree of blocks
	 *         rooted at the genesis block, false otherwise
	 * @throws DatabaseException if the block cannot be added, because the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown cryptographic algorithm
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws TimeoutException if the application did not answer in time
	 * @throws ApplicationException if the application is not behaving correctly
	 * @throws NodeException if the node is misbehaving
	 */
	public boolean addVerified(Block block) throws DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException, TimeoutException, InterruptedException, ApplicationException, NodeException {
		try {
			return add(block, false);
		}
		catch (VerificationException | DeadlineValidityCheckException e) {
			// impossible: we did not require block verification hence these exceptions should not have been generated
			throw new RuntimeException("Unexpected exception", e);
		}
	}

	/**
	 * Adds the given block to this blockchain, allowing one to specify if block verification is required.
	 * 
	 * @param block the block to add
	 * @param verify true if and only if verification of {@code block} must be performed
	 * @return true if the block has been actually added to the tree of blocks
	 *         rooted at the genesis block, false otherwise.
	 * @throws DatabaseException if the block cannot be added, because the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown cryptographic algorithm
	 * @throws VerificationException if {@code block} cannot be added since it does not respect all consensus rules
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws TimeoutException if the application did not answer in time
	 * @throws DeadlineValidityCheckException if the validity of the deadline could not be determined
	 * @throws ApplicationException if the application is not behaving correctly
	 * @throws NodeException if the node is misbehaving
	 */
	private boolean add(Block block, boolean verify) throws DatabaseException, NoSuchAlgorithmException, VerificationException, ClosedDatabaseException, TimeoutException, InterruptedException, DeadlineValidityCheckException, ApplicationException, NodeException {
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
					if (previous.isPresent())
						added |= add(ngb, hashOfCursor, verify, previous, block == ngb, ws, updatedHead);
					else {
						putAmongOrphans(ngb);
						if (block == ngb)
							addedToOrphans = true;
					}
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

			Block cursor = newHead;
			var blocksAdded = new LinkedList<Block>();
			blocksAdded.addLast(cursor);

			while (!cursor.equals(block)) {
				if (cursor instanceof NonGenesisBlock ngb) {
					cursor = getBlock(ngb.getHashOfPreviousBlock()).orElseThrow(() -> new DatabaseException("The head has been added to a dangling path"));
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
			throws DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException, VerificationException, TimeoutException, InterruptedException, DeadlineValidityCheckException, ApplicationException, NodeException {

		try {
			if (verify)
				new BlockVerification(node, block, previous);

			if (add(block, updatedHead)) {
				node.onAdded(block);
				getOrphansWithParent(hashOfBlock).forEach(ws::add);
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
			throw new NodeException(e);
		}

		return false;
	}

	/**
	 * Adds the given block to this blockchain. If the block was already in the database, nothing happens.
	 * 
	 * @param block the block to add
	 * @param updatedHead the new head of the best chain resulting from the addition,
	 *                    if it changed wrt the previous head
	 * @return true if the block has been actually added to the database, false otherwise
	 * @throws DatabaseException if the block cannot be added, because the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	private boolean add(Block block, AtomicReference<Block> updatedHead) throws DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException {
		boolean hasBeenAdded;
	
		try (var scope = mkScope()) {
			hasBeenAdded = check(DatabaseException.class, NoSuchAlgorithmException.class, () -> environment.computeInTransaction(uncheck(txn -> add(txn, block, updatedHead))));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	
		if (hasBeenAdded)
			LOGGER.info("blockchain: height " + block.getDescription().getHeight() + ": added block " + block.getHexHash(hashingForBlocks));
	
		return hasBeenAdded;
	}

	/**
	 * Adds a block to the tree of blocks rooted at the genesis block (if any), running
	 * inside a given transaction. It updates the references to the genesis and to the
	 * head of the longest chain, if needed.
	 * 
	 * @param txn the transaction
	 * @param block the block to add
	 * @param updatedHead the new head resulting from the addition, if it changed wrt the previous head
	 * @return true if and only if the block has been added. False means that
	 *         the block was already in the tree; or that {@code block} is a genesis
	 *         block and there is already a genesis block in the tree; or that {@code block}
	 *         is a non-genesis block whose previous is not in the tree
	 * @throws NoSuchAlgorithmException if some block uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 */
	private boolean add(Transaction txn, Block block, AtomicReference<Block> updatedHead) throws NoSuchAlgorithmException, DatabaseException {
		byte[] hashOfBlock = block.getHash(hashingForBlocks);

		if (containsBlock(txn, hashOfBlock)) {
			LOGGER.warning("blockchain: not adding block " + Hex.toHexString(hashOfBlock) + " since it is already in the database");
			return false;
		}
		else if (block instanceof NonGenesisBlock ngb) {
			var maybeDescriptionOfPreviousOfBlock = getBlockDescription(txn, ngb.getHashOfPreviousBlock());

			if (maybeDescriptionOfPreviousOfBlock.isPresent()) {
				if (isInFrozenPart(txn, maybeDescriptionOfPreviousOfBlock.get())) {
					LOGGER.warning("blockchain: not adding block " + Hex.toHexString(hashOfBlock) + " since its previous block is in the frozen part of the blockchain");
					return false;
				}

				putInStore(txn, hashOfBlock, block.toByteArray());
				addToForwards(txn, ngb, hashOfBlock);
				if (isBetterThanHead(txn, ngb, hashOfBlock)) {
					setHead(txn, block, hashOfBlock);
					updatedHead.set(block);
				}

				return true;
			}
			else {
				LOGGER.warning("blockchain: not adding block " + Hex.toHexString(hashOfBlock) + " since its previous block is not in the database");
				return false;
			}
		}
		else {
			if (isEmpty(txn)) {
				putInStore(txn, hashOfBlock, block.toByteArray());
				setGenesisHash(txn, hashOfBlock);
				setHead(txn, block, hashOfBlock);
				updatedHead.set(block);
				return true;
			}
			else {
				LOGGER.warning("blockchain: not adding genesis block " + Hex.toHexString(hashOfBlock) + " since the database already contains a genesis block");
				return false;
			}
		}
	}

	private boolean isInFrozenPart(Transaction txn, BlockDescription blockDescription) throws NoSuchAlgorithmException, DatabaseException {
		var totalWaitingTimeOfStartOfNonFrozenPart = getTotalWaitingTimeOfStartOfNonFrozenPart(txn);
		return totalWaitingTimeOfStartOfNonFrozenPart.isPresent() && totalWaitingTimeOfStartOfNonFrozenPart.getAsLong() > blockDescription.getTotalWaitingTime();
	}

	private Environment createBlockchainEnvironment() {
		var env = new Environment(node.getConfig().getDir().resolve("blocks").toString());
		LOGGER.info("blockchain: opened the blocks database");
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

		LOGGER.info("blockchain: opened the store of " + name + " inside the blocks database");
		return store.get();
	}

	/**
	 * Yields the hash of the head block of the blockchain in this database, if it has been set already,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the hash of the head block, if any
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<byte[]> getHeadHash(Transaction txn) throws DatabaseException {
		try {
			return Optional.ofNullable(storeOfBlocks.get(txn, HASH_OF_HEAD)).map(ByteIterable::getBytes);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the state identifier of yje head block of the blockchain in this database, if it has been set already,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the state identifier, if any
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<byte[]> getStateIdOfHead(Transaction txn) throws DatabaseException {
		try {
			return Optional.ofNullable(storeOfBlocks.get(txn, STATE_ID_OF_HEAD)).map(ByteIterable::getBytes);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the hash of the starting block of the non-frozen part of the blockchain, if it has been set already,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the hash of the starting block, if any
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<byte[]> getStartOfNonFrozenPartHash(Transaction txn) throws DatabaseException {
		try {
			return Optional.ofNullable(storeOfBlocks.get(txn, HASH_OF_START_OF_NON_FROZEN_PART)).map(ByteIterable::getBytes);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the limit time used to deliver the transactions in the non-frozen part of the history,
	 * running inside a Xodus transaction.
	 * Application transactions delivered before that time have generated stores that can be considered as frozen.
	 * 
	 * @param txn the Xodus transaction where the operation is performed
	 * @return the limit time; this is empty if the database is empty
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing or signature algorithm
	 */
	private Optional<LocalDateTime> getStartingTimeOfNonFrozenHistory(Transaction txn) throws DatabaseException, NoSuchAlgorithmException {
		try {
			Optional<byte[]> maybeStartOfNonFrozenPartHash = getStartOfNonFrozenPartHash(txn);
			if (maybeStartOfNonFrozenPartHash.isEmpty())
				return Optional.empty();

			Block startOfNonFrozenPart = getBlock(txn, maybeStartOfNonFrozenPartHash.get())
				.orElseThrow(() -> new DatabaseException("The hash of the start of the non-frozen part of the blockchain is set but its block cannot be found in the database"));

			if (startOfNonFrozenPart instanceof GenesisBlock gb)
				return Optional.of(gb.getStartDateTimeUTC());

			// the transactions in a non-genesis block are delivered in a time that is that of creation of the previous block
			return Optional.of(getGenesis(txn)
					.orElseThrow(() -> new DatabaseException("The database is not empty but its genesis block is not set"))
					.getStartDateTimeUTC().plus(startOfNonFrozenPart.getDescription().getTotalWaitingTime(), ChronoUnit.MILLIS));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the hash of the genesis block in this database, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the hash of the genesis block, if any
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<byte[]> getGenesisHash(Transaction txn) throws DatabaseException {
		try {
			return Optional.ofNullable(storeOfBlocks.get(txn, GENESIS)).map(ByteIterable::getBytes);
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
			return Optional.ofNullable(storeOfBlocks.get(txn, POWER_OF_HEAD)).map(ByteIterable::getBytes).map(BigInteger::new);
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
			ByteIterable heightBI = storeOfBlocks.get(txn, HEIGHT_OF_HEAD);
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
	 * Yields the total waiting time of the start of the non-frozen part of the blockchain, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the total waiting time, if any
	 * @throws DatabaseException if the database is corrupted
	 */
	private OptionalLong getTotalWaitingTimeOfStartOfNonFrozenPart(Transaction txn) throws DatabaseException {
		try {
			ByteIterable totalWaitingTimeBI = storeOfBlocks.get(txn, TOTAL_WAITING_TIME_OF_START_OF_NON_FROZEN_PART);
			if (totalWaitingTimeBI == null)
				return OptionalLong.empty();
			else {
				long totalWaitingTime = bytesToLong(totalWaitingTimeBI.getBytes());
				if (totalWaitingTime < 0L)
					throw new DatabaseException("The database contains a negative total waiting time for the start of the non-frozen part of the blockchain");

				return OptionalLong.of(totalWaitingTime);
			}
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the hashes of the blocks that follow the block with the given hash, if any,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param hash the hash of the parent block
	 * @return the hashes
	 * @throws DatabaseException if the database is corrupted
	 */
	private Stream<byte[]> getForwards(Transaction txn, byte[] hash) throws DatabaseException {
		ByteIterable forwards;

		try {
			forwards = storeOfForwards.get(txn, fromBytes(hash));
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
	 * @throws NoSuchAlgorithmException if the block uses an unknown hashing or signature algorithm
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<Block> getBlock(Transaction txn, byte[] hash) throws NoSuchAlgorithmException, DatabaseException {
		try {
			ByteIterable blockBI = storeOfBlocks.get(txn, fromBytes(hash));
			if (blockBI == null)
				return Optional.empty();
			
			try (var bais = new ByteArrayInputStream(blockBI.getBytes()); var context = UnmarshallingContexts.of(bais)) {
				return Optional.of(Blocks.from(context));
			}
		}
		catch (ExodusException | IOException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the description of the block with the given hash, if it is contained in this database,
	 * running inside the given transaction.
	 * 
	 * @param txn the transaction
	 * @param hash the hash
	 * @return the description of the block, if any
	 * @throws NoSuchAlgorithmException if the block uses an unknown hashing or signature algorithm
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<BlockDescription> getBlockDescription(Transaction txn, byte[] hash) throws NoSuchAlgorithmException, DatabaseException {
		try {
			ByteIterable blockBI = storeOfBlocks.get(txn, fromBytes(hash));
			if (blockBI == null)
				return Optional.empty();
			
			try (var bais = new ByteArrayInputStream(blockBI.getBytes()); var context = UnmarshallingContexts.of(bais)) {
				// the marshalling of a block starts with that of its description
				return Optional.of(BlockDescriptions.from(context));
			}
		}
		catch (ExodusException | IOException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the transaction with the given hash, if it is contained in some block of the best chain of this database,
	 * running inside the given transaction.
	 * 
	 * @param txn the database transaction
	 * @param hash the hash of the transaction to search
	 * @return the transaction, if any
	 * @throws NoSuchAlgorithmException if the block containing the transaction refers to an unknown hashing or signature algorithm
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<io.mokamint.node.api.Transaction> getTransaction(Transaction txn, byte[] hash) throws DatabaseException, NoSuchAlgorithmException {
		try {
			ByteIterable txBI = storeOfTransactions.get(txn, fromBytes(hash));
			if (txBI == null)
				return Optional.empty();

			var ref = TransactionRef.from(txBI);
			ByteIterable blockHash = storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(ref.height)));
			if (blockHash == null)
				throw new DatabaseException("The hash of the block of the best chain at height " + ref.height + " is not in the database");

			Optional<Block> block = getBlock(txn, blockHash.getBytes());
			if (block.isEmpty())
				throw new DatabaseException("The current best chain misses the block at height " + ref.height  + " with hash " + Hex.toHexString(blockHash.getBytes()));

			if (block.get() instanceof NonGenesisBlock ngb)
				return Optional.of(ngb.getTransaction(ref.progressive));
			else
				throw new DatabaseException("Transaction " + Hex.toHexString(hash) + " should be contained in a genesis block, which is impossible");
		}
		catch (ExodusException | IOException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the address of the transaction with the given hash, if it is contained in some block
	 * of the best chain of this database, running inside the given transaction.
	 * 
	 * @param txn the database transaction
	 * @param hash the hash of the transaction to search
	 * @return the transaction address, if any
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<TransactionAddress> getTransactionAddress(Transaction txn, byte[] hash) throws DatabaseException {
		try {
			ByteIterable txBI = storeOfTransactions.get(txn, fromBytes(hash));
			if (txBI == null)
				return Optional.empty();

			var ref = TransactionRef.from(txBI);
			ByteIterable blockHash = storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(ref.height)));
			if (blockHash == null)
				throw new DatabaseException("The hash of the block of the best chain at height " + ref.height + " is not in the database");

			return Optional.of(TransactionAddresses.of(blockHash.getBytes(), ref.progressive));
		}
		catch (ExodusException | IOException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the address of the transaction with the given hash, if it is contained in the
	 * chain from the given {@code block} towards the genesis block.
	 * 
	 * @param txn the database transaction
	 * @param block the block
	 * @param hash the hash of the transaction to search
	 * @return the transaction address, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database refers to an unknown cryptographic algorithm
	 */
	private Optional<TransactionAddress> getTransactionAddress(Transaction txn, Block block, byte[] hash) throws DatabaseException, NoSuchAlgorithmException {
		try {
			byte[] hashOfBlock = block.getHash(hashingForBlocks);
			String initialHash = Hex.toHexString(hashOfBlock);

			while (true) {
				if (isContainedInTheBestChain(txn, block, hashOfBlock)) {
					// since the block is inside the best chain, we can use the storeOfTransactions for it
					ByteIterable txBI = storeOfTransactions.get(txn, fromBytes(hash));
					if (txBI == null)
						return Optional.empty();

					var ref = TransactionRef.from(txBI);
					if (ref.height > block.getDescription().getHeight())
						// the transaction is present above the block
						return Optional.empty();

					ByteIterable blockHash = storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(ref.height)));
					if (blockHash == null)
						throw new DatabaseException("The hash of the block of the best chain at height " + ref.height + " is not in the database");

					return Optional.of(TransactionAddresses.of(blockHash.getBytes(), ref.progressive));
				}
				else if (block instanceof NonGenesisBlock ngb) {
					// we check if the transaction is inside the table of transactions of the block
					int length = ngb.getTransactionsCount();
					for (int pos = 0; pos < length; pos++)
						if (Arrays.equals(hash, hasherForTransactions.hash(ngb.getTransaction(pos))))
							return Optional.of(TransactionAddresses.of(hashOfBlock, pos));

					// otherwise we continue with the previous block
					byte[] hashOfPrevious = ngb.getHashOfPreviousBlock();
					block = getBlock(txn, hashOfPrevious).orElseThrow(() -> new DatabaseException("The database has no block with hash " + Hex.toHexString(hashOfPrevious)));
					hashOfBlock = hashOfPrevious;
				}
				else
					throw new DatabaseException("The block " + initialHash + " is not connected to the best chain");
			}
		}
		catch (ExodusException | IOException e) {
			throw new DatabaseException(e);
		}
	}

	private boolean isContainedInTheBestChain(Transaction txn, Block block, byte[] blockHash) {
		var height = block.getDescription().getHeight();
		var hashOfBlockFromBestChain = storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(height)));
		return hashOfBlockFromBestChain != null && Arrays.equals(hashOfBlockFromBestChain.getBytes(), blockHash);
	}

	/**
	 * Determines if this blockchain is empty, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return true if and only if this blockchain is empty
	 * @throws DatabaseException if the database is corrupted
	 */
	private boolean isEmpty(Transaction txn) throws DatabaseException {
		return getGenesisHash(txn).isEmpty();
	}

	/**
	 * Yields the head of the best chain in this database, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the head, if any
	 * @throws NoSuchAlgorithmException if the head uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<Block> getHead(Transaction txn) throws NoSuchAlgorithmException, DatabaseException {
		try {
			Optional<byte[]> maybeHeadHash = getHeadHash(txn);
			if (maybeHeadHash.isEmpty())
				return Optional.empty();

			Optional<Block> maybeHead = getBlock(txn, maybeHeadHash.get());
			if (maybeHead.isPresent())
				return maybeHead;
			else
				throw new DatabaseException("The head hash is set but it is not in the database");
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the starting block of the non-frozen part of the history of the blockchain, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the starting block, if any
	 * @throws NoSuchAlgorithmException if the starting block uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<Block> getStartOfNonFrozenPart(Transaction txn) throws NoSuchAlgorithmException, DatabaseException {
		try {
			Optional<byte[]> maybeStartOfNonFrozenPartHash = getStartOfNonFrozenPartHash(txn);
			if (maybeStartOfNonFrozenPartHash.isEmpty())
				return Optional.empty();

			Optional<Block> maybeStartOfNonFrozenPart = getBlock(txn, maybeStartOfNonFrozenPartHash.get());
			if (maybeStartOfNonFrozenPart.isPresent())
				return maybeStartOfNonFrozenPart;
			else
				throw new DatabaseException("The hash of the start of non-frozen part of the blockchain is set but it is not in the database");
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the genesis block in this database, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the genesis block, if any
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<GenesisBlock> getGenesis(Transaction txn) throws DatabaseException {
		try {
			Optional<byte[]> maybeGenesisHash = getGenesisHash(txn);
			if (maybeGenesisHash.isEmpty())
				return Optional.empty();

			Optional<Block> maybeGenesis = getBlock(txn, maybeGenesisHash.get());
			if (maybeGenesis.isPresent()) {
				if (maybeGenesis.get() instanceof GenesisBlock gb)
					return Optional.of(gb);
				else
					throw new DatabaseException("The genesis hash is set but it refers to a non-genesis block in the database");
			}
			else
				throw new DatabaseException("The genesis hash is set but it is not in the database");
		}
		catch (NoSuchAlgorithmException e) {
			// getBlock throws NoSuchAlgorithmException only for non-genesis blocks
			throw new DatabaseException("The genesis hash is set but it refers to a non-genesis block in the database", e);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Adds to the database a block with a given hash, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param hashOfBlock the hash of the block
	 * @param bytesOfBlock the bytes of the block
	 * @throws DatabaseException if the database is corrupted
	 */
	private void putInStore(Transaction txn, byte[] hashOfBlock, byte[] bytesOfBlock) throws DatabaseException {
		try {
			storeOfBlocks.put(txn, fromBytes(hashOfBlock), fromBytes(bytesOfBlock));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Determines if this database contains a block with the given hash, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param hashOfBlock the hash
	 * @return true if and only if that condition holds
	 * @throws DatabaseException if the database is corrupted
	 */
	private boolean containsBlock(Transaction txn, byte[] hashOfBlock) throws DatabaseException {
		try {
			return storeOfBlocks.get(txn, fromBytes(hashOfBlock)) != null;
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private boolean isBetterThanHead(Transaction txn, NonGenesisBlock block, byte[] hashOfBlock) throws DatabaseException, NoSuchAlgorithmException {
		BigInteger powerOfHead = getPowerOfHead(txn).orElseThrow(() -> new DatabaseException("The database of blocks is non-empty but the power of the head is not set"));

		return block.getDescription().getPower().compareTo(powerOfHead) > 0;
	}

	/**
	 * Sets the hash of the genesis block in this database, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param newGenesisHash the hash of the genesis block
	 * @throws DatabaseException if the database is corrupted
	 */
	private void setGenesisHash(Transaction txn, byte[] newGenesisHash) throws DatabaseException {
		try {
			storeOfBlocks.put(txn, GENESIS, fromBytes(newGenesisHash));
			LOGGER.info("blockchain: height 0: block " + Hex.toHexString(newGenesisHash) + " set as genesis");
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Sets the hash of the starting block of the non-frozen part of the blockchain, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param startOfNonFrozenPartHash the hash of the starting block of the non-frozen part of the blockchain
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if the start of the non-frozen part refers to an unknown hashing algorithm
	 */
	private void setStartOfNonFrozenPartHash(Transaction txn, byte[] startOfNonFrozenPartHash) throws DatabaseException, NoSuchAlgorithmException {
		try {
			storeOfBlocks.put(txn, HASH_OF_START_OF_NON_FROZEN_PART, fromBytes(startOfNonFrozenPartHash));
			var descriptionOfStartOfNonFrozenPart = getBlockDescription(txn, startOfNonFrozenPartHash).orElseThrow(() -> new DatabaseException("Trying to set the start of the non-frozen part of the blockchain to a block not present in the database"));
			storeOfBlocks.put(txn, TOTAL_WAITING_TIME_OF_START_OF_NON_FROZEN_PART, fromBytes(longToBytes(descriptionOfStartOfNonFrozenPart.getTotalWaitingTime())));
			LOGGER.info("blockchain: block " + Hex.toHexString(startOfNonFrozenPartHash) + " set as start of non-frozen part, at height " + descriptionOfStartOfNonFrozenPart.getHeight());
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Sets the head of the best chain in this database, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param newHead the new head
	 * @param newHeadHash the hash of {@code newHead}
	 * @throws NoSuchAlgorithmException if {@code newHead} uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 */
	private void setHead(Transaction txn, Block newHead, byte[] newHeadHash) throws NoSuchAlgorithmException, DatabaseException {
		try {
			updateChain(txn, newHead, newHeadHash);
			storeOfBlocks.put(txn, HASH_OF_HEAD, fromBytes(newHeadHash));
			storeOfBlocks.put(txn, STATE_ID_OF_HEAD, fromBytes(newHead.getStateId()));
			storeOfBlocks.put(txn, POWER_OF_HEAD, fromBytes(newHead.getDescription().getPower().toByteArray()));
			long heightOfHead = newHead.getDescription().getHeight();
			storeOfBlocks.put(txn, HEIGHT_OF_HEAD, fromBytes(longToBytes(heightOfHead)));
			LOGGER.info("blockchain: height " + heightOfHead + ": block " + Hex.toHexString(newHeadHash) + " set as head");
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Updates the current best chain in this database, to the chain having the given block as head,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param newHead the block that gets set as new head
	 * @param newHeadHash the hash of {@code block}
	 * @throws NoSuchAlgorithmException if some block uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 */
	private void updateChain(Transaction txn, Block newHead, byte[] newHeadHash) throws NoSuchAlgorithmException, DatabaseException {
		try {
			Block cursor = newHead;
			byte[] cursorHash = newHeadHash;
			long totalTimeOfNewHead = newHead.getDescription().getTotalWaitingTime();
			long height = cursor.getDescription().getHeight();

			removeDataHigherThan(txn, height);

			var heightBI = fromBytes(longToBytes(height));
			var _new = fromBytes(cursorHash);
			var old = storeOfChain.get(txn, heightBI);

			List<Block> addedBlocks = new ArrayList<>();

			do {
				storeOfChain.put(txn, heightBI, _new);

				if (old != null) {
					Optional<Block> oldBlock = getBlock(txn, old.getBytes());
					if (oldBlock.isEmpty())
						throw new DatabaseException("The current best chain misses the block at height " + height  + " with hash " + Hex.toHexString(old.getBytes()));

					removeReferencesToTransactionsInside(txn, oldBlock.get());
				}

				addedBlocks.add(cursor);

				if (cursor instanceof NonGenesisBlock ngb) {
					if (height <= 0L)
						throw new DatabaseException("The current best chain contains the non-genesis block " + Hex.toHexString(cursorHash) + " at height " + height);

					byte[] hashOfPrevious = ngb.getHashOfPreviousBlock();
					var cursorHashCopy = cursorHash;
					cursor = getBlock(txn, hashOfPrevious).orElseThrow(() -> new DatabaseException("Block " + Hex.toHexString(cursorHashCopy) + " has no previous block in the database"));
					cursorHash = hashOfPrevious;
					height--;
					heightBI = fromBytes(longToBytes(height));
					_new = fromBytes(cursorHash);
					old = storeOfChain.get(txn, heightBI);
				}
				else if (height > 0L)
					throw new DatabaseException("The current best chain contains a genesis block " + Hex.toHexString(cursorHash) + " at height " + height);
			}
			while (cursor instanceof NonGenesisBlock && !_new.equals(old));

			for (Block added: addedBlocks)
				addReferencesToTransactionsInside(txn, added);

			var maybeStartOfNonFrozenPartHash = getStartOfNonFrozenPartHash(txn);
			byte[] startOfNonFrozenPartHash;

			if (maybeStartOfNonFrozenPartHash.isEmpty()) {
				startOfNonFrozenPartHash = getGenesisHash(txn).orElseThrow(() -> new DatabaseException("The head has changed but the genesis hash is missing"));
				setStartOfNonFrozenPartHash(txn, startOfNonFrozenPartHash);
			}
			else
				startOfNonFrozenPartHash = maybeStartOfNonFrozenPartHash.get();

			if (maximalHistoryChangeTime >= 0L) {
				// we move startOfNonFrozenPartHash upwards along the current best chain, if it is too far away from the head of the blockchain
				do {
					var startOfNonFrozenPartHashCopy = startOfNonFrozenPartHash;
					var descriptionOfStartOfNonFrozenPart = getBlockDescription(txn, startOfNonFrozenPartHash).orElseThrow(() -> new DatabaseException("Block " + Hex.toHexString(startOfNonFrozenPartHashCopy) + " should be the start of the non-frozen part of the blockchain, but it cannot be found in the database"));

					if (totalTimeOfNewHead - descriptionOfStartOfNonFrozenPart.getTotalWaitingTime() <= maximalHistoryChangeTime)
						break;

					ByteIterable aboveStartOfNonFrozenPartHash = storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(descriptionOfStartOfNonFrozenPart.getHeight() + 1)));
					if (aboveStartOfNonFrozenPartHash == null)
						throw new DatabaseException("The block above the start of the non-frozen part of the blockchain is not in the database");

					byte[] newStartOfNonFrozenPartHash = aboveStartOfNonFrozenPartHash.getBytes();

					for (byte[] forward: getForwards(txn, startOfNonFrozenPartHash).toArray(byte[][]::new))
						if (!Arrays.equals(forward, newStartOfNonFrozenPartHash))
							gcBlocksRootedAt(txn, forward);

					setStartOfNonFrozenPartHash(txn, newStartOfNonFrozenPartHash);
					startOfNonFrozenPartHash = newStartOfNonFrozenPartHash;
				}
				while (true);
			}
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private void gcBlocksRootedAt(Transaction txn, byte[] hash) throws DatabaseException {
		var ws = new ArrayList<byte[]>();
		ws.add(hash);

		do {
			var currentHash = ws.remove(ws.size() - 1);
			getForwards(txn, currentHash).forEach(ws::add);

			var currentHashBI = fromBytes(currentHash);

			try {
				storeOfBlocks.delete(txn, currentHashBI);

				// blocks might have no forward blocks
				if (storeOfForwards.get(txn, currentHashBI) != null)
					storeOfForwards.delete(txn, currentHashBI);
			}
			catch (ExodusException e) {
				throw new DatabaseException(e);
			}
			
			LOGGER.info("blockchain: garbage-collected block " + Hex.toHexString(currentHash));
		}
		while (!ws.isEmpty());
	}

	private void removeDataHigherThan(Transaction txn, long height) throws NoSuchAlgorithmException, DatabaseException {
		Optional<Block> cursor = getHead(txn);
		Optional<byte[]> cursorHash = getHeadHash(txn);

		if (cursor.isPresent()) {
			Block block = cursor.get();
			byte[] blockHash = cursorHash.get();
			long blockHeight;

			while ((blockHeight = block.getDescription().getHeight()) > height) {
				if (block instanceof NonGenesisBlock ngb) {
					removeReferencesToTransactionsInside(txn, block);
					storeOfChain.delete(txn, ByteIterable.fromBytes(longToBytes(blockHeight)));
					byte[] hashOfPrevious = ngb.getHashOfPreviousBlock();
					Optional<Block> previous = getBlock(txn, hashOfPrevious);
					if (previous.isEmpty())
						throw new DatabaseException("Block " + Hex.toHexString(blockHash) + " has no previous block in the database");

					block = previous.get();
					blockHash = hashOfPrevious;
				}
				else
					throw new DatabaseException("The current best chain contains a genesis block " + Hex.toHexString(blockHash) + " at height " + blockHeight);
			}
		}
	}

	private void addReferencesToTransactionsInside(Transaction txn, Block block) {
		if (block instanceof NonGenesisBlock ngb) {
			long height = ngb.getDescription().getHeight();
			int count = ngb.getTransactionsCount();
			for (int pos = 0; pos < count; pos++) {
				var ref = new TransactionRef(height, pos);
				storeOfTransactions.put(txn, ByteIterable.fromBytes(hasherForTransactions.hash(ngb.getTransaction(pos))), ByteIterable.fromBytes(ref.toByteArray()));
			};
		}
	}

	private void removeReferencesToTransactionsInside(Transaction txn, Block block) {
		if (block instanceof NonGenesisBlock ngb) {
			int count = ngb.getTransactionsCount();
			for (int pos = 0; pos < count; pos++)
				storeOfTransactions.delete(txn, ByteIterable.fromBytes(hasherForTransactions.hash(ngb.getTransaction(pos))));
		}
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
			return ChainInfos.of(0L, Optional.empty(), Optional.empty(), Optional.empty());

		var maybeHeadHash = getHeadHash(txn);
		if (maybeHeadHash.isEmpty())
			throw new DatabaseException("The hash of the genesis is set but there is no head hash set in the database");

		OptionalLong maybeChainHeight = getHeightOfHead(txn);
		if (maybeChainHeight.isEmpty())
			throw new DatabaseException("The hash of the genesis is set but the height of the current best chain is missing");

		Optional<byte[]> maybeStateId = getStateIdOfHead(txn);
		if (maybeStateId.isEmpty())
			throw new DatabaseException("The hash of the genesis is set but there is no state identifier for the head set in the database");

		return ChainInfos.of(maybeChainHeight.getAsLong() + 1, maybeGenesisHash, maybeHeadHash, maybeStateId);
	}

	private Stream<byte[]> getChain(Transaction txn, long start, int count) throws DatabaseException {
		try {
			if (start < 0L || count <= 0)
				return Stream.empty();

			OptionalLong chainHeight = getHeightOfHead(txn);
			if (chainHeight.isEmpty())
				return Stream.empty();

			ByteIterable[] hashes = CheckSupplier.check(DatabaseException.class, () -> LongStream.range(start, Math.min(start + count, chainHeight.getAsLong() + 1))
				.mapToObj(height -> storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(height))))
				.map(UncheckFunction.uncheck(bi -> {
					if (bi == null)
						throw new DatabaseException("The current best chain contains a missing element");

					return bi;
				}))
				.toArray(ByteIterable[]::new));

			return Stream.of(hashes).map(ByteIterable::getBytes);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private void addToForwards(Transaction txn, NonGenesisBlock block, byte[] hashOfBlockToAdd) throws DatabaseException {
		try {
			var hashOfPrevious = fromBytes(block.getHashOfPreviousBlock());
			var oldForwards = storeOfForwards.get(txn, hashOfPrevious);
			var newForwards = fromBytes(oldForwards != null ? concat(oldForwards.getBytes(), hashOfBlockToAdd) : hashOfBlockToAdd);
			storeOfForwards.put(txn, hashOfPrevious, newForwards);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private static byte[] concat(byte[] array1, byte[] array2) {
		var merge = new byte[array1.length + array2.length];
		System.arraycopy(array1, 0, merge, 0, array1.length);
		System.arraycopy(array2, 0, merge, array1.length, array2.length);
		return merge;
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
	 * @param hashOfParent the hash of {@code parent}
	 * @return the orphans whose previous block is {@code parent}, if any
	 */
	private Stream<NonGenesisBlock> getOrphansWithParent(byte[] hashOfParent) {
		synchronized (orphans) {
			return Stream.of(orphans)
					.filter(Objects::nonNull)
					.filter(orphan -> Arrays.equals(orphan.getHashOfPreviousBlock(), hashOfParent));
		}
	}

	/**
	 * A marshallable reference to a transaction inside a block.
	 */
	private static class TransactionRef extends AbstractMarshallable {

		/**
		 * The height of the block in the current chain containing the transaction.
		 */
		private final long height;

		/**
		 * The progressive number of the transaction inside the array of transactions inside the block.
		 */
		private final int progressive;
	
		private TransactionRef(long height, int progressive) {
			this.height = height;
			this.progressive = progressive;
		}
	
		@Override
		public void into(MarshallingContext context) throws IOException {
			context.writeCompactLong(height);
			context.writeCompactInt(progressive);
		}
	
		/**
		 * Unmarshals a reference to a transaction from the given byte iterable.
		 * 
		 * @param bi the byte iterable
		 * @return the reference t a transaction
		 * @throws IOException if the reference cannot be unmarshalled
		 */
		private static TransactionRef from(ByteIterable bi) throws IOException {
			try (var bais = new ByteArrayInputStream(bi.getBytes()); var context = UnmarshallingContexts.of(bais)) {
				return new TransactionRef(context.readCompactLong(), context.readCompactInt());
			}
		}

		@Override
		public String toString() {
			return "[" + height + ", " + progressive + "]";
		}
	}
}
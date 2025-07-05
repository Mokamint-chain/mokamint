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

import static io.hotmoka.xodus.ByteIterable.fromByte;
import static io.hotmoka.xodus.ByteIterable.fromBytes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import io.hotmoka.closeables.AbstractAutoCloseableWithLock;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.hotmoka.exceptions.functions.FunctionWithExceptions3;
import io.hotmoka.exceptions.functions.FunctionWithExceptions4;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.UnmarshallingContexts;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.xodus.ByteIterable;
import io.hotmoka.xodus.ExodusException;
import io.hotmoka.xodus.env.Environment;
import io.hotmoka.xodus.env.Store;
import io.hotmoka.xodus.env.Transaction;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.Memories;
import io.mokamint.node.TransactionAddresses;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.Memory;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.api.TransactionRejectedException;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.ApplicationTimeoutException;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.BlockVerification.Mode;
import io.mokamint.node.local.internal.Mempool.TransactionEntry;

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
	 * The configuration of {@code node}.
	 */
	private final LocalNodeConfig config;

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
	private final Memory<NonGenesisBlock> orphans;

	/**
	 * The executor of synchronization tasks.
	 */
	private final ExecutorService executors = Executors.newCachedThreadPool();

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the hash of the genesis block.
	 */
	private final static ByteIterable HASH_OF_GENESIS = fromByte((byte) 0);

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the hash of the head block.
	 */
	private final static ByteIterable HASH_OF_HEAD = fromByte((byte) 1);

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the power of the head block.
	 */
	private final static ByteIterable POWER_OF_HEAD = fromByte((byte) 2);

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the height of the head block.
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
	 * Creates a blockchain.
	 * 
	 * @throws NodeException if the node is misbehaving
	 */
	public Blockchain(LocalNodeImpl node) throws NodeException {
		super(ClosedDatabaseException::new);

		this.node = node;
		this.config = node.getConfig();
		this.hasherForTransactions = config.getHashingForTransactions().getHasher(io.mokamint.node.api.Transaction::toByteArray);
		this.maximalHistoryChangeTime = config.getMaximalHistoryChangeTime();
		this.orphans = Memories.of(config.getOrphansMemorySize());
		this.environment = createBlockchainEnvironment();
		this.storeOfBlocks = openStore("blocks");
		this.storeOfForwards = openStore("forwards");
		this.storeOfChain = openStore("chain");
		this.storeOfTransactions = openStore("transactions");
		this.genesisHashCache = Optional.empty();
		this.genesisCache = Optional.empty();
	}

	@Override
	public void close() {
		try {
			if (stopNewCalls()) {
				try {
					environment.close(); // stopNewCalls() guarantees that there are no unfinished transactions at this moment
					LOGGER.info("blockchain: closed the blocks database");
				}
				catch (ExodusException e) {
					// TODO not sure while this happens, it seems there might be transactions run for garbage collection,
					// that will consequently find a closed environment
					LOGGER.log(Level.SEVERE, "blockchain: failed to close the blocks database", e);
				}
				finally {
					executors.shutdownNow();
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Determines if this blockchain is empty.
	 * 
	 * @return true if and only if this blockchain is empty
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean isEmpty() throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(this::isEmpty);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the hash of the genesis block of this blockchain, if any.
	 * 
	 * @return the hash of the genesis block, if any
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<byte[]> getGenesisHash() throws ClosedDatabaseException {
		// we use a cache to avoid repeated access for reading the hash of the genesis block, since it is not allowed to change after being set
		if (genesisHashCache.isPresent())
			return genesisHashCache.map(byte[]::clone);

		Optional<byte[]> result;

		try (var scope = mkScope()) {
			result = environment.computeInReadonlyTransaction(this::getGenesisHash);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}

		genesisHashCache = result.map(byte[]::clone);

		return result;
	}

	/**
	 * Yields the genesis block of this blockchain, if any.
	 * 
	 * @return the genesis block, if any
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<GenesisBlock> getGenesis() throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			// we use a cache to avoid repeated access for reading the genesis block, since it is not allowed to change after being set
			if (genesisCache.isPresent())
				return genesisCache;

			return genesisCache = environment.computeInReadonlyTransaction(this::getGenesis);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the head of the best chain of this blockchain, if any.
	 * 
	 * @return the head block, if any
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<Block> getHead() throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(this::getHead);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the starting block of the non-frozen part of the history of this blockchain, if any.
	 * 
	 * @return the starting block, if any
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<Block> getStartOfNonFrozenPart() throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(this::getStartOfNonFrozenPart);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the limit time used to deliver the transactions in the non-frozen part of the history.
	 * Transactions delivered before that time have stores that can be considered as frozen.
	 * 
	 * @return the limit time; this is empty if the database is empty
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<LocalDateTime> getStartingTimeOfNonFrozenHistory() throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(this::getStartingTimeOfNonFrozenHistory);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the height of the head of the best chain of this blockchain, if any.
	 * 
	 * @return the height of the head block, if any
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public OptionalLong getHeightOfHead() throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(this::getHeightOfHead);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the block with the given hash, if it is contained in this blockchain.
	 * 
	 * @param hash the hash
	 * @return the block, if any
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<Block> getBlock(byte[] hash) throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(txn -> getBlock(txn, hash));
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the description of the block with the given hash, if it is contained in this blockchain.
	 * 
	 * @param hash the hash
	 * @return the description of the block, if any
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<BlockDescription> getBlockDescription(byte[] hash) throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(txn -> getBlockDescription(txn, hash));
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the transaction with the given hash, if it is contained in some block of the best chain of this blockchain.
	 * 
	 * @param hash the hash of the transaction to search
	 * @return the transaction, if any
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<io.mokamint.node.api.Transaction> getTransaction(byte[] hash) throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(txn -> getTransaction(txn, hash));
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the address of the transaction with the given hash, if it is contained in some block
	 * of the best chain of this blockchain.
	 * 
	 * @param hash the hash of the transaction to search
	 * @return the transaction address, if any
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<TransactionAddress> getTransactionAddress(byte[] hash) throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(txn -> getTransactionAddress(txn, hash));
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the address of the transaction with the given hash, if it is contained in the
	 * chain from the given {@code block} towards the genesis block.
	 * 
	 * @param block the block
	 * @param hash the hash of the transaction to search
	 * @return the transaction address, if any
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<TransactionAddress> getTransactionAddress(Block block, byte[] hash) throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(txn -> getTransactionAddress(txn, block, hash));
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields information about the current best chain of this blockchain.
	 * 
	 * @return the information
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public ChainInfo getChainInfo() throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(this::getChainInfo);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the hashes of the blocks in the best chain, starting at height {@code start}
	 * (inclusive) and ending at height {@code start + count} (exclusive). The result
	 * might actually be shorter if not all such blocks exist.
	 * 
	 * @param start the height of the first block whose hash is returned
	 * @param count how many hashes (maximum) must be reported
	 * @return the hashes, in order
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Stream<byte[]> getChain(long start, int count) throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(txn -> getChain(txn, start, count));
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Determines if this blockchain contains a block with the given hash.
	 * 
	 * @param hash the hash of the block
	 * @return true if and only if that condition holds
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean containsBlock(byte[] hash) throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(txn -> containsBlock(txn, hash));
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Determines if the given block is more powerful than the current head.
	 * 
	 * @param block the block
	 * @return true if and only if the current head is missing or {@code block} is more powerful than the current head
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean isBetterThanHead(NonGenesisBlock block) throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInReadonlyTransaction(txn -> isBetterThanHead(txn, block));
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the creation time of the given block, if it were to be added to this blockchain.
	 * For genesis blocks, their creation time is explicit in the block.
	 * For non-genesis blocks, this method adds the total waiting time of the block to the time of the genesis
	 * of this blockchain.
	 * 
	 * @param block the block whose creation time is computed
	 * @return the creation time of {@code block}; this is empty only {@code block} is a non-genesis blocks and the blockchain is empty
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<LocalDateTime> creationTimeOf(Block block) throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			// we do not call creationTimeOf(txn, block) since we hope to avoid a database transaction by exploiting the cache in getGenesis()
			if (block instanceof GenesisBlock gb)
				return Optional.of(gb.getStartDateTimeUTC());
			else
				return getGenesis().map(genesis -> genesis.getStartDateTimeUTC().plus(block.getDescription().getTotalWaitingTime(), ChronoUnit.MILLIS));
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Initializes this blockchain, which must be empty. It adds a genesis block.
	 * 
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 * @throws NodeException if the node is misbehaving
	 */
	public void initialize() throws InterruptedException, ApplicationTimeoutException, NodeException, AlreadyInitializedException {
		if (!isEmpty())
			throw new AlreadyInitializedException("Initialization cannot be required for an already initialized blockchain");

		var keys = node.getKeys();

		try {
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")),
				config.getTargetBlockCreationTime(), config.getOblivion(), config.getHashingForBlocks(), config.getHashingForTransactions(),
				config.getHashingForDeadlines(), config.getHashingForGenerations(), config.getSignatureForBlocks(), keys.getPublic());

			addVerified(Blocks.genesis(description, node.getApplication().getInitialStateId(), keys.getPrivate()));
		}
		catch (ClosedApplicationException | InvalidKeyException | SignatureException e) {
			// this node is misbehaving because the application it is connected to is misbehaving or its key is wrong
			throw new NodeException(e);
		}
		catch (TimeoutException e) {
			throw new ApplicationTimeoutException(e);
		}
	}

	/**
	 * If the block was already in the database, this method performs nothing. Otherwise, it verifies
	 * the given block and adds it to the database of blocks of this blockchain. Note that the addition
	 * of a block might actually induce the addition of more, orphan blocks,
	 * if the block is recognized as the previous block of an orphan block.
	 * If the block is an orphan with higher power than the current head, its addition might trigger
	 * the synchronization of the chain from the peers of the node.
	 * 
	 * @param block the block to add
	 * @return true if the block has been actually added to the tree of blocks
	 *         rooted at the genesis block, false otherwise.
	 *         There are a few situations when the result can be false. For instance,
	 *         if {@code block} was already in the tree, or if {@code block} is
	 *         a genesis block but a genesis block is already present in the tree, or
	 *         if {@code block} has no previous block already in the tree (it is orphaned)
	 * @throws NodeException if the node is misbehaving
	 * @throws VerificationException if {@code block} cannot be added since it does not respect all consensus rules
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 */
	public boolean add(Block block) throws NodeException, VerificationException, InterruptedException, ApplicationTimeoutException {
		return add(block, Optional.of(Mode.COMPLETE));
	}

	/**
	 * This method behaves like {@link #add(Block)} but assumes that the given block is
	 * verified, so that it does not need further verification before being added to blockchain.
	 * 
	 * @param block the block to add
	 * @return true if the block has been actually added to the tree of blocks
	 *         rooted at the genesis block, false otherwise
	 * @throws NodeException if the node is misbehaving
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 */
	boolean addVerified(Block block) throws NodeException, InterruptedException, ApplicationTimeoutException {
		try {
			return add(block, Optional.empty());
		}
		catch (VerificationException e) {
			// impossible: we did not require block verification hence this exception should not have been generated
			throw new RuntimeException("Unexpected exception", e);
		}
	}

	/**
	 * Synchronizes this blockchain with the peers of the node.
	 * 
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 * @throws NodeException if the node is misbehaving
	 */
	public void synchronize() throws InterruptedException, ApplicationTimeoutException, NodeException {
		try {
			new Synchronization(node, executors);
		}
		finally {
			node.onSynchronizationCompleted();
		}
	}

	public void rebase(Mempool mempool, Block newBase) throws NodeException, InterruptedException, ApplicationTimeoutException {
		FunctionWithExceptions3<Transaction, Rebase, NodeException, InterruptedException, ApplicationTimeoutException> function = txn -> new Rebase(txn, mempool, newBase);
		environment.computeInReadonlyTransaction(NodeException.class, InterruptedException.class, ApplicationTimeoutException.class, function).updateMempool();
	}

	/**
	 * A context for the addition of one or more blocks to this blockchain, inside the same database transaction.
	 */
	private class BlockAdder {

		/**
		 * The database transaction where the additions are performed.
		 */
		private final Transaction txn;

		/**
		 * The blocks added to the blockchain, as result of the addition of some blocks and,
		 * potentially, of some previously orphan blocks that have been connected to the blockchain.
		 */
		private final List<Block> blocksAdded = new ArrayList<>();

		/**
		 * The blocks added to the current best chain of the blockchain. If not empty, this
		 * might contain some added block, some previously orphan blocks, some blocks from previously
		 * secondary branches, and ends with the new head of the blockchain.
		 */
		private final Deque<Block> blocksAddedToTheCurrentBestChain = new LinkedList<>();

		/**
		 * The blocks that can be added as orphan blocks after the additions have been performed.
		 */
		private final Set<NonGenesisBlock> blocksToAddAmongOrphans = new HashSet<>();

		/**
		 * The blocks that can be removed from the orphan blocks after the additions have been performed.
		 */
		private final Set<NonGenesisBlock> blocksToRemoveFromOrphans = new HashSet<>();

		/**
		 * True if and only if the added block ends being connected to the blockchain (this is true also if
		 * it was already connected before the addition).
		 */
		private boolean connected;

		/**
		 * Creates a context for the addition of blocks, inside the same database transaction.
		 * 
		 * @param txn the database transaction
		 * @throws NodeException if the node is misbehaving
		 */
		private BlockAdder(Transaction txn) throws NodeException {
			this.txn = txn;
		}

		/**
		 * Adds the given block to the blockchain.
		 * 
		 * @param block the block to add
		 * @param verification the verification mode of the block, if any
		 * @return this same adder
		 * @throws NodeException if the node is misbehaving
		 * @throws VerificationException if {@code block} cannot be added since it does not respect the consensus rules
		 * @throws InterruptedException if the current thread is interrupted
		 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
		 */
		private BlockAdder add(Block block, Optional<Mode> verification) throws NodeException, VerificationException, InterruptedException, ApplicationTimeoutException {
			byte[] hashOfBlockToAdd = block.getHash();

			// optimization check, to avoid repeated verification
			if (containsBlock(txn, hashOfBlockToAdd)) {
				connected = true;
				LOGGER.warning("blockchain: not adding block " + block.getHexHash() + " since it is already in blockchain");
			}
			else {
				Optional<byte[]> initialHeadHash = getHeadHash(txn);
				addBlockAndConnectOrphans(block, verification);
				computeBlocksAddedToTheCurrentBestChain(initialHeadHash);

				connected |= somethingHasBeenAdded();
			}

			return this;
		}

		/**
		 * Adds the given block to the blockchain.
		 * 
		 * @param block the block to add
		 * @param verification the verification mode of the block, if any
		 * @return this same adder
		 * @throws NodeException if the node is misbehaving
		 * @throws VerificationException if {@code block} cannot be added since it does not respect the consensus rules
		 * @throws InterruptedException if the current thread is interrupted
		 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
		 */
		private BlockAdder connect(Block block, Optional<Mode> verification) throws NodeException, VerificationException, InterruptedException, ApplicationTimeoutException {
			byte[] hashOfBlockToAdd = block.getHash();

			// optimization check, to avoid repeated verification
			if (containsBlock(txn, hashOfBlockToAdd)) {
				connected = true;
				LOGGER.fine(() -> "blockchain: not adding block " + block.getHexHash() + " since it is already in blockchain");
			}
			else {
				Optional<byte[]> initialHeadHash = getHeadHash(txn);
				addBlockAndConnectOrphans(block, verification);
				computeBlocksAddedToTheCurrentBestChain(initialHeadHash);

				connected |= somethingHasBeenAdded();
			}

			return this;
		}

		private void informNode() {
			blocksToAddAmongOrphans.forEach(orphans::add);
			blocksToRemoveFromOrphans.forEach(orphans::remove);
			blocksAdded.forEach(node::onAdded);

			if (!blocksAddedToTheCurrentBestChain.isEmpty())
				node.onHeadChanged(blocksAddedToTheCurrentBestChain);
		}

		private void updateMempool() throws NodeException, InterruptedException, ApplicationTimeoutException {
			if (!blocksAddedToTheCurrentBestChain.isEmpty())
				// if the head has been improved, we update the mempool by removing
				// the transactions that have been added in blockchain and potentially adding
				// other transactions (if the history changed). Note that, in general, the mempool of
				// the node might not always be aligned with the current head of the blockchain,
				// since another task might execute this same update concurrently. This is not
				// a problem, since this rebase is only an optimization, to keep the mempool small;
				// in any case, when a new mining task is spawn, its mempool gets recomputed wrt
				// the block over which mining occurs, so it will be aligned there
				node.rebaseMempoolAt(blocksAddedToTheCurrentBestChain.getLast());
		}

		/**
		 * Determines if this blockchain has been expanded with this adder, not necessarily
		 * along its best chain. The addition of orphan blocks is not considered as an expansion.
		 * 
		 * @return true if and only if this blockchain has been expanded
		 */
		private boolean somethingHasBeenAdded() {
			return !blocksAdded.isEmpty();
		}

		/**
		 * Determines if the node required to add or connect has been actually connected to the blockchain tree by the operation.
		 * 
		 * @return true if and only if that condition holds
		 */
		private boolean addedBlockHasBeenConnected() {
			return connected;
		}

		private void addBlockAndConnectOrphans(Block blockToAdd, Optional<Mode> verification) throws NodeException, VerificationException, InterruptedException, ApplicationTimeoutException {
			// we use a working set, since the addition of a single block might trigger the further addition of orphan blocks, recursively
			var ws = new ArrayList<Block>();
			ws.add(blockToAdd);

			do {
				Block cursor = ws.remove(ws.size() - 1);
				Optional<Block> previous = Optional.empty();

				if (cursor instanceof GenesisBlock || (previous = getBlock(txn, ((NonGenesisBlock) cursor).getHashOfPreviousBlock())).isPresent()) {
					byte[] hashOfCursor = cursor.getHash();
					if (add(cursor, hashOfCursor, previous, blockToAdd != cursor, verification)) {
						blocksAdded.add(cursor);
						forEachOrphanWithParent(hashOfCursor, ws::add);
						if (blockToAdd != cursor)
							blocksToRemoveFromOrphans.add((NonGenesisBlock) cursor); // orphan blocks are always non-genesis blocks
					}
				}
				else if (cursor instanceof NonGenesisBlock ngb)
					blocksToAddAmongOrphans.add(ngb);
			}
			while (!ws.isEmpty());
		}

		private void computeBlocksAddedToTheCurrentBestChain(Optional<byte[]> initialHeadHash) throws NodeException {
			if (blocksAdded.isEmpty())
				return;

			// we move backwards from the initial head until we hit the current best chain
			Block start;
			if (initialHeadHash.isEmpty())
				start = getGenesis(txn).orElseThrow(() -> new DatabaseException("The blockchain has been expanded but it still misses a genesis block"));
			else {
				byte[] hashOfCursor = initialHeadHash.get();
				Block cursor = getBlock(txn, hashOfCursor).orElseThrow(() -> new DatabaseException("Cannot find the original head of the blockchain"));
				while (!isContainedInTheBestChain(txn, cursor, hashOfCursor))
					if (cursor instanceof NonGenesisBlock ngb) {
						hashOfCursor = ngb.getHashOfPreviousBlock();
						cursor = getBlock(txn, hashOfCursor).orElseThrow(() -> new DatabaseException("Cannot follow the path to the original head of the blockchain, backwards"));
					}
					else
						throw new DatabaseException("The original head is in a dangling path");

				start = cursor;
			}

			// once we reached the current best chain, we move upwards along it and collect all blocks that have been added to it
			var height = start.getDescription().getHeight();
			ByteIterable hashOfBlockFromBestChain;

			do {
				height++;
				hashOfBlockFromBestChain = storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(height)));
				if (hashOfBlockFromBestChain != null)
					blocksAddedToTheCurrentBestChain.addLast(getBlock(txn, hashOfBlockFromBestChain.getBytes())
							.orElseThrow(() -> new DatabaseException("Cannot follow the new best chain upwards")));
			}
			while (hashOfBlockFromBestChain != null);
		}

		private boolean add(Block blockToAdd, byte[] hashOfBlockToAdd, Optional<Block> previous, boolean isOrphan, Optional<Mode> verification) throws NodeException, VerificationException, InterruptedException, ApplicationTimeoutException {
			if (verification.isPresent() || isOrphan) {
				try {
					new BlockVerification(txn, node, blockToAdd, previous, isOrphan ? Mode.COMPLETE : verification.get());
				}
				catch (VerificationException e) {
					if (isOrphan) {
						LOGGER.warning("blockchain: discarding orphan block " + blockToAdd.getHexHash() + " since it does not pass verification: " + e.getMessage());
						blocksToRemoveFromOrphans.add((NonGenesisBlock) blockToAdd); // orphan blocks are never genesis blocks
						return false;
					}
					else
						throw e;
				}
			}

			return add(blockToAdd, hashOfBlockToAdd, previous);
		}

		/**
		 * Adds a block to the tree of blocks rooted at the genesis block (if any).
		 * It updates the references to the genesis and to the head of the longest chain, if needed.
		 * 
		 * @param block the block to add
		 * @param hashOfBlock the hash of {@code block}
		 * @param previous the parent of {@code block}, if it is a non-genesis block
		 * @return true if and only if the block has been added. False means that
		 *         the block was already in the tree; or that it is a genesis
		 *         block and there is already a genesis block in the tree; or that it
		 *         is a non-genesis block whose previous is not in the tree
		 * @throws NodeException if the node is misbehaving
		 */
		private boolean add(Block block, byte[] hashOfBlock, Optional<Block> previous) throws NodeException {
			if (block instanceof NonGenesisBlock ngb) {
				if (isInFrozenPart(txn, previous.get().getDescription())) {
					LOGGER.warning("blockchain: not adding block " + block.getHexHash() + " since its previous block is in the frozen part of the blockchain");
					return false;
				}
				else if (containsBlock(txn, hashOfBlock)) {
					LOGGER.fine(() -> "blockchain: not adding block " + block.getHexHash() + " since it is already present in blockchain");
					return false;
				}
				else {
					putBlockInStore(txn, hashOfBlock, block);
					addToForwards(txn, ngb, hashOfBlock);
					if (isBetterThanHead(txn, ngb))
						setHead(block, hashOfBlock);
						
					LOGGER.fine(() -> "blockchain: height " + block.getDescription().getHeight() + ": added block " + block.getHexHash());
					return true;
				}
			}
			else {
				if (isEmpty(txn)) {
					putBlockInStore(txn, hashOfBlock, block);
					setGenesisHash(txn, hashOfBlock);
					setHead(block, hashOfBlock);
					LOGGER.fine(() -> "blockchain: height " + block.getDescription().getHeight() + ": added block " + block.getHexHash());
					return true;
				}
				else {
					LOGGER.warning("blockchain: not adding genesis block " + block.getHexHash() + " since the database already contains a genesis block");
					return false;
				}
			}
		}

		/**
		 * Sets the head of the best chain in this database.
		 * 
		 * @param newHead the new head
		 * @param newHeadHash the hash of {@code newHead}
		 * @throws NodeException if the node is misbehaving
		 */
		private void setHead(Block newHead, byte[] newHeadHash) throws NodeException {
			try {
				updateChain(newHead, newHeadHash);
				storeOfBlocks.put(txn, HASH_OF_HEAD, fromBytes(newHeadHash));
				storeOfBlocks.put(txn, STATE_ID_OF_HEAD, fromBytes(newHead.getStateId()));
				storeOfBlocks.put(txn, POWER_OF_HEAD, fromBytes(newHead.getDescription().getPower().toByteArray()));
				long heightOfHead = newHead.getDescription().getHeight();
				storeOfBlocks.put(txn, HEIGHT_OF_HEAD, fromBytes(longToBytes(heightOfHead)));
				LOGGER.info(() -> "blockchain: height " + heightOfHead + ": block " + newHead.getHexHash() + " set as head");
			}
			catch (ExodusException e) {
				throw new DatabaseException(e);
			}
		}

		/**
		 * Updates the current best chain in this database, to the chain having the given block as head.
		 * 
		 * @param newHead the block that gets set as new head
		 * @param newHeadHash the hash of {@code block}
		 * @throws NodeException if the node is misbehaving
		 */
		private void updateChain(Block newHead, byte[] newHeadHash) throws NodeException {
			try {
				Block cursor = newHead;
				byte[] cursorHash = newHeadHash;
				long totalTimeOfNewHead = newHead.getDescription().getTotalWaitingTime();
				long height = newHead.getDescription().getHeight();
		
				removeDataHigherThan(txn, height);
		
				var heightBI = fromBytes(longToBytes(height));
				var _new = fromBytes(newHeadHash);
				var old = storeOfChain.get(txn, heightBI);

				var blocksAddedToTheCurrentBestChain = new LinkedList<Block>();
		
				do {
					storeOfChain.put(txn, heightBI, _new);
		
					if (old != null) {
						long heightCopy = height;
						var oldBytes = old.getBytes();
						Block oldBlock = getBlock(txn, oldBytes)
							.orElseThrow(() -> new DatabaseException("The current best chain misses the block at height " + heightCopy  + " with hash " + Hex.toHexString(oldBytes)));
		
						removeReferencesToTransactionsInside(txn, oldBlock);
					}
		
					blocksAddedToTheCurrentBestChain.addFirst(cursor);
		
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
		
				for (Block added: blocksAddedToTheCurrentBestChain)
					addReferencesToTransactionsInside(added);
		
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
						var descriptionOfStartOfNonFrozenPart = getBlockDescription(txn, startOfNonFrozenPartHash)
								.orElseThrow(() -> new DatabaseException("Block " + Hex.toHexString(startOfNonFrozenPartHashCopy)
									+ " should be the start of the non-frozen part of the blockchain, but it cannot be found in the database"));
		
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

		private void addReferencesToTransactionsInside(Block block) {
			if (block instanceof NonGenesisBlock ngb) {
				long height = ngb.getDescription().getHeight();

				int count = ngb.getTransactionsCount();
				for (int pos = 0; pos < count; pos++) {
					var ref = new TransactionRef(height, pos);
					
					try {
						storeOfTransactions.put(txn, ByteIterable.fromBytes(hasherForTransactions.hash(ngb.getTransaction(pos))), ByteIterable.fromBytes(ref.toByteArray()));
					}
					catch (ExodusException e) {
						throw new UncheckedDatabaseException(e);
					}
				};
			}
		}

		/**
		 * Performs an action for each orphan having the given parent.
		 * 
		 * @param hashOfParent the hash of the parent
		 * @param action the action to perform
		 */
		private void forEachOrphanWithParent(byte[] hashOfParent, Consumer<NonGenesisBlock> action) {
			// we also look in the orphans that have been added during the life of this adder
			Stream.concat(orphans.stream(), blocksToAddAmongOrphans.stream())
				.filter(Objects::nonNull)
				.filter(orphan -> Arrays.equals(orphan.getHashOfPreviousBlock(), hashOfParent))
				.filter(orphan -> !blocksToRemoveFromOrphans.contains(orphan))
				.forEach(action);
		}

		private void removeDataHigherThan(Transaction txn, long height) throws NodeException {
			Optional<Block> cursor = getHead(txn);
		
			if (cursor.isPresent()) {
				Block block = cursor.get();
				long blockHeight;
		
				while ((blockHeight = block.getDescription().getHeight()) > height) {
					if (block instanceof NonGenesisBlock ngb) {
						removeReferencesToTransactionsInside(txn, block);

						try {
							storeOfChain.delete(txn, ByteIterable.fromBytes(longToBytes(blockHeight)));
						}
						catch (ExodusException e) {
							throw new UncheckedDatabaseException(e);
						}

						byte[] hashOfPrevious = ngb.getHashOfPreviousBlock();
						var blockCopy = block;
						block = getBlock(txn, hashOfPrevious).orElseThrow(() -> new DatabaseException("Block " + blockCopy.getHexHash() + " has no previous block in the database"));
					}
					else
						throw new UncheckedDatabaseException("The current best chain contains a genesis block " + block.getHexHash() + " at height " + blockHeight);
				}
			}
		}

		private void removeReferencesToTransactionsInside(Transaction txn, Block block) {
			if (block instanceof NonGenesisBlock ngb) {
				int count = ngb.getTransactionsCount();
				for (int pos = 0; pos < count; pos++) {
					try {
						storeOfTransactions.delete(txn, ByteIterable.fromBytes(hasherForTransactions.hash(ngb.getTransaction(pos))));
					}
					catch (ExodusException e) {
						throw new UncheckedDatabaseException(e);
					}
				}
			}
		}
	}

	/**
	 * The algorithm for rebasing a mempool at a given, new base.
	 */
	private class Rebase {
		private final Transaction txn;
		private final Mempool mempool;
		private final Block newBase;
		private final Set<TransactionEntry> toRemove = new HashSet<>();
		private final Set<TransactionEntry> toAdd = new HashSet<>();
		private Block newBlock;
		private Block oldBlock;

		private Rebase(Transaction txn, Mempool mempool, Block newBase) throws NodeException, InterruptedException, ApplicationTimeoutException {
			this.txn = txn;
			this.mempool = mempool;
			this.newBase = newBase;
			this.newBlock = newBase;
			this.oldBlock = mempool.getBase().orElse(null);

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

		private void updateMempool() {
			mempool.update(newBase, toAdd.stream(), toRemove.stream());
		}

		private boolean reachedSharedAncestor() throws NodeException {
			if (newBlock.equals(oldBlock))
				return true;
			else if (newBlock instanceof GenesisBlock || oldBlock instanceof GenesisBlock)
				throw new DatabaseException("Cannot identify a shared ancestor block between " + oldBlock.getHexHash() + " and " + newBlock.getHexHash());
			else
				return false;
		}

		private void markToRemoveAllTransactionsInNewBlockAndMoveItBackwards() throws NodeException, InterruptedException, ApplicationTimeoutException {
			if (newBlock instanceof NonGenesisBlock ngb) {
				markAllTransactionsAsToRemove(ngb);
				newBlock = getBlock(ngb.getHashOfPreviousBlock());
			}
			else
				throw new DatabaseException("The database contains a genesis block " + newBlock.getHexHash() + " at height " + newBlock.getDescription().getHeight());
		}

		private void markToAddAllTransactionsInOldBlockAndMoveItBackwards() throws NodeException, InterruptedException, ApplicationTimeoutException {
			if (oldBlock instanceof NonGenesisBlock ngb) {
				markAllTransactionsAsToAdd(ngb);
				oldBlock = getBlock(ngb.getHashOfPreviousBlock());
			}
			else
				throw new DatabaseException("The database contains a genesis block " + oldBlock.getHexHash() + " at height " + oldBlock.getDescription().getHeight());
		}

		private void markToRemoveAllTransactionsFromNewBaseToGenesis() throws NodeException, InterruptedException, ApplicationTimeoutException {
			while (newBlock instanceof NonGenesisBlock ngb) {
				markAllTransactionsAsToRemove(ngb);
				newBlock = getBlock(ngb.getHashOfPreviousBlock());
			}
		}

		/**
		 * Adds the transaction entries to those that must be added to the mempool.
		 * 
		 * @param block the block
		 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
		 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
		 * @throws NodeException if the node is misbehaving
		 */
		private void markAllTransactionsAsToAdd(NonGenesisBlock block) throws InterruptedException, ApplicationTimeoutException, NodeException {
			for (int pos = 0; pos < block.getTransactionsCount(); pos++)
				toAdd.add(intoTransactionEntry(block.getTransaction(pos)));
		}

		/**
		 * Adds the transactions from the given block to those that must be removed from the mempool.
		 * 
		 * @param block the block
		 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
		 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
		 * @throws NodeException if the node is misbehaving
		 */
		private void markAllTransactionsAsToRemove(NonGenesisBlock block) throws InterruptedException, ApplicationTimeoutException, NodeException {
			for (var transaction: block.getTransactions().toArray(io.mokamint.node.api.Transaction[]::new))
				toRemove.add(intoTransactionEntry(transaction));
		}

		/**
		 * Expands the given transaction into a transaction entry, by filling its priority and hash.
		 * 
		 * @param transaction the transaction
		 * @return the resulting transaction entry
		 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
		 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
		 * @throws NodeException if the node is misbehaving
		 */
		private TransactionEntry intoTransactionEntry(io.mokamint.node.api.Transaction transaction) throws InterruptedException, ApplicationTimeoutException, NodeException {
			try {
				return mempool.mkTransactionEntry(transaction);
			}
			catch (TransactionRejectedException | ClosedApplicationException e) {
				// either the database contains a block with a rejected transaction: it should not be there!
				// or the node is misbehaving because the application it is connected to is misbehaving
				throw new NodeException(e);
			}
		}

		private Block getBlock(byte[] hash) throws NodeException {
			return Blockchain.this.getBlock(txn, hash).orElseThrow(() -> new DatabaseException("Missing block with hash " + Hex.toHexString(hash)));
		}
	}

	/**
	 * Adds the given block to this blockchain, possibly connecting existing orphan blocks as well.
	 * If the previous block does not exist, the block is itself added among the orphan blocks.
	 * 
	 * @param block the block to add
	 * @param verification the kind of verification to perform on the node; if missing, no verification is performed
	 * @return true if and only if the block has been actually connected; this is false if the node has been added among the orphan blocks
	 * @throws NodeException if the node is misbehaving
	 * @throws VerificationException if the required verification fails
	 * @throws InterruptedException if the current thread gets interrupted while performing the action
	 * @throws ApplicationTimeoutException if the application is non-responsive
	 */
	public boolean add(Block block, Optional<Mode> verification) throws NodeException, VerificationException, InterruptedException, ApplicationTimeoutException {
		BlockAdder adder;

		FunctionWithExceptions4<Transaction, BlockAdder, NodeException, VerificationException, InterruptedException, ApplicationTimeoutException> function = txn -> new BlockAdder(txn).add(block, verification);

		try (var scope = mkScope()) {
			adder = environment.computeInTransaction(NodeException.class, VerificationException.class, InterruptedException.class, ApplicationTimeoutException.class, function);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	
		adder.informNode();
		adder.updateMempool();
	
		return adder.somethingHasBeenAdded();
	}

	/**
	 * Adds the given block to this blockchain, if its previous block already exists in blockchain
	 * and its state has not been garbage-collected. This method never adds the block to the orphan blocks,
	 * but it might connect existing orphan blocks to the added block.
	 * 
	 * @param block the block to connect
	 * @param verification the kind of verification to perform on the node; if missing, no verification is performed
	 * @return true if and only if the block is now part of the blockchain tree; this is false if the previous block is missing in blockchain
	 * @throws NodeException if the node is misbehaving
	 * @throws VerificationException if the required verification fails
	 * @throws InterruptedException if the current thread gets interrupted while performing the action
	 * @throws ApplicationTimeoutException if the application is non-responsive
	 */
	public boolean connect(Block block, Optional<Mode> verification) throws NodeException, VerificationException, InterruptedException, ApplicationTimeoutException {
		BlockAdder adder;

		FunctionWithExceptions4<Transaction, BlockAdder, NodeException, VerificationException, InterruptedException, ApplicationTimeoutException> function = txn -> new BlockAdder(txn).connect(block, verification);

		try (var scope = mkScope()) {
			adder = environment.computeInTransaction(NodeException.class, VerificationException.class, InterruptedException.class, ApplicationTimeoutException.class, function);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	
		adder.informNode();
		adder.updateMempool();
	
		return adder.addedBlockHasBeenConnected();
	}

	private boolean isInFrozenPart(Transaction txn, BlockDescription blockDescription) throws NodeException {
		var totalWaitingTimeOfStartOfNonFrozenPart = getTotalWaitingTimeOfStartOfNonFrozenPart(txn);
		return totalWaitingTimeOfStartOfNonFrozenPart.isPresent() && totalWaitingTimeOfStartOfNonFrozenPart.getAsLong() > blockDescription.getTotalWaitingTime();
	}

	private Environment createBlockchainEnvironment() throws NodeException {
		try {
			var path = config.getDir().resolve("blocks");
			var env = new Environment(path.toString());
			LOGGER.info("blockchain: opened the blocks database at " + path);
			return env;
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private Store openStore(String name) throws NodeException {
		try {
			Store store = environment.computeInTransaction(txn -> environment.openStoreWithoutDuplicatesWithPrefixing(name, txn));
			LOGGER.info("blockchain: opened the store of " + name + " inside the blocks database");
			return store;
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the hash of the head block of this blockchain, if it has been set already,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the hash of the head block, if any
	 */
	private Optional<byte[]> getHeadHash(Transaction txn) {
		try {
			return Optional.ofNullable(storeOfBlocks.get(txn, HASH_OF_HEAD)).map(ByteIterable::getBytes);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the state identifier of the head block of this blockchain, if it has been set already,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the state identifier, if any
	 */
	private Optional<byte[]> getStateIdOfHead(Transaction txn) {
		try {
			return Optional.ofNullable(storeOfBlocks.get(txn, STATE_ID_OF_HEAD)).map(ByteIterable::getBytes);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the hash of the starting block of the non-frozen part of this blockchain, if it has been set already,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the hash of the starting block, if any
	 */
	private Optional<byte[]> getStartOfNonFrozenPartHash(Transaction txn) {
		try {
			return Optional.ofNullable(storeOfBlocks.get(txn, HASH_OF_START_OF_NON_FROZEN_PART)).map(ByteIterable::getBytes);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the limit time used to deliver the transactions in the non-frozen part of the history,
	 * running inside a transaction.
	 * Application transactions delivered before that time have stores that can be considered as frozen.
	 * 
	 * @param txn the transaction
	 * @return the limit time; this is empty if the blockchain is empty
	 */
	private Optional<LocalDateTime> getStartingTimeOfNonFrozenHistory(Transaction txn) {
		Optional<byte[]> maybeStartOfNonFrozenPartHash = getStartOfNonFrozenPartHash(txn);
		if (maybeStartOfNonFrozenPartHash.isEmpty())
			return Optional.empty();

		Block startOfNonFrozenPart = getBlock(txn, maybeStartOfNonFrozenPartHash.get())
			.orElseThrow(() -> new UncheckedDatabaseException("The hash of the start of the non-frozen part of the blockchain is set but its block cannot be found in the database"));

		if (startOfNonFrozenPart instanceof GenesisBlock gb)
			return Optional.of(gb.getStartDateTimeUTC());

		return Optional.of(getGenesis(txn)
			.orElseThrow(() -> new UncheckedDatabaseException("The database is not empty but its genesis block is not set"))
			.getStartDateTimeUTC().plus(startOfNonFrozenPart.getDescription().getTotalWaitingTime(), ChronoUnit.MILLIS));
	}

	/**
	 * Yields the hash of the genesis block in this database, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the hash of the genesis block, if any
	 */
	private Optional<byte[]> getGenesisHash(Transaction txn) {
		try {
			return Optional.ofNullable(storeOfBlocks.get(txn, HASH_OF_GENESIS)).map(ByteIterable::getBytes);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the power of the head block, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the power of the head block, if any
	 */
	private Optional<BigInteger> getPowerOfHead(Transaction txn) {
		try {
			return Optional.ofNullable(storeOfBlocks.get(txn, POWER_OF_HEAD)).map(ByteIterable::getBytes).map(BigInteger::new);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the height of the head block, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the height of the head block, if any
	 */
	private OptionalLong getHeightOfHead(Transaction txn) {
		try {
			ByteIterable heightBI = storeOfBlocks.get(txn, HEIGHT_OF_HEAD);
			if (heightBI == null)
				return OptionalLong.empty();
			else {
				long chainHeight = bytesToLong(heightBI.getBytes());
				if (chainHeight < 0L)
					throw new UncheckedDatabaseException("The database contains a negative chain length");

				return OptionalLong.of(chainHeight);
			}
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the total waiting time of the start of the non-frozen part of the blockchain, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the total waiting time, if any
	 */
	private OptionalLong getTotalWaitingTimeOfStartOfNonFrozenPart(Transaction txn) {
		try {
			ByteIterable totalWaitingTimeBI = storeOfBlocks.get(txn, TOTAL_WAITING_TIME_OF_START_OF_NON_FROZEN_PART);
			if (totalWaitingTimeBI == null)
				return OptionalLong.empty();
			else {
				long totalWaitingTime = bytesToLong(totalWaitingTimeBI.getBytes());
				if (totalWaitingTime < 0L)
					throw new UncheckedDatabaseException("The database contains a negative total waiting time for the start of the non-frozen part of the blockchain");

				return OptionalLong.of(totalWaitingTime);
			}
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the hashes of the blocks that follow the block with the given hash, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param hash the hash of the parent block
	 * @return the hashes
	 */
	private Stream<byte[]> getForwards(Transaction txn, byte[] hash) {
		ByteIterable forwards;

		try {
			forwards = storeOfForwards.get(txn, fromBytes(hash));
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}

		if (forwards == null)
			return Stream.empty();
		else {
			int size = config.getHashingForBlocks().length();
			byte[] hashes = forwards.getBytes();
			if (hashes.length % size != 0)
				throw new UncheckedDatabaseException("The forward map has been corrupted");
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
	 * Yields the block with the given hash, if it is contained in this blockchain,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param hash the hash
	 * @return the block, if any
	 */
	private Optional<Block> getBlock(Transaction txn, byte[] hash) {
		try {
			ByteIterable blockBI = storeOfBlocks.get(txn, fromBytes(hash));
			if (blockBI == null)
				return Optional.empty();
			
			try (var bais = new ByteArrayInputStream(blockBI.getBytes()); var context = UnmarshallingContexts.of(bais)) {
				return Optional.of(Blocks.from(context, config));
			}
		}
		catch (ExodusException | IOException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the description of the block with the given hash, if it is contained in this blockchain,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param hash the hash
	 * @return the description of the block, if any
	 */
	private Optional<BlockDescription> getBlockDescription(Transaction txn, byte[] hash) {
		try {
			ByteIterable blockBI = storeOfBlocks.get(txn, fromBytes(hash));
			if (blockBI == null)
				return Optional.empty();
			
			try (var bais = new ByteArrayInputStream(blockBI.getBytes()); var context = UnmarshallingContexts.of(bais)) {
				// the marshalling of a block starts with that of its description
				return Optional.of(BlockDescriptions.from(context, config));
			}
		}
		catch (ExodusException | IOException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the transaction with the given hash, if it is contained in some block of the best chain of this blockchain,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param hash the hash of the transaction to search
	 * @return the transaction, if any
	 */
	private Optional<io.mokamint.node.api.Transaction> getTransaction(Transaction txn, byte[] hash) {
		try {
			ByteIterable txBI = storeOfTransactions.get(txn, fromBytes(hash));
			if (txBI == null)
				return Optional.empty();

			var ref = TransactionRef.from(txBI);
			ByteIterable blockHash = storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(ref.height)));
			if (blockHash == null)
				throw new UncheckedDatabaseException("The hash of the block of the best chain at height " + ref.height + " is not in the database");

			Block block = getBlock(txn, blockHash.getBytes())
				.orElseThrow(() -> new UncheckedDatabaseException("The current best chain misses the block at height " + ref.height  + " with hash " + Hex.toHexString(blockHash.getBytes())));

			if (block instanceof NonGenesisBlock ngb) {
				try {
					return Optional.of(ngb.getTransaction(ref.progressive));
				}
				catch (IndexOutOfBoundsException e) {
					throw new UncheckedDatabaseException("Transaction " + Hex.toHexString(hash) + " has a progressive number outside the bounds for the block where it is contained");
				}
			}
			else
				throw new UncheckedDatabaseException("Transaction " + Hex.toHexString(hash) + " seems contained in a genesis block, which is impossible");
		}
		catch (ExodusException | IOException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the address of the transaction with the given hash, if it is contained in some block
	 * of the best chain of this database, running inside the given transaction.
	 * 
	 * @param txn the database transaction
	 * @param hash the hash of the transaction to search
	 * @return the transaction address, if any
	 */
	private Optional<TransactionAddress> getTransactionAddress(Transaction txn, byte[] hash) {
		try {
			ByteIterable txBI = storeOfTransactions.get(txn, fromBytes(hash));
			if (txBI == null)
				return Optional.empty();

			var ref = TransactionRef.from(txBI);
			ByteIterable blockHash = storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(ref.height)));
			if (blockHash == null)
				throw new UncheckedDatabaseException("The hash of the block of the best chain at height " + ref.height + " is not in the database");

			return Optional.of(TransactionAddresses.of(blockHash.getBytes(), ref.progressive));
		}
		catch (ExodusException | IOException e) {
			throw new UncheckedDatabaseException(e);
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
	 */
	Optional<TransactionAddress> getTransactionAddress(Transaction txn, Block block, byte[] hash) {
		try {
			byte[] hashOfBlock = block.getHash();
			String initialHash = block.getHexHash();

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
						throw new UncheckedDatabaseException("The hash of the block of the best chain at height " + ref.height + " is not in the database");

					return Optional.of(TransactionAddresses.of(blockHash.getBytes(), ref.progressive));
				}
				else if (block instanceof NonGenesisBlock ngb) {
					// we check if the transaction is inside the table of transactions of the block
					int length = ngb.getTransactionsCount();
					for (int pos = 0; pos < length; pos++)
						if (Arrays.equals(hash, hasherForTransactions.hash(ngb.getTransaction(pos))))
							return Optional.of(TransactionAddresses.of(hashOfBlock, pos));

					// otherwise we continue with the previous block, if any
					byte[] hashOfPrevious = ngb.getHashOfPreviousBlock();
					var maybePreviousBlock = getBlock(txn, hashOfPrevious);
					if (maybePreviousBlock.isEmpty())
						// the block was actually in a dangling chain, not connected to the blockchain
						return Optional.empty();

					block = maybePreviousBlock.get();
					hashOfBlock = hashOfPrevious;
				}
				else
					throw new UncheckedDatabaseException("The block " + initialHash + " is not connected to the best chain");
			}
		}
		catch (ExodusException | IOException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the creation time of the given block, if it were to be added to this blockchain, running
	 * inside a transaction. For genesis blocks, their creation time is explicit in the block.
	 * For non-genesis blocks, this method adds the total waiting time of the block to the time of the genesis of this blockchain.
	 * 
	 * @param txn the transaction
	 * @param block the block whose creation time is computed
	 * @return the creation time of {@code block}; this is empty only for non-genesis blocks if the blockchain is empty
	 */
	Optional<LocalDateTime> creationTimeOf(Transaction txn, Block block) {
		if (block instanceof GenesisBlock gb)
			return Optional.of(gb.getStartDateTimeUTC());
		else
			return getGenesis(txn).map(genesis -> genesis.getStartDateTimeUTC().plus(block.getDescription().getTotalWaitingTime(), ChronoUnit.MILLIS));
	}

	/**
	 * Checks if the blockchain is empty or the given block is better than the current head, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param block the block
	 * @return true if and only if that condition holds
	 */
	private boolean isBetterThanHead(Transaction txn, NonGenesisBlock block) {
		if (isEmpty(txn))
			return true;
		else {
			BigInteger powerOfHead = getPowerOfHead(txn).orElseThrow(() -> new UncheckedDatabaseException("The database of blocks is non-empty but the power of the head is not set"));
			return block.getDescription().getPower().compareTo(powerOfHead) > 0;
		}
	}

	private boolean isContainedInTheBestChain(Transaction txn, Block block, byte[] blockHash) {
		try {
			var height = block.getDescription().getHeight();
			var hashOfBlockFromBestChain = storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(height)));
			return hashOfBlockFromBestChain != null && Arrays.equals(hashOfBlockFromBestChain.getBytes(), blockHash);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Determines if this blockchain is empty, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return true if and only if this blockchain is empty
	 */
	private boolean isEmpty(Transaction txn) {
		return getGenesisHash(txn).isEmpty();
	}

	/**
	 * Yields the head of the best chain of this blockchain, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the head, if any
	 */
	private Optional<Block> getHead(Transaction txn) {
		try {
			Optional<byte[]> maybeHeadHash = getHeadHash(txn);
			if (maybeHeadHash.isEmpty())
				return Optional.empty();

			Optional<Block> maybeHead = getBlock(txn, maybeHeadHash.get());
			if (maybeHead.isPresent())
				return maybeHead;
			else
				throw new UncheckedDatabaseException("The head hash is set but it is not in the database");
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the starting block of the non-frozen part of the history of this blockchain, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the starting block, if any
	 */
	private Optional<Block> getStartOfNonFrozenPart(Transaction txn) {
		try {
			Optional<byte[]> maybeStartOfNonFrozenPartHash = getStartOfNonFrozenPartHash(txn);
			if (maybeStartOfNonFrozenPartHash.isEmpty())
				return Optional.empty();

			Optional<Block> maybeStartOfNonFrozenPart = getBlock(txn, maybeStartOfNonFrozenPartHash.get());
			if (maybeStartOfNonFrozenPart.isPresent())
				return maybeStartOfNonFrozenPart;
			else
				throw new UncheckedDatabaseException("The hash of the start of non-frozen part of the blockchain is set but it is not in the database");
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the genesis block of this blockchain, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the genesis block, if any
	 */
	private Optional<GenesisBlock> getGenesis(Transaction txn) {
		try {
			Optional<byte[]> maybeGenesisHash = getGenesisHash(txn);
			if (maybeGenesisHash.isEmpty())
				return Optional.empty();

			Block genesis = getBlock(txn, maybeGenesisHash.get()).orElseThrow(() -> new UncheckedDatabaseException("The genesis hash is set but it is not in the database"));

			if (genesis instanceof GenesisBlock gb)
				return Optional.of(gb);
			else
				throw new UncheckedDatabaseException("The genesis hash is set but it refers to a non-genesis block in the database");
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Adds a block with a given hash to this blockchain, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param hashOfBlock the hash of the block
	 * @param block the block
	 */
	private void putBlockInStore(Transaction txn, byte[] hashOfBlock, Block block) {
		try {
			// to save space, we do not write the data of the block that can be recovered from the configuration of the node
			storeOfBlocks.put(txn, fromBytes(hashOfBlock), fromBytes(block.toByteArrayWithoutConfigurationData()));
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Determines if this blockchain contains a block with the given hash, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param hashOfBlock the hash
	 * @return true if and only if that condition holds
	 */
	private boolean containsBlock(Transaction txn, byte[] hashOfBlock) {
		try {
			return storeOfBlocks.get(txn, fromBytes(hashOfBlock)) != null;
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Sets the hash of the genesis block of this blockchain, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param newGenesisHash the hash to set as hash of the genesis block
	 */
	private void setGenesisHash(Transaction txn, byte[] newGenesisHash) {
		try {
			storeOfBlocks.put(txn, HASH_OF_GENESIS, fromBytes(newGenesisHash));
			LOGGER.info("blockchain: height 0: block " + Hex.toHexString(newGenesisHash) + " set as genesis");
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Sets the hash of the starting block of the non-frozen part of this blockchain, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param startOfNonFrozenPartHash the hash to set as starting block of the non-frozen part of this blockchain
	 */
	private void setStartOfNonFrozenPartHash(Transaction txn, byte[] startOfNonFrozenPartHash) {
		try {
			storeOfBlocks.put(txn, HASH_OF_START_OF_NON_FROZEN_PART, fromBytes(startOfNonFrozenPartHash));
			var descriptionOfStartOfNonFrozenPart = getBlockDescription(txn, startOfNonFrozenPartHash).orElseThrow(() -> new UncheckedDatabaseException("Trying to set the start of the non-frozen part of the blockchain to a block not present in the database"));
			storeOfBlocks.put(txn, TOTAL_WAITING_TIME_OF_START_OF_NON_FROZEN_PART, fromBytes(longToBytes(descriptionOfStartOfNonFrozenPart.getTotalWaitingTime())));
			LOGGER.fine(() -> "blockchain: block " + Hex.toHexString(startOfNonFrozenPartHash) + " set as start of non-frozen part, at height " + descriptionOfStartOfNonFrozenPart.getHeight());
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	private void gcBlocksRootedAt(Transaction txn, byte[] hash) throws NodeException {
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

				// storeOfTransactions and storeOfChain refer to the current best chain, that
				// is never garbage-collected; if that best chain changes, the BlockAdder will
				// take care of updating storeOfTransactions and storeOfChain
			}
			catch (ExodusException e) {
				throw new DatabaseException(e);
			}
			
			LOGGER.fine(() -> "blockchain: garbage-collected block " + Hex.toHexString(currentHash));
		}
		while (!ws.isEmpty());
	}

	private ChainInfo getChainInfo(Transaction txn) {
		var maybeGenesisHash = getGenesisHash(txn);
		if (maybeGenesisHash.isEmpty())
			return ChainInfos.of(0L, Optional.empty(), Optional.empty(), Optional.empty());

		var maybeHeadHash = getHeadHash(txn);
		if (maybeHeadHash.isEmpty())
			throw new UncheckedDatabaseException("The hash of the genesis is set but there is no head hash set in the database");

		OptionalLong maybeChainHeight = getHeightOfHead(txn);
		if (maybeChainHeight.isEmpty())
			throw new UncheckedDatabaseException("The hash of the genesis is set but the height of the current best chain is missing");

		Optional<byte[]> maybeStateId = getStateIdOfHead(txn);
		if (maybeStateId.isEmpty())
			throw new UncheckedDatabaseException("The hash of the genesis is set but the state identifier for the head is missing");

		return ChainInfos.of(maybeChainHeight.getAsLong() + 1, maybeGenesisHash, maybeHeadHash, maybeStateId);
	}

	private Stream<byte[]> getChain(Transaction txn, long start, int count) {
		try {
			if (start < 0L || count <= 0)
				return Stream.empty();

			OptionalLong chainHeight = getHeightOfHead(txn);
			if (chainHeight.isEmpty())
				return Stream.empty();

			var hashes = LongStream.range(start, Math.min(start + count, chainHeight.getAsLong() + 1))
				.mapToObj(height -> storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(height))))
				.toArray(ByteIterable[]::new);

			for (var bi: hashes)
				if (bi == null)
					throw new UncheckedDatabaseException("The current best chain misses an element");

			return Stream.of(hashes).map(ByteIterable::getBytes);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	private void addToForwards(Transaction txn, NonGenesisBlock block, byte[] hashOfBlock) {
		try {
			var hashOfPrevious = fromBytes(block.getHashOfPreviousBlock());
			var oldForwards = storeOfForwards.get(txn, hashOfPrevious);
			var newForwards = fromBytes(oldForwards != null ? concat(oldForwards.getBytes(), hashOfBlock) : hashOfBlock);
			storeOfForwards.put(txn, hashOfPrevious, newForwards);
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
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

	private static byte[] concat(byte[] array1, byte[] array2) {
		var merge = new byte[array1.length + array2.length];
		System.arraycopy(array1, 0, merge, 0, array1.length);
		System.arraycopy(array2, 0, merge, array1.length, array2.length);
		return merge;
	}

	/**
	 * A marshallable reference to a transaction inside a block.
	 */
	private static class TransactionRef extends AbstractMarshallable {

		/**
		 * The height of the block in the current chain, containing the transaction.
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
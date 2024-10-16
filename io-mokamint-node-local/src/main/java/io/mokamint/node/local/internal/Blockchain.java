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

import static io.hotmoka.exceptions.CheckRunnable.check;
import static io.hotmoka.exceptions.UncheckConsumer.uncheck;
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
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
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
import io.hotmoka.exceptions.CheckRunnable;
import io.hotmoka.exceptions.CheckSupplier;
import io.hotmoka.exceptions.UncheckConsumer;
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
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.DatabaseException;
import io.mokamint.node.TransactionAddresses;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.api.TransactionRejectedException;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.Mempool.TransactionEntry;
import io.mokamint.node.remote.api.RemotePublicNode;

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
	 * The size of the group of blocks whose hashes get downloaded in one shot during synchronization.
	 */
	private final int synchronizationGroupSize;

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
	 * @throws NodeException if the node is misbehaving
	 */
	public Blockchain(LocalNodeImpl node) throws NodeException {
		super(ClosedDatabaseException::new);

		this.node = node;
		LocalNodeConfig config = node.getConfig();
		this.hashingForBlocks = config.getHashingForBlocks();
		this.hasherForTransactions = config.getHashingForTransactions().getHasher(io.mokamint.node.api.Transaction::toByteArray);
		this.maximalHistoryChangeTime = config.getMaximalHistoryChangeTime();
		this.synchronizationGroupSize = config.getSynchronizationGroupSize();
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
				environment.close(); // stopNewCalls() guarantees that there are no unfinished transactions at this moment
				LOGGER.info("blockchain: closed the blocks database");
			}
			catch (ExodusException e) {
				// TODO not sure while this happens, it seems there might be transactions run for garbage collection,
				// that will consequently find a closed environment
				LOGGER.log(Level.SEVERE, "blockchain: failed to close the blocks database", e);
			}
		}
	}

	/**
	 * Determines if this blockchain is empty.
	 * 
	 * @return true if and only if this blockchain is empty
	 * @throws NodeException if the node is misbehaving
	 */
	public boolean isEmpty() throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () -> environment.computeInReadonlyTransaction(UncheckFunction.uncheck(this::isEmpty)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the hash of the genesis block in this blockchain, if any.
	 * 
	 * @return the hash of the genesis block, if any
	 * @throws NodeException if the node is misbehaving
	 */
	public Optional<byte[]> getGenesisHash() throws NodeException {
		// we use a cache to avoid repeated access for reading the hash of the genesis block, that is not allowed to change after being set
		if (genesisHashCache != null)
			return Optional.of(genesisHashCache.get().clone());

		Optional<byte[]> result;

		try (var scope = mkScope()) {
			result = CheckSupplier.check(NodeException.class, () -> environment.computeInReadonlyTransaction(UncheckFunction.uncheck(this::getGenesisHash)));
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
	 * @throws NodeException if the node is misbehaving
	 */
	public Optional<GenesisBlock> getGenesis() throws NodeException {
		// we use a cache to avoid repeated access for reading the genesis block, that is not allowed to change after being set
		if (genesisCache != null)
			return genesisCache;

		Optional<GenesisBlock> result;

		try (var scope = mkScope()) {
			result = CheckSupplier.check(NodeException.class, () ->
				environment.computeInReadonlyTransaction(UncheckFunction.uncheck(this::getGenesis))
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
	 * @throws NodeException if the node is misbehaving
	 */
	public Optional<Block> getHead() throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () ->
				environment.computeInReadonlyTransaction(UncheckFunction.uncheck(this::getHead))
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
	 * @throws NodeException if the node is misbehaving
	 */
	/*public Optional<byte[]> getHeadHash() throws NodeException {
		try (var scope = mkScope()) {
			return check(NodeException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getHeadHash)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}*/

	/**
	 * Yields the starting block of the non-frozen part of the history of this blockchain, if any.
	 * 
	 * @return the starting block, if any
	 * @throws NodeException if the node is misbehaving
	 */
	public Optional<Block> getStartOfNonFrozenPart() throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () ->
				environment.computeInReadonlyTransaction(UncheckFunction.uncheck(this::getStartOfNonFrozenPart))
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
	 * @throws NodeException if the node is misbehaving
	 */
	public Optional<LocalDateTime> getStartingTimeOfNonFrozenHistory() throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () ->
				environment.computeInReadonlyTransaction(UncheckFunction.uncheck(this::getStartingTimeOfNonFrozenHistory))
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
	 * @throws NodeException if the node is misbehaving
	 */
	public OptionalLong getHeightOfHead() throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () -> environment.computeInReadonlyTransaction(UncheckFunction.uncheck(this::getHeightOfHead)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the power of the head of the best chain in this blockchain, if any.
	 * 
	 * @return the power of the head block, if any
	 * @throws NodeException if the node is misbehaving
	 */
	/*public Optional<BigInteger> getPowerOfHead() throws NodeException {
		try (var scope = mkScope()) {
			return check(NodeException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getPowerOfHead)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}*/

	/**
	 * Yields the block with the given hash, if it is contained in this blockchain.
	 * 
	 * @param hash the hash
	 * @return the block, if any
	 * @throws NodeException if the node is misbehaving
	 */
	public Optional<Block> getBlock(byte[] hash) throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () ->
				environment.computeInReadonlyTransaction(UncheckFunction.uncheck(txn -> getBlock(txn, hash)))
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
	 * @throws NodeException if the node is misbehaving
	 */
	public Optional<BlockDescription> getBlockDescription(byte[] hash) throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () ->
				environment.computeInReadonlyTransaction(UncheckFunction.uncheck(txn -> getBlockDescription(txn, hash)))
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
	 * @throws NodeException if the node is misbehaving
	 */
	public Optional<io.mokamint.node.api.Transaction> getTransaction(byte[] hash) throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () ->
				environment.computeInReadonlyTransaction(UncheckFunction.uncheck(txn -> getTransaction(txn, hash)))
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
	 * @throws NodeException if the node is misbehaving
	 */
	public Optional<TransactionAddress> getTransactionAddress(byte[] hash) throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () ->
				environment.computeInReadonlyTransaction(UncheckFunction.uncheck(txn -> getTransactionAddress(txn, hash)))
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
	 * @throws NodeException if the node is misbehaving
	 */
	public Optional<TransactionAddress> getTransactionAddress(Block block, byte[] hash) throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () ->
				environment.computeInReadonlyTransaction(UncheckFunction.uncheck(txn -> getTransactionAddress(txn, block, hash)))
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
	 * @throws NodeException if the node is misbehaving
	 */
	public ChainInfo getChainInfo() throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () -> environment.computeInReadonlyTransaction(UncheckFunction.uncheck(this::getChainInfo)));
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
	 * @throws NodeException if the node is misbehaving
	 */
	public Stream<byte[]> getChain(long start, int count) throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () -> environment.computeInReadonlyTransaction(UncheckFunction.uncheck(txn -> getChain(txn, start, count))));
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
	 * @throws NodeException if the node is misbehaving
	 */
	public boolean containsBlock(byte[] hash) throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () -> environment.computeInReadonlyTransaction(UncheckFunction.uncheck(txn -> containsBlock(txn, hash))));
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
	 * @throws NodeException if the node is misbehaving
	 */
	public boolean headIsLessPowerfulThan(Block block) throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () -> environment.computeInReadonlyTransaction(UncheckFunction.uncheck(txn -> headIsLessPowerfulThan(txn, block))));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the creation time of the given block, if it were to be added to this blockchain.
	 * For genesis blocks, their creation time is explicit in the block.
	 * For non-genesis blocks, this method adds the total waiting time of the block to the time of the genesis
	 * of this blockchain.
	 * 
	 * @param block the block whose creation time is computed
	 * @return the creation time of {@code block}; this is empty only for non-genesis blocks if the blockchain is empty
	 * @throws NodeException if the node is misbehaving
	 */
	public Optional<LocalDateTime> creationTimeOf(Block block) throws NodeException {
		try (var scope = mkScope()) {
			return CheckSupplier.check(NodeException.class, () -> environment.computeInReadonlyTransaction(UncheckFunction.uncheck(txn -> creationTimeOf(txn, block))));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Initializes this blockchain, which must be empty. It adds a genesis block.
	 * 
	 * @throws AlreadyInitializedException if this blockchain is already initialized (non-empty)
	 * @throws InvalidKeyException if the key of the node is invalid
	 * @throws SignatureException if the genesis block could not be signed
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws TimeoutException if some operation timed out
	 * @throws NodeException if the node is misbehaving
	 */
	public void initialize() throws AlreadyInitializedException, InvalidKeyException, SignatureException, InterruptedException, TimeoutException, NodeException {
		if (!isEmpty())
			throw new AlreadyInitializedException("Initialization cannot be required for an already initialized blockchain");

		var config = node.getConfig();
		var keys = node.getKeys();

		// TODO
		/*
		var generationSignature = new byte[config.getHashingForGenerations().length()];
		generationSignature[0] = (byte) 0x80;
		var acceleration = new BigInteger(1, generationSignature).divide(BigInteger.valueOf(config.getTargetBlockCreationTime()));
		*/

		var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), keys.getPublic());

		try {
			var genesis = Blocks.genesis(description, node.getApplication().getInitialStateId(), keys.getPrivate());
			addVerified(genesis);
		}
		catch (ApplicationException e) {
			// this node is misbehaving because the application it is connected to is misbehaving
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
	 * @throws NodeException if the node is misbehaving
	 * @throws VerificationException if {@code block} cannot be added since it does not respect all consensus rules
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws TimeoutException if some operation timed out
	 */
	public boolean add(Block block) throws NodeException, VerificationException, InterruptedException, TimeoutException {
		return add(block, true);
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
	 * @throws TimeoutException if some operation timed out
	 */
	public boolean addVerified(Block block) throws NodeException, InterruptedException, TimeoutException {
		try {
			return add(block, false);
		}
		catch (VerificationException e) {
			// impossible: we did not require block verification hence this exception should not have been generated
			throw new NodeException("Unexpected exception", e);
		}
	}

	public void synchronize() throws InterruptedException, TimeoutException, NodeException {
		DownloadedGroupOfBlocks group = CheckSupplier.check(NodeException.class, InterruptedException.class, TimeoutException.class, () ->
			environment.computeInExclusiveTransaction(UncheckFunction.uncheck(DownloadedGroupOfBlocks::new))
		);

		group.updateMempool();
		group.informNode();

		while (group.thereMightBeMoreGroupsToDownload()) {
			DownloadedGroupOfBlocks previousGroup = group;

			group = CheckSupplier.check(NodeException.class, InterruptedException.class, TimeoutException.class, () ->
				environment.computeInExclusiveTransaction(UncheckFunction.uncheck(txn -> new DownloadedGroupOfBlocks(txn, previousGroup)))
			);

			group.updateMempool();
			group.informNode();
		}

		node.onSynchronizationCompleted();
	}

	public void rebase(Mempool mempool, Block newBase) throws NodeException, InterruptedException, TimeoutException {
		CheckSupplier.check(NodeException.class, InterruptedException.class, TimeoutException.class, () ->
			environment.computeInReadonlyTransaction(UncheckFunction.uncheck(txn -> new Rebase(txn, mempool, newBase)))
		)
		.updateMempool();
	}

	/**
	 * A context for the addition of one or more blocks to this blockchain, inside the same databse transaction.
	 */
	public class BlockAdder {

		/**
		 * The database transaction where the additions are performed.
		 */
		private final Transaction txn;

		/**
		 * The hash of the head before performing the additions with this adder, if any.
		 */
		private final Optional<byte[]> initialHeadHash;

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
		 * The blocks that can be added among the orphan blocks after the additions have been performed.
		 */
		private final Set<NonGenesisBlock> blocksToAddAmongOrphans = new HashSet<>();

		/**
		 * The blocks that can be removed from the orphan blocks after the additions have been performed.
		 */
		private final Set<NonGenesisBlock> blocksToRemoveFromOrphans = new HashSet<>();

		/**
		 * Creates a context for the addition of blocks, inside the same database transaction.
		 * 
		 * @param txn the database transaction
		 * @throws NodeException if the node is misbehaving
		 */
		public BlockAdder(Transaction txn) throws NodeException {
			this.txn = txn;
			this.initialHeadHash = getHeadHash(txn);
		}

		/**
		 * Adds the given block to this blockchain, allowing one to specify if block verification is required.
		 * 
		 * @param block the block to add
		 * @param verify true if and only if verification of {@code block} must be performed
		 * @return this same adder
		 * @throws NodeException if the node is misbehaving
		 * @throws VerificationException if {@code block} cannot be added since it does not respect the consensus rules
		 * @throws InterruptedException if the current thread is interrupted
		 * @throws TimeoutException if some operation timed out
		 */
		public BlockAdder add(Block block, boolean verify) throws NodeException, VerificationException, InterruptedException, TimeoutException {
			byte[] hashOfBlockToAdd = block.getHash(hashingForBlocks);

			// optimization check, to avoid repeated verification
			if (containsBlock(txn, hashOfBlockToAdd))
				LOGGER.warning("blockchain: not adding block " + Hex.toHexString(hashOfBlockToAdd) + " since it is already in the database");

			addBlockAndConnectOrphans(block, verify);
			computeBlocksAddedToTheCurrentBestChain();

			return this;
		}

		public void informNode() {
			blocksToAddAmongOrphans.forEach(this::putAmongOrphans);
			blocksToRemoveFromOrphans.forEach(this::removeFromOrphans);
			blocksAdded.forEach(node::onAdded);

			if (!blocksAddedToTheCurrentBestChain.isEmpty())
				node.onHeadChanged(blocksAddedToTheCurrentBestChain);
		}

		public void updateMempool() throws NodeException, InterruptedException, TimeoutException {
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
		 * Schedules a synchronization operation if it might be worthwhile, since, for instance,
		 * an orphan block has been added, that is better than the current head.
		 * 
		 * @throws NodeException if the node is misbehaving
		 */
		public void scheduleSynchronizationIfUseful() throws NodeException {
			for (var newOrphan: blocksToAddAmongOrphans)
				if (headIsLessPowerfulThan(newOrphan)) {
					// the new orphan was better than our current head: we synchronize from our peers
					node.scheduleSynchronization();
					break;
				}
		}

		/**
		 * Determines if this blockchain has been expanded with this adder, not necessarily
		 * along its best chain. The addition of orphan blocks is not considered as an expansion.
		 * 
		 * @return true if and only if this blockchain has been expanded
		 */
		public boolean somethingHasBeenAdded() {
			return !blocksAdded.isEmpty();
		}

		private void addBlockAndConnectOrphans(Block blockToAdd, boolean verify) throws NodeException, VerificationException, InterruptedException, TimeoutException {
			// we use a working set, since the addition of a single block might trigger the further addition of orphan blocks, recursively
			var ws = new ArrayList<Block>();
			ws.add(blockToAdd);

			do {
				Block cursor = ws.remove(ws.size() - 1);
				Optional<Block> previous = Optional.empty();

				if (cursor instanceof GenesisBlock || (previous = getBlock(txn, ((NonGenesisBlock) cursor).getHashOfPreviousBlock())).isPresent()) {
					byte[] hashOfCursor = cursor.getHash(hashingForBlocks);
					if (add(cursor, hashOfCursor, previous, blockToAdd != cursor, verify)) {
						blocksAdded.add(cursor);
						forEachOrphanWithParent(hashOfCursor, ws::add);
						if (cursor instanceof NonGenesisBlock ngb && cursor != blockToAdd)
							blocksToRemoveFromOrphans.add(ngb);
					}
				}
				else if (cursor instanceof NonGenesisBlock ngb)
					blocksToAddAmongOrphans.add(ngb);
			}
			while (!ws.isEmpty());
		}

		private void computeBlocksAddedToTheCurrentBestChain() throws NodeException {
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
					blocksAddedToTheCurrentBestChain.addLast(getBlock(txn, hashOfBlockFromBestChain.getBytes()).orElseThrow(() -> new DatabaseException("Cannot follow the new best chain upwards")));
			}
			while (hashOfBlockFromBestChain != null);
		}

		private boolean add(Block blockToAdd, byte[] hashOfBlockToAdd, Optional<Block> previous, boolean isOrphan, boolean verify) throws NodeException, VerificationException, InterruptedException, TimeoutException {
			if (verify || isOrphan) {
				try {
					new BlockVerification(txn, node, blockToAdd, previous);
				}
				catch (VerificationException e) {
					if (isOrphan) {
						LOGGER.warning("blockchain: discarding orphan block " + Hex.toHexString(hashOfBlockToAdd) + " since it does not pass verification: " + e.getMessage());
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
		 * Adds a block to the tree of blocks rooted at the genesis block (if any), running
		 * inside a given transaction. It updates the references to the genesis and to the
		 * head of the longest chain, if needed.
		 * 
		 * @param txn the transaction
		 * @param block the block to add
		 * @param hashOfBlock the hash of {@code block}
		 * @param updatedHead the new head resulting from the addition, if it changed wrt the previous head
		 * @return true if and only if the block has been added. False means that
		 *         the block was already in the tree; or that {@code block} is a genesis
		 *         block and there is already a genesis block in the tree; or that {@code block}
		 *         is a non-genesis block whose previous is not in the tree
		 * @throws NodeException if the node is misbehaving
		 */
		private boolean add(Block block, byte[] hashOfBlock, Optional<Block> previous) throws NodeException {
			if (block instanceof NonGenesisBlock ngb) {
				if (isInFrozenPart(txn, previous.get().getDescription())) {
					LOGGER.warning("blockchain: not adding block " + Hex.toHexString(hashOfBlock) + " since its previous block is in the frozen part of the blockchain");
					return false;
				}
				else if (containsBlock(txn, hashOfBlock)) {
					LOGGER.warning("blockchain: not adding block " + Hex.toHexString(hashOfBlock) + " since it is already present in blockchain");
					return false;
				}
				else {
					putInStore(txn, hashOfBlock, block.toByteArray());
					addToForwards(txn, ngb, hashOfBlock);
					if (isBetterThanHead(txn, ngb, hashOfBlock))
						setHead(block, hashOfBlock);

					LOGGER.info("blockchain: height " + block.getDescription().getHeight() + ": added block " + Hex.toHexString(hashOfBlock));
					return true;
				}
			}
			else {
				if (isEmpty(txn)) {
					putInStore(txn, hashOfBlock, block.toByteArray());
					setGenesisHash(txn, hashOfBlock);
					setHead(block, hashOfBlock);
					LOGGER.info("blockchain: height " + block.getDescription().getHeight() + ": added block " + Hex.toHexString(hashOfBlock));
					return true;
				}
				else {
					LOGGER.warning("blockchain: not adding genesis block " + Hex.toHexString(hashOfBlock) + " since the database already contains a genesis block");
					return false;
				}
			}
		}

		/**
		 * Sets the head of the best chain in this database, running inside a transaction.
		 * 
		 * @param txn the transaction
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

		private void addReferencesToTransactionsInside(Block block) {
			if (block instanceof NonGenesisBlock ngb) {
				long height = ngb.getDescription().getHeight();
				int count = ngb.getTransactionsCount();
				for (int pos = 0; pos < count; pos++) {
					var ref = new TransactionRef(height, pos);
					storeOfTransactions.put(txn, ByteIterable.fromBytes(hasherForTransactions.hash(ngb.getTransaction(pos))), ByteIterable.fromBytes(ref.toByteArray()));
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
			synchronized (orphans) {
				// we also look in the orphans that have been added during the life of this adder
				Stream.concat(Stream.of(orphans), blocksToAddAmongOrphans.stream())
					.filter(Objects::nonNull)
					.filter(orphan -> Arrays.equals(orphan.getHashOfPreviousBlock(), hashOfParent))
					.filter(orphan -> !blocksToRemoveFromOrphans.contains(orphan))
					.forEach(action);
			}
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

		private void removeDataHigherThan(Transaction txn, long height) throws NodeException {
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
						var blockHashCopy = blockHash;
						block = getBlock(txn, hashOfPrevious).orElseThrow(() -> new DatabaseException("Block " + Hex.toHexString(blockHashCopy) + " has no previous block in the database"));
						blockHash = hashOfPrevious;
					}
					else
						throw new DatabaseException("The current best chain contains a genesis block " + Hex.toHexString(blockHash) + " at height " + blockHeight);
				}
			}
		}

		private void removeReferencesToTransactionsInside(Transaction txn, Block block) {
			if (block instanceof NonGenesisBlock ngb) {
				int count = ngb.getTransactionsCount();
				for (int pos = 0; pos < count; pos++)
					storeOfTransactions.delete(txn, ByteIterable.fromBytes(hasherForTransactions.hash(ngb.getTransaction(pos))));
			}
		}

		/**
		 * Yields the orphans having the given parent.
		 * 
		 * @param hashOfParent the hash of {@code parent}
		 * @return the orphans whose previous block is {@code parent}, if any
		 */
		private void removeFromOrphans(Block blockToRemove) {
			synchronized (orphans) {
				for (int pos = 0; pos < orphans.length; pos++)
					if (orphans[pos] == blockToRemove)
						orphans[pos] = null;
			}
		}
	}

	public class DownloadedGroupOfBlocks {
	
		/**
		 * The database transaction where the synchronization occurs.
		 */
		private final Transaction txn;
	
		/**
		 * The height of the next block whose hash must be downloaded.
		 */
		private final long height;
	
		/**
		 * The object used to add blocks to the blockchain, during synchronization.
		 */
		private final BlockAdder blockAdder;
	
		/**
		 * The peers of the node.
		 */
		private final Peers peers = node.getPeers();
	
		/**
		 * The peers that have been discarded so far during this synchronization, since
		 * for instance they timed out or provided illegal blocks.
		 */
		private final Set<Peer> unusable;
	
		/**
		 * The last groups of hashes downloaded, for each peer.
		 */
		private final ConcurrentMap<Peer, byte[][]> groups = new ConcurrentHashMap<>();

		/**
		 * The last hash in the previous group loaded before this group. This is used
		 * to compare it against the beginning of the new group loaded by this object,
		 * in order to check that they actually match. The first download has no previous group.
		 */
		private final Optional<byte[]> lastHashOfPreviousGroup;

		/**
		 * The group in {@link #groups} that has been selected as more
		 * reliable chain, because the most peers agree on its hashes.
		 */
		private final byte[][] chosenGroup;
	
		/**
		 * The downloaded blocks, whose hashes are in {@link #chosenGroup}.
		 */
		private final AtomicReferenceArray<Block> blocks;
	
		/**
		 * Semaphores used to avoid having two peers downloading the same block.
		 */
		private final Semaphore[] semaphores;
	
		/**
		 * A map from each downloaded block to the peer that downloaded that block.
		 * This is used to blame that peer if the block is not verifiable.
		 */
		private final ConcurrentMap<Block, Peer> downloaders = new ConcurrentHashMap<>();
	
		public DownloadedGroupOfBlocks(Transaction txn) throws InterruptedException, TimeoutException, NodeException {
			this(txn,
				Math.max(getStartOfNonFrozenPart(txn).map(Block::getDescription).map(BlockDescription::getHeight).orElse(0L), getHeightOfHead(txn).orElse(0L) - 1000L),
				Optional.empty(),
				ConcurrentHashMap.newKeySet());
		}

		public DownloadedGroupOfBlocks(Transaction txn, DownloadedGroupOfBlocks previous) throws InterruptedException, TimeoutException, NodeException {
			// -1 is used in order the link the next group with the previous one: they must coincide for the first (respectively, last) block hash
			this(txn, previous.height  + synchronizationGroupSize - 1, Optional.of(previous.chosenGroup[previous.chosenGroup.length - 1]), previous.unusable);
		}

		private DownloadedGroupOfBlocks(Transaction txn, long height, Optional<byte[]> lastHashOfPreviousGroup, Set<Peer> unusable) throws InterruptedException, TimeoutException, NodeException {
			this.txn = txn;
			this.blockAdder = new BlockAdder(txn);
			this.lastHashOfPreviousGroup = lastHashOfPreviousGroup;
			this.unusable = unusable;
			this.height = height;
		
			if (!downloadNextGroupFromEachPeer()) {
				LOGGER.info("sync: stop here since the peers do not provide more block hashes to download");
				this.chosenGroup = null;
				this.semaphores = null;
				this.blocks = null;
				return;
			}
		
			this.chosenGroup = chooseMostReliableGroup();
			this.semaphores = new Semaphore[chosenGroup.length];
			this.blocks = new AtomicReferenceArray<Block>(chosenGroup.length);
			downloadBlocks();
		
			if (!addBlocksToBlockchain()) {
				LOGGER.info("sync: stop here since no more verifiable blocks can be downloaded");
				return;
			}
		
			keepOnlyPeersAgreeingOnTheChosenGroup();
		}

		public boolean thereMightBeMoreGroupsToDownload() {
			return chosenGroup != null && chosenGroup.length == synchronizationGroupSize;
		}
	
		public void updateMempool() throws NodeException, InterruptedException, TimeoutException {
			blockAdder.updateMempool();
		}
	
		public void informNode() {
			blockAdder.informNode();
		}
	
		/**
		 * Checks if the current thread has been interrupted and, in that case, throws an exception.
		 * 
		 * @throws InterruptedException if and only if the current thread has been interrupted
		 */
		private static void stopIfInterrupted() throws InterruptedException {
			if (Thread.currentThread().isInterrupted())
				throw new InterruptedException("Interrupted");
		}
	
		/**
		 * Downloads the next group of hashes with each available peer.
		 * 
		 * @return true if and only if at least a group could be downloaded,
		 *         from at least one peer; if false, synchronization must stop here
		 * @throws InterruptedException if the execution has been interrupted
		 * @throws NodeException if the node is misbehaving
		 */
		private boolean downloadNextGroupFromEachPeer() throws InterruptedException, NodeException {
			stopIfInterrupted();
			LOGGER.info("sync: downloading the hashes of the blocks at height [" + height + ", " + (height + synchronizationGroupSize) + ")");
	
			check(InterruptedException.class, NodeException.class, () -> {
				peers.get().parallel()
					.filter(PeerInfo::isConnected)
					.map(PeerInfo::getPeer)
					.filter(peer -> !unusable.contains(peer))
					.forEach(uncheck(this::downloadNextGroupFrom));
			});
	
			return !groups.isEmpty();
		}
	
		/**
		 * Download, into the {@link #groups} map, the next group of hashes with the given peer.
		 * 
		 * @param peer the peer
		 * @throws InterruptedException if the execution has been interrupted
		 * @throws NodeException if the node is misbehaving
		 */
		private void downloadNextGroupFrom(Peer peer) throws InterruptedException, NodeException {
			Optional<RemotePublicNode> maybeRemote = peers.getRemote(peer);
			if (maybeRemote.isEmpty())
				return;
	
			ChainPortion chain;
	
			try {
				chain = maybeRemote.get().getChainPortion(height, synchronizationGroupSize);
			}
			catch (NodeException e) {
				// it is the peer that is misbehaving, not {@code node}
				markAsMisbehaving(peer);
				return;
			}
			catch (TimeoutException e) {
				markAsUnreachable(peer);
				return;
			}
	
			var hashes = chain.getHashes().toArray(byte[][]::new);
			if (hashes.length > synchronizationGroupSize)
				markAsMisbehaving(peer); // if a peer sends inconsistent information, we take note
			else if (groupIsUseless(hashes))
				unusable.add(peer);
			else
				groups.put(peer, hashes);
		}
	
		/**
		 * Determines if the given group of hashes can be discarded since it does not match some expected constraints.
		 * 
		 * @param hashes the group of hashes
		 * @return true if and only if it can be discarded
		 * @throws NodeException if the node is misbehaving
		 */
		private boolean groupIsUseless(byte[][] hashes) throws NodeException {
			Optional<byte[]> genesisHash;
	
			// the first hash must coincide with the last hash of the previous group, or otherwise the peer is cheating
			// or there has been a change in the best chain and we must anyway stop downloading blocks here from this group
			if (hashes.length > 0 && lastHashOfPreviousGroup.isPresent() && !Arrays.equals(hashes[0], lastHashOfPreviousGroup.get()))
				return true;
			// if synchronization occurs from the genesis and the genesis of the blockchain is set,
			// then the first hash must be that genesis' hash
			else if (hashes.length > 0 && height == 0L && (genesisHash = getGenesisHash(txn)).isPresent() && !Arrays.equals(hashes[0], genesisHash.get()))
				return true;
			// if synchronization starts from above the genesis, the first hash must be in the blockchain of the node or otherwise the hashes are useless
			else if (hashes.length > 0 && lastHashOfPreviousGroup.isEmpty() && height > 0L && !containsBlock(txn, hashes[0]))
				return true;
			else
				return false;
		}
	
		private void markAsMisbehaving(Peer peer) throws NodeException, InterruptedException {
			unusable.add(peer);
			peers.ban(peer);
		}
	
		private void markAsUnreachable(Peer peer) throws NodeException, InterruptedException {
			unusable.add(peer);
			peers.punishBecauseUnreachable(peer);
		}
	
		/**
		 * Selects the group in {@link #groups} that looks as the most reliable, since the most peers agree on its hashes.
		 * 
		 * @throws InterruptedException if the current thread gets interrupted
		 */
		private byte[][] chooseMostReliableGroup() throws InterruptedException {
			stopIfInterrupted();
			var alternatives = new HashSet<byte[][]>(groups.values());
	
			for (int h = 1; h < synchronizationGroupSize && alternatives.size() > 1; h++) {
				Optional<byte[][]> mostFrequent = findMostFrequent(alternatives, h);
				// there might be no alternatives with at least h hashes
				if (mostFrequent.isEmpty())
					break;
	
				byte[] mostFrequentHash = mostFrequent.get()[h];
				for (byte[][] alternative: new HashSet<>(alternatives))
					if (alternative.length <= h || !Arrays.equals(alternative[h], mostFrequentHash))
						alternatives.remove(alternative);
			}
	
			// the remaining alternatives actually coincide: just take one
			return alternatives.stream().findAny().get();
		}
	
		/**
		 * Yields the alternative whose {@code h}'s hash is the most frequent
		 * among the given {@code alternatives}.
		 * 
		 * @param alternatives the alternatives
		 * @param h the index of the compared hash of the alternatives
		 * @return the alternative whose {@code h}'s hash is the most frequent among {@code alternatives};
		 *         this is missing when all alternatives have fewer than {@code h} hashes
		 */
		private Optional<byte[][]> findMostFrequent(Set<byte[][]> alternatives, int h) {
			byte[][] result = null;
			long bestFrequency = 0L;
			for (byte[][] alternative: alternatives)
				if (alternative.length > h) {
					long frequency = computeFrequency(alternative, alternatives, h);
					if (frequency > bestFrequency) {
						bestFrequency = frequency;
						result = alternative;
					}
				}
	
			return Optional.ofNullable(result);
		}
	
		/**
		 * Counts how many {@code alternatives} have their {@code h}'s hash coinciding
		 * with that of {@code alternative}.
		 * 
		 * @param alternative the reference alternative
		 * @param alternatives the alternatives
		 * @param h the height of the counted hash
		 * @return the count; this is 0 if all alternatives have fewer than {@code h} hashes
		 */
		private long computeFrequency(byte[][] alternative, Set<byte[][]> alternatives, int h) {
			return alternatives.stream()
				.filter(hashes -> hashes.length > h)
				.map(hashes -> hashes[h])
				.filter(hash -> Arrays.equals(hash, alternative[h]))
				.count();
		}
	
		/**
		 * Downloads as many blocks as possible whose hashes are in {@link #chosenGroup},
		 * by using the peers whose group agrees on such hashes, in parallel. Some block
		 * might no be downloaded if all peers time out or no peer contains that block.
		 * 
		 * @throws InterruptedException if the current thread gets interrupted
		 * @throws NodeException if the node is misbehaving
		 */
		private void downloadBlocks() throws InterruptedException, NodeException {
			stopIfInterrupted();
			Arrays.setAll(semaphores, _index -> new Semaphore(1));
	
			LOGGER.info("sync: downloading the blocks at height [" + height + ", " + (height + chosenGroup.length) + ")");
	
			check(InterruptedException.class, NodeException.class, () -> {
				peers.get().parallel()
					.filter(PeerInfo::isConnected)
					.map(PeerInfo::getPeer)
					.filter(peer -> !unusable.contains(peer))
					.forEach(UncheckConsumer.uncheck(peer -> downloadBlocksFrom(peer)));
			});
		}
	
		private void downloadBlocksFrom(Peer peer) throws NodeException, InterruptedException {
			byte[][] ownGroup = groups.get(peer);
			if (ownGroup != null) {
				var alreadyTried = new boolean[chosenGroup.length];
	
				// we try twice: the second time to help peers that are trying to download something
				for (int time = 1; time <= 2; time++) {
					for (int h = chosenGroup.length - 1; h >= 0; h--)
						if (canDownload(peer, h, ownGroup, alreadyTried))
							if (time == 2 || semaphores[h].tryAcquire()) {
								try {
									alreadyTried[h] = true;
									tryToDownloadBlockFrom(peer, h);
								}
								finally {
									if (time == 1)
										semaphores[h].release();
								}
							}
				}
			}
		}
	
		/**
		 * Determines if the given peer could be used to download the block with the {@code h}th hash in {@link #chosenGroup}.
		 * 
		 * @param peer the peer
		 * @param h the index of the hash
		 * @param ownGroup the group of hashes for the peer
		 * @param alreadyTried information about which hashes have already been tried with this same peer
		 * @return true if and only if it is sensible to use {@code peer} to download the block
		 * @throws NodeException if the node is misbehaving
		 */
		private boolean canDownload(Peer peer, int h, byte[][] ownGroup, boolean[] alreadyTried) throws NodeException {
			return !unusable.contains(peer) && !alreadyTried[h] && ownGroup.length > h && Arrays.equals(ownGroup[h], chosenGroup[h]) && !containsBlock(txn, chosenGroup[h]) && blocks.get(h) == null;
		}
	
		/**
		 * Tries to download the block with the {@code h}th hash in {@link #chosenGroup}, from the given peer.
		 * 
		 * @param peer the peer
		 * @param h the height of the hash
		 * @throws InterruptedException if the executed was interrupted
		 * @throws NodeException if the node is misbehaving
		 */
		private void tryToDownloadBlockFrom(Peer peer, int h) throws InterruptedException, NodeException {
			var maybeRemote = peers.getRemote(peer);
			if (maybeRemote.isEmpty())
				unusable.add(peer);
			else {
				var remote = maybeRemote.get();
				Optional<Block> maybeBlock;
	
				try {
					maybeBlock = remote.getBlock(chosenGroup[h]);
				}
				catch (NodeException e) {
					markAsMisbehaving(peer);
					return;
				}
				catch (TimeoutException e) {
					markAsUnreachable(peer);
					return;
				}
	
				if (maybeBlock.isPresent()) {
					Block block = maybeBlock.get();
					if (!Arrays.equals(chosenGroup[h], block.getHash(hashingForBlocks)))
						// the peer answered with a block with the wrong hash!
						markAsMisbehaving(peer);
					else {
						blocks.set(h, block);
						downloaders.put(block, peer);
					}
				}
			}
		}
	
		/**
		 * Adds the {@link #blocks} to the blockchain, stopping at the first missing block
		 * or at the first block that cannot be verified.
		 * 
		 * @return true if and only if no block was missing and all blocks could be
		 *         successfully verified and added to blockchain; if false, synchronization must stop here
		 * @throws InterruptedException if the current thread gets interrupted during this method
		 * @throws TimeoutException if some operation timed out
		 * @throws NodeException if the node is misbehaving
		 */
		private boolean addBlocksToBlockchain() throws InterruptedException, TimeoutException, NodeException {
			for (int h = 0; h < chosenGroup.length; h++) {
				stopIfInterrupted();
	
				if (!containsBlock(txn, chosenGroup[h])) {
					Block block = blocks.get(h);
					if (block == null)
						return false;
	
					stopIfInterrupted();
	
					try {
						blockAdder.add(block, true);
					}
					catch (VerificationException e) {
						LOGGER.log(Level.SEVERE, "sync: verification of block " + block.getHexHash(hashingForBlocks) + " failed: " + e.getMessage());
						markAsMisbehaving(downloaders.get(block));
						return false;
					}
				}
			}
	
			return true;
		}
	
		/**
		 * Puts in the {@link #unusable} set all peers that downloaded a group
		 * different from {@link #chosenGroup}: in any case, their subsequent groups are more
		 * a less reliable history and won't be downloaded.
		 * 
		 * @throws InterruptedException if the current thread gets interrupted
		 */
		private void keepOnlyPeersAgreeingOnTheChosenGroup() throws InterruptedException {
			stopIfInterrupted();
			for (var entry: groups.entrySet())
				if (!Arrays.deepEquals(chosenGroup, entry.getValue()))
					unusable.add(entry.getKey());
		}
	}

	/**
	 * The algorithm for rebasing a mempool at a given, new base.
	 */
	public class Rebase {
		private final io.hotmoka.xodus.env.Transaction txn;
		private final Mempool mempool;
		private final Block newBase;
		private final Set<TransactionEntry> toRemove = new HashSet<>();
		private final Set<TransactionEntry> toAdd = new HashSet<>();
		private Block newBlock;
		private Block oldBlock;

		public Rebase(io.hotmoka.xodus.env.Transaction txn, Mempool mempool, Block newBase) throws NodeException, InterruptedException, TimeoutException {
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

		public void updateMempool() {
			mempool.addAll(toAdd.stream());
			mempool.removeAll(toRemove.stream());
			mempool.setBase(newBase);
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

		private void markToRemoveAllTransactionsInNewBlockAndMoveItBackwards() throws NodeException, InterruptedException, TimeoutException {
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

		private void markToRemoveAllTransactionsFromNewBaseToGenesis() throws NodeException, InterruptedException, TimeoutException {
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
		 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
		 * @throws TimeoutException if some operation timed out
		 * @throws NodeException if the node is misbehaving
		 */
		private void markAllTransactionsAsToRemove(NonGenesisBlock block) throws InterruptedException, TimeoutException, NodeException {
			CheckRunnable.check(InterruptedException.class, TimeoutException.class, NodeException.class, () ->
				block.getTransactions()
					.map(UncheckFunction.uncheck(this::intoTransactionEntry))
					.forEach(toRemove::add)
			);
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
		private TransactionEntry intoTransactionEntry(io.mokamint.node.api.Transaction transaction) throws InterruptedException, TimeoutException, NodeException {
			try {
				return new TransactionEntry(transaction, node.getApplication().getPriority(transaction), hasherForTransactions.hash(transaction));
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

		private Block getBlock(byte[] hash) throws NodeException {
			return Blockchain.this.getBlock(txn, hash).orElseThrow(() -> new DatabaseException("Missing block with hash " + Hex.toHexString(hash)));
		}
	}

	private boolean add(Block block, boolean verify) throws NodeException, VerificationException, InterruptedException, TimeoutException {
		BlockAdder adder;
	
		try (var scope = mkScope()) {
			adder = CheckSupplier.check(NodeException.class, VerificationException.class, InterruptedException.class, TimeoutException.class,
				() -> environment.computeInTransaction(UncheckFunction.uncheck(txn -> new BlockAdder(txn).add(block, verify)))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	
		adder.informNode();
		adder.updateMempool();
		adder.scheduleSynchronizationIfUseful();
	
		return adder.somethingHasBeenAdded();
	}

	private boolean isInFrozenPart(Transaction txn, BlockDescription blockDescription) throws NodeException {
		var totalWaitingTimeOfStartOfNonFrozenPart = getTotalWaitingTimeOfStartOfNonFrozenPart(txn);
		return totalWaitingTimeOfStartOfNonFrozenPart.isPresent() && totalWaitingTimeOfStartOfNonFrozenPart.getAsLong() > blockDescription.getTotalWaitingTime();
	}

	private Environment createBlockchainEnvironment() {
		var env = new Environment(node.getConfig().getDir().resolve("blocks").toString());
		LOGGER.info("blockchain: opened the blocks database");
		return env;
	}

	private Store openStore(String name) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<byte[]> getHeadHash(Transaction txn) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<byte[]> getStateIdOfHead(Transaction txn) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<byte[]> getStartOfNonFrozenPartHash(Transaction txn) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<LocalDateTime> getStartingTimeOfNonFrozenHistory(Transaction txn) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<byte[]> getGenesisHash(Transaction txn) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<BigInteger> getPowerOfHead(Transaction txn) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	private OptionalLong getHeightOfHead(Transaction txn) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	private OptionalLong getTotalWaitingTimeOfStartOfNonFrozenPart(Transaction txn) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	private Stream<byte[]> getForwards(Transaction txn, byte[] hash) throws NodeException {
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
	 * Yields the block with the given hash, if it is contained in this blockchain,
	 * running inside the given transaction.
	 * 
	 * @param txn the transaction
	 * @param hash the hash
	 * @return the block, if any
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<Block> getBlock(Transaction txn, byte[] hash) throws NodeException {
		try {
			ByteIterable blockBI = storeOfBlocks.get(txn, fromBytes(hash));
			if (blockBI == null)
				return Optional.empty();
			
			try (var bais = new ByteArrayInputStream(blockBI.getBytes()); var context = UnmarshallingContexts.of(bais)) {
				return Optional.of(Blocks.from(context, node.getConfig()));
			}
		}
		catch (ExodusException | IOException e) {
			throw new DatabaseException(e);
		}
		catch (NoSuchAlgorithmException e) {
			throw new NodeException(e);
		}
	}

	/**
	 * Yields the description of the block with the given hash, if it is contained in this database,
	 * running inside the given transaction.
	 * 
	 * @param txn the transaction
	 * @param hash the hash
	 * @return the description of the block, if any
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<BlockDescription> getBlockDescription(Transaction txn, byte[] hash) throws NodeException {
		try {
			ByteIterable blockBI = storeOfBlocks.get(txn, fromBytes(hash));
			if (blockBI == null)
				return Optional.empty();
			
			try (var bais = new ByteArrayInputStream(blockBI.getBytes()); var context = UnmarshallingContexts.of(bais)) {
				// the marshalling of a block starts with that of its description
				return Optional.of(BlockDescriptions.from(context, node.getConfig()));
			}
		}
		catch (ExodusException | IOException e) {
			throw new DatabaseException(e);
		}
		catch (NoSuchAlgorithmException e) {
			throw new NodeException(e);
		}
	}

	/**
	 * Yields the transaction with the given hash, if it is contained in some block of the best chain of this database,
	 * running inside the given transaction.
	 * 
	 * @param txn the database transaction
	 * @param hash the hash of the transaction to search
	 * @return the transaction, if any
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<io.mokamint.node.api.Transaction> getTransaction(Transaction txn, byte[] hash) throws NodeException {
		try {
			ByteIterable txBI = storeOfTransactions.get(txn, fromBytes(hash));
			if (txBI == null)
				return Optional.empty();

			var ref = TransactionRef.from(txBI);
			ByteIterable blockHash = storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(ref.height)));
			if (blockHash == null)
				throw new DatabaseException("The hash of the block of the best chain at height " + ref.height + " is not in the database");

			Block block = getBlock(txn, blockHash.getBytes())
				.orElseThrow(() -> new DatabaseException("The current best chain misses the block at height " + ref.height  + " with hash " + Hex.toHexString(blockHash.getBytes())));

			if (block instanceof NonGenesisBlock ngb)
				return Optional.of(ngb.getTransaction(ref.progressive));
			else
				throw new DatabaseException("Transaction " + Hex.toHexString(hash) + " seems contained in a genesis block, which is impossible");
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
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<TransactionAddress> getTransactionAddress(Transaction txn, byte[] hash) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	Optional<TransactionAddress> getTransactionAddress(Transaction txn, Block block, byte[] hash) throws NodeException {
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

	/**
	 * Yields the creation time of the given block, if it were to be added to this blockchain, running
	 * inside a transaction. For genesis blocks, their creation time is explicit in the block.
	 * For non-genesis blocks, this method adds the total waiting time of the block to the time of the genesis
	 * of this blockchain.
	 * 
	 * @param txn the Xodus transaction
	 * @param block the block whose creation time is computed
	 * @return the creation time of {@code block}; this is empty only for non-genesis blocks if the blockchain is empty
	 * @throws NodeException if the node is misbehaving
	 */
	Optional<LocalDateTime> creationTimeOf(Transaction txn, Block block) throws NodeException {
		if (block instanceof GenesisBlock gb)
			return Optional.of(gb.getStartDateTimeUTC());
		else {
			var maybeGenesis = getGenesis(txn);
			if (maybeGenesis.isEmpty())
				return Optional.empty();
			else
				return Optional.of(maybeGenesis.get().getStartDateTimeUTC().plus(block.getDescription().getTotalWaitingTime(), ChronoUnit.MILLIS));
		}
	}

	/**
	 * Determines if the given block is more powerful than the current head, running inside a transaction.
	 * 
	 * @param txn the Xodus transaction
	 * @param block the block
	 * @return true if and only if the current head is missing or {@code block} is more powerful
	 * @throws NodeException if the node is misbehaving
	 */
	private boolean headIsLessPowerfulThan(Transaction txn, Block block) throws NodeException {
		return getPowerOfHead(txn).map(power -> power.compareTo(block.getDescription().getPower()) < 0).orElse(true);
	}

	private boolean isContainedInTheBestChain(Transaction txn, Block block, byte[] blockHash) throws NodeException {
		try {
			var height = block.getDescription().getHeight();
			var hashOfBlockFromBestChain = storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(height)));
			return hashOfBlockFromBestChain != null && Arrays.equals(hashOfBlockFromBestChain.getBytes(), blockHash);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Determines if this blockchain is empty, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return true if and only if this blockchain is empty
	 * @throws NodeException if the node is misbehaving
	 */
	private boolean isEmpty(Transaction txn) throws NodeException {
		return getGenesisHash(txn).isEmpty();
	}

	/**
	 * Yields the head of the best chain in this database, if any, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @return the head, if any
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<Block> getHead(Transaction txn) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<Block> getStartOfNonFrozenPart(Transaction txn) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	private Optional<GenesisBlock> getGenesis(Transaction txn) throws NodeException {
		try {
			Optional<byte[]> maybeGenesisHash = getGenesisHash(txn);
			if (maybeGenesisHash.isEmpty())
				return Optional.empty();

			Block genesis = getBlock(txn, maybeGenesisHash.get()).orElseThrow(() -> new DatabaseException("The genesis hash is set but it is not in the database"));

			if (genesis instanceof GenesisBlock gb)
				return Optional.of(gb);
			else
				throw new DatabaseException("The genesis hash is set but it refers to a non-genesis block in the database");
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
	 * @throws NodeException if the node is misbehaving
	 */
	private void putInStore(Transaction txn, byte[] hashOfBlock, byte[] bytesOfBlock) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	private boolean containsBlock(Transaction txn, byte[] hashOfBlock) throws NodeException {
		try {
			return storeOfBlocks.get(txn, fromBytes(hashOfBlock)) != null;
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private boolean isBetterThanHead(Transaction txn, NonGenesisBlock block, byte[] hashOfBlock) throws NodeException {
		BigInteger powerOfHead = getPowerOfHead(txn).orElseThrow(() -> new DatabaseException("The database of blocks is non-empty but the power of the head is not set"));
		return block.getDescription().getPower().compareTo(powerOfHead) > 0;
	}

	/**
	 * Sets the hash of the genesis block in this database, running inside a transaction.
	 * 
	 * @param txn the transaction
	 * @param newGenesisHash the hash of the genesis block
	 * @throws NodeException if the node is misbehaving
	 */
	private void setGenesisHash(Transaction txn, byte[] newGenesisHash) throws NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 */
	private void setStartOfNonFrozenPartHash(Transaction txn, byte[] startOfNonFrozenPartHash) throws NodeException {
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
			}
			catch (ExodusException e) {
				throw new DatabaseException(e);
			}
			
			LOGGER.info("blockchain: garbage-collected block " + Hex.toHexString(currentHash));
		}
		while (!ws.isEmpty());
	}

	private ChainInfo getChainInfo(Transaction txn) throws NodeException {
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

	private Stream<byte[]> getChain(Transaction txn, long start, int count) throws NodeException {
		try {
			if (start < 0L || count <= 0)
				return Stream.empty();

			OptionalLong chainHeight = getHeightOfHead(txn);
			if (chainHeight.isEmpty())
				return Stream.empty();

			ByteIterable[] hashes = CheckSupplier.check(NodeException.class, () -> LongStream.range(start, Math.min(start + count, chainHeight.getAsLong() + 1))
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

	private void addToForwards(Transaction txn, NonGenesisBlock block, byte[] hashOfBlockToAdd) throws NodeException {
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
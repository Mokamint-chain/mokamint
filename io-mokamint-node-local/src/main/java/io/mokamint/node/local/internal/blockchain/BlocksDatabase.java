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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.UnmarshallingContexts;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.xodus.ByteIterable;
import io.hotmoka.xodus.ExodusException;
import io.hotmoka.xodus.env.Environment;
import io.hotmoka.xodus.env.Store;
import io.hotmoka.xodus.env.Transaction;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.TransactionAddresses;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.ClosureLock;
import io.mokamint.node.local.internal.LocalNodeImpl;

/**
 * The database where the blocks are persisted. Blocks are rooted at a genesis block
 * and do not necessarily form a list but are in general a tree.
 */
public class BlocksDatabase implements AutoCloseable {

	/**
	 * The node using this database of blocks.
	 */
	private final LocalNodeImpl node;

	/**
	 * The lock used to block new calls when the database has been requested to close.
	 */
	private final ClosureLock closureLock = new ClosureLock();

	/**
	 * The Xodus environment that holds the database of blocks.
	 */
	private final Environment environment;

	/**
	 * The Xodus store that holds the blocks, of all chains.
	 */
	private final Store storeOfBlocks;

	/**
	 * The Xodus store that maps each block to its immediate successor(s).
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

	private final static Logger LOGGER = Logger.getLogger(BlocksDatabase.class.getName());

	/**
	 * Opens the database of blocks.
	 * 
	 * @throws DatabaseException if the database is corrupted
	 */
	public BlocksDatabase(LocalNodeImpl node) throws DatabaseException {
		this.node = node;
		this.hashingForBlocks = node.getConfig().getHashingForBlocks();
		this.hasherForTransactions = node.getConfig().getHashingForTransactions().getHasher(io.mokamint.node.api.Transaction::toByteArray);
		this.environment = createBlockchainEnvironment();
		this.storeOfBlocks = openStore("blocks");
		this.storeOfForwards = openStore("forwards");
		this.storeOfChain = openStore("chain");
		this.storeOfTransactions = openStore("transactions");
	}

	@Override
	public void close() throws InterruptedException, DatabaseException {
		if (closureLock.stopNewCalls()) {
			try {
				environment.close(); // the lock guarantees that there are no unfinished transactions at this moment
				LOGGER.info("db: closed the blocks database");
			}
			catch (ExodusException e) {
				// TODO
				// not sure while this happens, it seems there might be transactions run for garbage collection,
				// that will consequently find a closed environment
				LOGGER.log(Level.WARNING, "db: failed to close the blocks database", e);
				throw new DatabaseException("Cannot close the blocks database", e);
			}
		}
	}

	/**
	 * Yields the hash of the genesis block in this database, if any.
	 * 
	 * @return the hash of the genesis block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<byte[]> getGenesisHash() throws DatabaseException, ClosedDatabaseException {
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getGenesisHash)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the genesis block in this database, if any.
	 * 
	 * @return the genesis block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<GenesisBlock> getGenesis() throws DatabaseException, ClosedDatabaseException {
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
			return check(DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(this::getGenesis))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the head of the best chain in this database, if any.
	 * 
	 * @return the head block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<Block> getHead() throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
			return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(this::getHead))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the hash of the head of the best chain in this database, if any.
	 * 
	 * @return the hash of the head block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<byte[]> getHeadHash() throws DatabaseException, ClosedDatabaseException {
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getHeadHash)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the height of the head of the best chain in this database, if any.
	 * 
	 * @return the height of the head block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public OptionalLong getHeightOfHead() throws DatabaseException, ClosedDatabaseException {
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getHeightOfHead)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the power of the head of the best chain in this database, if any.
	 * 
	 * @return the power of the head block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<BigInteger> getPowerOfHead() throws DatabaseException, ClosedDatabaseException {
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(this::getPowerOfHead)));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the block with the given hash, if it is contained in this database.
	 * 
	 * @param hash the hash
	 * @return the block, if any
	 * @throws NoSuchAlgorithmException if the block uses an unknown hashing or signature algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<Block> getBlock(byte[] hash) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
			return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(txn -> getBlock(txn, hash)))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the description of the block with the given hash, if it is contained in this database.
	 * 
	 * @param hash the hash
	 * @return the description of the block, if any
	 * @throws NoSuchAlgorithmException if the block uses an unknown hashing or signature algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<BlockDescription> getBlockDescription(byte[] hash) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
			return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(txn -> getBlockDescription(txn, hash)))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the transaction with the given hash, if it is contained in some block of the best chain of this database.
	 * 
	 * @param hash the hash of the transaction to search
	 * @return the transaction, if any
	 * @throws NoSuchAlgorithmException if the block containing the transaction refers to an unknown hashing or signature algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<io.mokamint.node.api.Transaction> getTransaction(byte[] hash) throws ClosedDatabaseException, DatabaseException, NoSuchAlgorithmException {
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
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
	 * of the best chain of this database.
	 * 
	 * @param hash the hash of the transaction to search
	 * @return the transaction address, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<TransactionAddress> getTransactionAddress(byte[] hash) throws ClosedDatabaseException, DatabaseException {
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
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
	 * chain from the block with hash {@code blockHash} towards the genesis block.
	 * 
	 * @param blockHash the hash of the block
	 * @param hash the hash of the transaction to search
	 * @return the transaction address, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database refers to an unknown hashing algorithm
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Optional<TransactionAddress> getTransactionAddress(byte[] blockHash, byte[] hash) throws ClosedDatabaseException, DatabaseException, NoSuchAlgorithmException {
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
			return check(DatabaseException.class, NoSuchAlgorithmException.class, () ->
				environment.computeInReadonlyTransaction(uncheck(txn -> getTransactionAddress(txn, blockHash, hash)))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
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
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(txn -> getForwards(txn, fromBytes(hash)))));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
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
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
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
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
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
		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(txn -> containsBlock(txn, hash))));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Adds the given block to this database.
	 * If the block was already in the database, nothing happens.
	 * 
	 * @param block the block to add
	 * @param updatedHead the new head of the best chain resulting from the addition,
	 *                    if it changed wrt the previous head; otherwise it is left unchanged
	 * @return true if the block has been actually added to the database, false otherwise.
	 *         There are a few situations when the result can be false. For instance,
	 *         if {@code block} was already in the database, or if {@code block} is
	 *         a genesis block but the genesis block is already set in the database;
	 *         or if {@code block} is a non-genesis block whose previous is not in the tree
	 * @throws DatabaseException if the block cannot be added, because the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean add(Block block, AtomicReference<Block> updatedHead) throws DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException {
		boolean hasBeenAdded;

		try (var scope = closureLock.scope(ClosedDatabaseException::new)) {
			hasBeenAdded = check(DatabaseException.class, NoSuchAlgorithmException.class, () -> environment.computeInTransaction(uncheck(txn -> add(txn, block, updatedHead))));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}

		if (hasBeenAdded)
			LOGGER.info("db: height " + block.getDescription().getHeight() + ": added block " + block.getHexHash(node.getConfig().getHashingForBlocks()));

		return hasBeenAdded;
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
	 */
	private boolean add(Transaction txn, Block block, AtomicReference<Block> updatedHead) throws NoSuchAlgorithmException, DatabaseException {
		byte[] hashOfBlock = block.getHash(hashingForBlocks);
	
		if (containsBlock(txn, hashOfBlock)) {
			LOGGER.warning("db: not adding block " + Hex.toHexString(hashOfBlock) + " since it is already in the database");
			return false;
		}
		else if (block instanceof NonGenesisBlock ngb) {
			if (containsBlock(txn, ngb.getHashOfPreviousBlock())) {
				byte[] bytesOfBlock = block.toByteArray();
				storeOfBlocks.put(txn, fromBytes(hashOfBlock), fromBytes(bytesOfBlock));
				putInStore(txn, hashOfBlock, bytesOfBlock);
				addToForwards(txn, ngb, hashOfBlock);
				if (updateHead(txn, ngb, hashOfBlock))
					updatedHead.set(ngb);
	
				return true;
			}
			else {
				LOGGER.warning("db: not adding block " + Hex.toHexString(hashOfBlock) + " since its previous block is not in the database");
				return false;
			}
		}
		else {
			if (getGenesisHash(txn).isEmpty()) {
				byte[] bytesOfBlock = block.toByteArray();
				storeOfBlocks.put(txn, fromBytes(hashOfBlock), fromBytes(bytesOfBlock));
				putInStore(txn, hashOfBlock, bytesOfBlock);
				setGenesisHash(txn, hashOfBlock);
				setHeadHash(txn, block, hashOfBlock);
				updatedHead.set(block);
				return true;
			}
			else {
				LOGGER.warning("not adding genesis block " + Hex.toHexString(hashOfBlock) + " since the database already contains a genesis block");
				return false;
			}
		}
	}

	private Environment createBlockchainEnvironment() {
		var env = new Environment(node.getConfig().getDir().resolve("blocks").toString());
		LOGGER.info("db: opened the blocks database");
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

		LOGGER.info("db: opened the store of " + name + " inside the blocks database");
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
			return Optional.ofNullable(storeOfBlocks.get(txn, head)).map(ByteIterable::getBytes);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the hash of the genesis block in this database, if any,
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
	 * Yields the hashes of the blocks that follow the block with the given hash, if any,
	 * running inside a transaction.
	 * 
	 * @param txn the transaction
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

			return Optional.of(block.get().getTransaction(ref.progressive));
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
	 * chain from the block with hash {@code blockHash} towards the genesis block.
	 * 
	 * @param txn the database transaction
	 * @param blockHash the hash of the block
	 * @param hash the hash of the transaction to search
	 * @return the transaction address, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database refers to an unknown hashing algorithm
	 */
	private Optional<TransactionAddress> getTransactionAddress(Transaction txn, byte[] blockHash, byte[] hash) throws DatabaseException, NoSuchAlgorithmException {
		try {
			byte[] cursor = blockHash;

			while (true) {
				Optional<Block> maybeBlock = getBlock(txn, cursor);
				if (maybeBlock.isEmpty())
					throw new DatabaseException("The database has no block with hash " + Hex.toHexString(cursor));

				Block block = maybeBlock.get();

				if (isContainedInTheBestChain(txn, block, cursor)) {
					// since the block is inside the best chain, we can use the storeOfTransactions for it
					ByteIterable txBI = storeOfTransactions.get(txn, fromBytes(hash));
					if (txBI == null)
						return Optional.empty();

					var ref = TransactionRef.from(txBI);
					if (ref.height > block.getDescription().getHeight())
						// the transaction is present above the block
						return Optional.empty();

					ByteIterable blockHash2 = storeOfChain.get(txn, ByteIterable.fromBytes(longToBytes(ref.height)));
					if (blockHash2 == null)
						throw new DatabaseException("The hash of the block of the best chain at height " + ref.height + " is not in the database");

					return Optional.of(TransactionAddresses.of(blockHash2.getBytes(), ref.progressive));
				}
				else {
					// we check if the transaction is inside the table of transactions of the block
					int length = block.getTransactionsCount();
					for (int pos = 0; pos < length; pos++)
						if (Arrays.equals(hash, hasherForTransactions.hash(block.getTransaction(pos))))
							return Optional.of(TransactionAddresses.of(cursor, pos));

					// otherwise we continue with the previous block
					if (block instanceof NonGenesisBlock ngb)
						cursor = ngb.getHashOfPreviousBlock();
					else
						throw new DatabaseException("The block with hash " + Hex.toHexString(blockHash) + " is not connected to the best chain");
				}
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
				throw new DatabaseException("the genesis hash is set but it is not in the database");
		}
		catch (NoSuchAlgorithmException e) {
			// getBlock throws NoSuchAlgorithmException only for non-genesis blocks
			throw new DatabaseException("the genesis hash is set but it refers to a non-genesis block in the database", e);
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

	private boolean updateHead(Transaction txn, NonGenesisBlock block, byte[] hashOfBlock) throws DatabaseException, NoSuchAlgorithmException {
		Optional<BigInteger> maybePowerOfHead = getPowerOfHead(txn);
		if (maybePowerOfHead.isEmpty())
			throw new DatabaseException("the database of blocks is non-empty but the power of the head is not set");

		// we choose the branch with more power
		if (block.getDescription().getPower().compareTo(maybePowerOfHead.get()) > 0) {
			setHeadHash(txn, block, hashOfBlock);
			return true;
		}
		else
			return false;
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
			storeOfBlocks.put(txn, genesis, fromBytes(newGenesisHash));
			LOGGER.info("db: height 0: block " + Hex.toHexString(newGenesisHash) + " set as genesis");
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
	private void setHeadHash(Transaction txn, Block newHead, byte[] newHeadHash) throws NoSuchAlgorithmException, DatabaseException {
		try {
			storeOfBlocks.put(txn, head, fromBytes(newHeadHash));
			storeOfBlocks.put(txn, power, fromBytes(newHead.getDescription().getPower().toByteArray()));
			long heightOfHead = newHead.getDescription().getHeight();
			storeOfBlocks.put(txn, height, fromBytes(longToBytes(heightOfHead)));
			updateChain(txn, newHead, newHeadHash);
			LOGGER.info("db: height " + heightOfHead + ": block " + Hex.toHexString(newHeadHash) + " set as head");
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
	 * @param block the block that gets set as new head
	 * @param blockHash the hash of {@code block}
	 * @throws NoSuchAlgorithmException if some block uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 */
	private void updateChain(Transaction txn, Block block, byte[] blockHash) throws NoSuchAlgorithmException, DatabaseException {
		try {
			long height = block.getDescription().getHeight();
			var heightBI = fromBytes(longToBytes(height));
			var _new = fromBytes(blockHash);
			var old = storeOfChain.get(txn, heightBI);

			do {
				storeOfChain.put(txn, heightBI, _new);

				if (old != null) {
					Optional<Block> oldBlock = getBlock(txn, old.getBytes());
					if (oldBlock.isEmpty())
						throw new DatabaseException("The current best chain misses the block at height " + height  + " with hash " + Hex.toHexString(old.getBytes()));

					removeReferencesToTransactionsInside(txn, oldBlock.get());
				}

				addReferencesToTransactionsInside(txn, block);

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
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private void addReferencesToTransactionsInside(Transaction txn, Block block) {
		long height = block.getDescription().getHeight();
		int count = block.getTransactionsCount();
		for (int pos = 0; pos < count; pos++) {
			var ref = new TransactionRef(height, pos);
			storeOfTransactions.put(txn, ByteIterable.fromBytes(hasherForTransactions.hash(block.getTransaction(pos))), ByteIterable.fromBytes(ref.toByteArray()));
		};
	}

	private void removeReferencesToTransactionsInside(Transaction txn, Block block) {
		int count = block.getTransactionsCount();
		for (int pos = 0; pos < count; pos++)
			storeOfTransactions.remove(txn, ByteIterable.fromBytes(hasherForTransactions.hash(block.getTransaction(pos))));
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

		return ChainInfos.of(chainHeight.getAsLong() + 1, maybeGenesisHash, maybeHeadHash);
	}

	private Stream<byte[]> getChain(Transaction txn, long start, int count) throws DatabaseException {
		try {
			if (start < 0L || count <= 0)
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
			context.writeLong(height);
			context.writeInt(progressive);
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
				return new TransactionRef(context.readLong(), context.readInt());
			}
		}
	}
}
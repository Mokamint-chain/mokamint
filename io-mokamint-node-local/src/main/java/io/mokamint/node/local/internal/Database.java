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

import static io.hotmoka.exceptions.Check.checkNoSuchAlgorithmException;
import static io.hotmoka.exceptions.Uncheck.uncheck;
import static io.hotmoka.xodus.ByteIterable.fromByte;
import static io.hotmoka.xodus.ByteIterable.fromBytes;

import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.exceptions.UncheckedIOException;
import io.hotmoka.xodus.ByteIterable;
import io.hotmoka.xodus.ExodusException;
import io.hotmoka.xodus.env.Environment;
import io.hotmoka.xodus.env.Store;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.local.Config;

/**
 * The database where the blockchain is persisted.
 */
public class Database implements AutoCloseable {

	/**
	 * The hashing algorithm used for hashing the blocks.
	 */
	private final HashingAlgorithm<byte[]> hashingForBlocks;

	/**
	 * The Xodus environment that holds the database.
	 */
	private final Environment environment;

	/**
	 * The Xodus store that holds the blocks of the chain.
	 */
	private final Store storeOfBlocks;

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the genesis block.
	 */
	private final static ByteIterable genesis = fromByte((byte) 42);
	
	/**
	 * The key mapped in the {@link #storeOfBlocks} to the head block.
	 */
	private final static ByteIterable head = fromByte((byte) 19);

	/**
	 * The Xodus store that maps each block to its immediate successor(s).
	 */
	private final Store storeOfForwards;

	private final static Logger LOGGER = Logger.getLogger(Database.class.getName());

	/**
	 * Creates the database.
	 * 
	 * @param config the configuration of the node using this database
	 */
	public Database(Config config) {
		this.hashingForBlocks = config.hashingForBlocks;
		this.environment = createBlockchainEnvironment(config);
		this.storeOfBlocks = openStoreOfBlocks();
		this.storeOfForwards = openStoreOfForwards();
	}

	/**
	 * Yields the block with the given hash, if it is contained in this database.
	 * 
	 * @param hash the hash
	 * @return the block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 */
	public Optional<Block> get(byte[] hash) throws NoSuchAlgorithmException {
		return checkNoSuchAlgorithmException(() ->
			Optional.ofNullable(environment.computeInReadonlyTransaction(txn -> storeOfBlocks.get(txn, fromBytes(hash))))
				.map(ByteIterable::getBytes)
				.map(uncheck(Blocks::from))
		);
	}

	/**
	 * Yields the hash of the first genesis block that has been added to this database, if any.
	 * 
	 * @return the hash of the genesis block, if any
	 */
	public Optional<byte[]> getGenesisHash() {
		return environment.computeInReadonlyTransaction(txn -> Optional.ofNullable(storeOfBlocks.get(txn, genesis)).map(ByteIterable::getBytes));
	}

	/**
	 * Yields the first genesis block that has been added to this database, if any.
	 * 
	 * @return the genesis block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the genesis block is unknown
	 */
	public Optional<GenesisBlock> getGenesis() throws NoSuchAlgorithmException {
		return checkNoSuchAlgorithmException(() ->
			getGenesisHash()
				.map(uncheck(hash -> get(hash).orElseThrow(uncheckedIOException("the genesis hash is set but it is not in the database"))))
				.map(block -> castToGenesis(block).orElseThrow(uncheckedIOException("the genesis hash is set but it refers to a non-genesis block in the database")))
		);
	}

	private static Optional<GenesisBlock> castToGenesis(Block block) {
		return block instanceof GenesisBlock ? Optional.of((GenesisBlock) block) : Optional.empty();
	}

	// TODO
	private static Supplier<UncheckedIOException> uncheckedIOException(String message) {
		return () -> new UncheckedIOException(message);
	}

	/**
	 * Yields the hash of the head block of the blockchain in the database, if it has been set already.
	 * 
	 * @return the hash of the head block, if any
	 */
	public Optional<byte[]> getHeadHash() {
		return environment.computeInReadonlyTransaction(txn -> Optional.ofNullable(storeOfBlocks.get(txn, head)).map(ByteIterable::getBytes));
	}

	/**
	 * Yields the head block of the blockchain in the database, if it has been set already.
	 * 
	 * @return the head block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 */
	public Optional<Block> getHead() throws NoSuchAlgorithmException {
		return checkNoSuchAlgorithmException(() ->
			getHeadHash()
				.map(uncheck(hash -> get(hash).orElseThrow(uncheckedIOException("the head hash is set but it is not in the database"))))
		);
	}

	/**
	 * Yields the hashes of the blocks that follow the block with the given hash, if any.
	 * 
	 * @param hash the hash of the parent block
	 * @return the hashes
	 */
	public Stream<byte[]> getForwards(byte[] hash) {
		var forwards = environment.computeInReadonlyTransaction(txn -> storeOfForwards.get(txn, fromBytes(hash)));
		
		if (forwards == null)
			return Stream.empty();
		else {
			int size = hashingForBlocks.length();
			byte[] hashes = forwards.getBytes();
			if (hashes.length % size != 0)
				throw new UncheckedIOException("the forward map has been corrupted");
			else if (hashes.length == size) // frequent case, worth optimizing
				return Stream.of(hashes);
			else
				return IntStream.rangeClosed(0, hashes.length / size).mapToObj(n -> slice(hashes, n, size));
		}
	}

	private static byte[] slice(byte[] all, int n, int size) {
		byte[] result = new byte[size];
		System.arraycopy(all, n * size, result, 0, size);
		return result;
	}

	/**
	 * Adds the given block to the database of blocks.
	 * If the block was already in the database, nothing happens.
	 * 
	 * @param block the block to add
	 * @return the hash of the block
	 */
	public byte[] add(Block block) {
		byte[] bytesOfBlock = block.toByteArray();
		byte[] hashOfBlock = hashingForBlocks.hash(bytesOfBlock);

		environment.executeInTransaction(txn -> {
			storeOfBlocks.put(txn, fromBytes(hashOfBlock), fromBytes(bytesOfBlock));
			if (block instanceof NonGenesisBlock) {
				var previousKey = fromBytes(((NonGenesisBlock) block).getHashOfPreviousBlock());
				var oldForwards = storeOfForwards.get(txn, previousKey);
				var newForwards = fromBytes(oldForwards != null ? concat(oldForwards.getBytes(), hashOfBlock) : hashOfBlock);
				storeOfForwards.put(txn, previousKey, newForwards);
			}
			else if (block instanceof GenesisBlock)
				if (storeOfBlocks.get(txn, genesis) == null) {
					storeOfBlocks.put(txn, genesis, fromBytes(hashOfBlock));
					LOGGER.info("setting block " + Hex.toHexString(hashOfBlock) + " as genesis");
				}
		});

		LOGGER.info("height " + block.getHeight() + ": added block " + Hex.toHexString(hashOfBlock));

		return hashOfBlock;
	}

	/**
	 * Sets the head reference of the chain to the block with the given hash.
	 * 
	 * @param hash the hash of the block that becomes the head reference of the blockchain
	 */
	public void setHeadHash(byte[] hash) {
		environment.executeInTransaction(txn -> storeOfBlocks.put(txn, head, fromBytes(hash)));
	}

	@Override
	public void close() {
		try {
			environment.close();
			LOGGER.info("closed the blockchain database");
		}
		catch (ExodusException e) {
			LOGGER.log(Level.WARNING, "failed to close the blockchain database", e);
		}
	}

	private static byte[] concat(byte[] array1, byte[] array2) {
		byte[] merge = new byte[array1.length + array2.length];
		System.arraycopy(array1, 0, merge, 0, array1.length);
		System.arraycopy(array2, 0, merge, array1.length, array2.length);
		return merge;
	}

	private Environment createBlockchainEnvironment(Config config) {
		var env = new Environment(config.dir + "/blockchain");
		LOGGER.info("opened the blockchain database");
		return env;
	}

	private Store openStoreOfBlocks() {
		var storeOfBlocks = new AtomicReference<Store>();
		environment.executeInTransaction(txn -> storeOfBlocks.set(environment.openStoreWithoutDuplicates("blocks", txn)));
		LOGGER.info("opened the store of blocks inside the blockchain database");
		return storeOfBlocks.get();
	}

	private Store openStoreOfForwards() {
		var storeOfForwards = new AtomicReference<Store>();
		environment.executeInTransaction(txn -> storeOfForwards.set(environment.openStoreWithoutDuplicates("forwards", txn)));
		LOGGER.info("opened the store of forwards inside the blockchain database");
		return storeOfForwards.get();
	}
}
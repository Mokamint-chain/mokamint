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

import static io.hotmoka.xodus.ByteIterable.fromBytes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.UnmarshallingContexts;
import io.hotmoka.xodus.ExodusException;
import io.hotmoka.xodus.env.Environment;
import io.hotmoka.xodus.env.Store;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.blockchain.Block;
import io.mokamint.node.local.internal.blockchain.NonGenesisBlock;

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
	 * The Xodus store that maps each block to its immediate successor(s).
	 */
	private final Store storeOfForwards;

	private final static Logger LOGGER = Logger.getLogger(Database.class.getName());

	/**
	 * Creates the database.
	 * 
	 * @param config the configuration of the node using this database
	 * @throws NoSuchAlgorithmException if the hashing algorithm for the blocks cannot be found
	 */
	public Database(Config config) throws NoSuchAlgorithmException {
		this.hashingForBlocks = HashingAlgorithms.mk(config.hashingForBlocks, (byte[] bytes) -> bytes);
		this.environment = createBlockchainEnvironment(config);
		this.storeOfBlocks = openStoreOfBlocks();
		this.storeOfForwards = openStoreOfForwards();
	}

	/**
	 * Yields the block with the given hash, if it is contained in this database.
	 * 
	 * @param hash the hash
	 * @return the block, if any
	 * @throws NoSuchAlgorithmException if the block uses an unknown hashing algorithm in its deadline
	 * @throws IOException if the database is corrupted
	 */
	public Optional<Block> get(byte[] hash) throws NoSuchAlgorithmException, IOException {
		var bytesOfBlock = environment.computeInReadonlyTransaction(txn -> storeOfBlocks.get(txn, fromBytes(hash)));
		if (bytesOfBlock == null)
			return Optional.empty();
		else
			try (var bais = new ByteArrayInputStream(bytesOfBlock.getBytes()); var context = UnmarshallingContexts.of(bais)) {
				return Optional.of(Block.from(context));
			}
	}

	/**
	 * Yields the hashes of the blocks that follow the block with the given hash, if any.
	 * 
	 * @param hash the hash of the parent block
	 * @return the hashes
	 * @throws IOException if the database is corrupted
	 */
	public Stream<byte[]> forwards(byte[] hash) throws IOException {
		var forwards = environment.computeInReadonlyTransaction(txn -> storeOfForwards.get(txn, fromBytes(hash)));
		
		if (forwards == null)
			return Stream.empty();
		else {
			int size = hashingForBlocks.length();
			byte[] hashes = forwards.getBytes();
			if (hashes.length % size != 0)
				throw new IOException("the forward map has been corrupted");
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
	 * 
	 * @param block the block to add
	 */
	public void add(Block block) {
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
		});

		LOGGER.info("height " + block.getHeight() + ": added block " + Hex.toHexString(hashOfBlock) + " to the database");
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
		var store = storeOfBlocks.get();
		LOGGER.info("opened the store of blocks inside the blockchain database");
		return store;
	}

	private Store openStoreOfForwards() {
		var storeOfForwards = new AtomicReference<Store>();
		environment.executeInTransaction(txn -> storeOfForwards.set(environment.openStoreWithoutDuplicates("forwards", txn)));
		var store = storeOfForwards.get();
		LOGGER.info("opened the store of forwards inside the blockchain database");
		return store;
	}
}
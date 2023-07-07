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
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.UnmarshallingContexts;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.xodus.ByteIterable;
import io.hotmoka.xodus.ExodusException;
import io.hotmoka.xodus.env.Environment;
import io.hotmoka.xodus.env.Store;
import io.hotmoka.xodus.env.Transaction;
import io.mokamint.node.Blocks;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.Peer;
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
	 * The maximal number of non-forced peers kept in the database.
	 */
	private final long maxPeers;

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

	/**
	 * The Xodus store that contains the set of peers of the node.
	 */
	private final Store storeOfPeers;

	/**
	 * The key mapped in the {@link #storeOfBlocks} to the genesis block.
	 */
	private final static ByteIterable genesis = fromByte((byte) 42);
	
	/**
	 * The key mapped in the {@link #storeOfBlocks} to the head block.
	 */
	private final static ByteIterable head = fromByte((byte) 19);

	/**
	 * The key mapped in the {@link #storeOfPeers} to the sequence of peers.
	 */
	private final static ByteIterable peers = fromByte((byte) 17);

	private final static Logger LOGGER = Logger.getLogger(Database.class.getName());

	/**
	 * Creates the database.
	 * 
	 * @param config the configuration of the node using this database
	 * @throws DatabaseException if the database cannot be opened, because it is corrupted
	 */
	public Database(Config config) throws DatabaseException {
		this.hashingForBlocks = config.getHashingForBlocks();
		this.maxPeers = config.maxPeers;
		this.environment = createBlockchainEnvironment(config);
		this.storeOfBlocks = openStore("blocks");
		this.storeOfForwards = openStore("forwards");
		this.storeOfPeers = openStore("peers");
	}

	/**
	 * Yields the block with the given hash, if it is contained in this database.
	 * 
	 * @param hash the hash
	 * @return the block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws DatabaseException if the database is corrupted
	 */
	public Optional<Block> get(byte[] hash) throws NoSuchAlgorithmException, DatabaseException {
		try {
			return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				Optional.ofNullable(environment.computeInReadonlyTransaction(txn -> storeOfBlocks.get(txn, fromBytes(hash))))
					.map(ByteIterable::getBytes)
					.map(uncheck(Database::blockFrom))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Unmarshals a block from the givne bytes.
	 * 
	 * @param bytes the bytes
	 * @return the resulting block
	 * @throws NoSuchAlgorithmException if the block uses an unknown hashing algorithm
	 * @throws DatabaseException if the block cannot be unmarshalled
	 */
	private static Block blockFrom(byte[] bytes) throws NoSuchAlgorithmException, DatabaseException {
		try {
			return Blocks.from(bytes);
		}
		catch (IOException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the hash of the first genesis block that has been added to this database, if any.
	 * 
	 * @return the hash of the genesis block, if any
	 * @throws DatabaseException if the database is corrupted
	 */
	public Optional<byte[]> getGenesisHash() throws DatabaseException {
		try {
			return environment.computeInReadonlyTransaction(txn -> Optional.ofNullable(storeOfBlocks.get(txn, genesis)).map(ByteIterable::getBytes));
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the first genesis block that has been added to this database, if any.
	 * 
	 * @return the genesis block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the genesis block is unknown
	 * @throws DatabaseException if the database is corrupted
	 */
	public Optional<GenesisBlock> getGenesis() throws NoSuchAlgorithmException, DatabaseException {
		Optional<byte[]> maybeGenesisHash = getGenesisHash();

		return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
			maybeGenesisHash
				.map(uncheck(hash -> get(hash).orElseThrow(() -> new DatabaseException("the genesis hash is set but it is not in the database"))))
				.map(uncheck(block -> castToGenesis(block).orElseThrow(() -> new DatabaseException("the genesis hash is set but it refers to a non-genesis block in the database"))))
		);
	}

	private static Optional<GenesisBlock> castToGenesis(Block block) {
		return block instanceof GenesisBlock ? Optional.of((GenesisBlock) block) : Optional.empty();
	}

	/**
	 * Yields the hash of the head block of the blockchain in the database, if it has been set already.
	 * 
	 * @return the hash of the head block, if any
	 * @throws DatabaseException if the database is corrupted
	 */
	public Optional<byte[]> getHeadHash() throws DatabaseException {
		try {
			return Optional.ofNullable(environment.computeInReadonlyTransaction(txn -> storeOfBlocks.get(txn, head))).map(ByteIterable::getBytes);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the head block of the blockchain in the database, if it has been set already.
	 * 
	 * @return the head block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws DatabaseException if the database is corrupted
	 */
	public Optional<Block> getHead() throws NoSuchAlgorithmException, DatabaseException {
		Optional<byte[]> maybeHeadHash = getHeadHash();

		return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
			maybeHeadHash
				.map(uncheck(hash -> get(hash).orElseThrow(() -> new DatabaseException("the head hash is set but it is not in the database"))))
		);
	}

	/**
	 * Yields the hashes of the blocks that follow the block with the given hash, if any.
	 * 
	 * @param hash the hash of the parent block
	 * @return the hashes
	 * @throws DatabaseException if the database is corrupted
	 */
	public Stream<byte[]> getForwards(byte[] hash) throws DatabaseException {
		ByteIterable forwards;

		try {
			forwards = environment.computeInReadonlyTransaction(txn -> storeOfForwards.get(txn, fromBytes(hash)));
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
				return IntStream.rangeClosed(0, hashes.length / size).mapToObj(n -> slice(hashes, n, size));
		}
	}

	private static byte[] slice(byte[] all, int n, int size) {
		var result = new byte[size];
		System.arraycopy(all, n * size, result, 0, size);
		return result;
	}

	/**
	 * A marshallable array of peers.
	 */
	private static class ArrayOfPeers extends AbstractMarshallable {
		private final Peer[] peers;
	
		private ArrayOfPeers(Stream<Peer> peers) {
			this.peers = peers.toArray(Peer[]::new);
		}

		private boolean contains(Peer peer) {
			for(var p: peers)
				if (p.equals(peer))
					return true;

			return false;
		}

		private Stream<Peer> stream() {
			return Stream.of(peers);
		}

		private int length() {
			return peers.length;
		}

		@Override
		public void into(MarshallingContext context) throws IOException {
			context.writeCompactInt(peers.length);
			for (var peer: peers)
				peer.into(context);
		}
	
		/**
		 * Unmarshals an array of peers from the given byte iterable.
		 * 
		 * @param bi the byte iterable
		 * @return the array of peers
		 * @throws IOException if the peers cannot be unmarshalled
		 * @throws URISyntaxException if the context contains a peer whose URI has illegal syntax
		 */
		private static ArrayOfPeers from(ByteIterable bi) throws IOException, URISyntaxException {
			try (var bais = new ByteArrayInputStream(bi.getBytes()); var context = UnmarshallingContexts.of(bais)) {
				int length = context.readCompactInt();
				var peers = new Peer[length];
				for (int pos = 0; pos < length; pos++)
					peers[pos] = Peers.from(context);
		
				return new ArrayOfPeers(Stream.of(peers));
			}
		}
	}

	/**
	 * Yields the set of peers saved in this database, if any.
	 * 
	 * @return the peers
	 * @throws DatabaseException of the database is corrupted
	 */
	public Stream<Peer> getPeers() throws DatabaseException {
		try {
			var bi = environment.computeInReadonlyTransaction(txn -> storeOfPeers.get(txn, peers));
			return bi == null ? Stream.empty() : ArrayOfPeers.from(bi).stream();
		}
		catch (IOException | URISyntaxException | ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Adds the given peer to the set of peers in this database.
	 * 
	 * @param peer the peer to add
	 * @param force true if the peer must be added, regardless of the total amount
	 *              of peers already added; false otherwise, which means that no more
	 *              than {@link #maxPeers} peers are allowed
	 * @return true if the peer has been added; false otherwise, which means
	 *         that the database already contains the same peer or that the peer was not forced
	 *         and there are already {@link maxPeers} peers
	 * @throws DatabaseException if the database is corrupted
	 */
	public boolean addPeer(Peer peer, boolean force) throws DatabaseException {
		try {
			return check(URISyntaxException.class, IOException.class,
					() -> environment.computeInTransaction(uncheck(txn -> addPeer(txn, peer, force))));
		}
		catch (IOException | URISyntaxException | ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private boolean addPeer(Transaction txn, Peer peer, boolean force) throws IOException, URISyntaxException {
		var bi = storeOfPeers.get(txn, peers);
		if (bi == null) {
			if (force || maxPeers >= 1) {
				storeOfPeers.put(txn, peers, fromBytes(new ArrayOfPeers(Stream.of(peer)).toByteArray()));
				return true;
			}
			else
				return false;
		}
		else {
			var aop = ArrayOfPeers.from(bi);
			if (aop.contains(peer) || (!force && aop.length() >= maxPeers))
				return false;
			else {
				var concat = Stream.concat(aop.stream(), Stream.of(peer));
				storeOfPeers.put(txn, peers, fromBytes(new ArrayOfPeers(concat).toByteArray()));
				return true;
			}
		}
	}

	/**
	 * Removes the given peer from those stored in this database.
	 * 
	 * @param peer the peer to remove
	 * @return true if the peer has been actually removed; false otherwise, which means
	 *         that the peer was not in the database
	 * @throws DatabaseException if the database is corrupted
	 */
	public boolean removePeer(Peer peer) throws DatabaseException {
		try {
			return check(URISyntaxException.class, IOException.class,
					() -> environment.computeInTransaction(uncheck(txn -> removePeer(txn, peer))));
		}
		catch (IOException | URISyntaxException | ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private boolean removePeer(Transaction txn, Peer peer) throws IOException, URISyntaxException {
		var bi = storeOfPeers.get(txn, peers);
		if (bi == null)
			return false;
		else {
			var aop = ArrayOfPeers.from(bi);
			if (aop.contains(peer)) {
				Stream<Peer> result = aop.stream().filter(p -> !peer.equals(p));
				storeOfPeers.put(txn, peers, fromBytes(new ArrayOfPeers(result).toByteArray()));
				return true;
			}
			else
				return false;
		}
	}

	/**
	 * Adds the given block to the database of blocks.
	 * If the block was already in the database, nothing happens.
	 * 
	 * @param block the block to add
	 * @return the hash of the block
	 * @throws DatabaseException if the block cannot be added, because the database is corrupted
	 */
	public byte[] add(Block block) throws DatabaseException {
		var bytesOfBlock = block.toByteArray();
		byte[] hashOfBlock = hashingForBlocks.hash(bytesOfBlock);

		try {
			environment.executeInTransaction(txn -> {
				storeOfBlocks.put(txn, fromBytes(hashOfBlock), fromBytes(bytesOfBlock));
				if (block instanceof NonGenesisBlock) {
					var previousKey = fromBytes(((NonGenesisBlock) block).getHashOfPreviousBlock());
					var oldForwards = storeOfForwards.get(txn, previousKey);
					var newForwards = fromBytes(oldForwards != null ? concat(oldForwards.getBytes(), hashOfBlock) : hashOfBlock);
					storeOfForwards.put(txn, previousKey, newForwards);
				}
				else if (storeOfBlocks.get(txn, genesis) == null) {
					storeOfBlocks.put(txn, genesis, fromBytes(hashOfBlock));
					LOGGER.info("setting block " + Hex.toHexString(hashOfBlock) + " as genesis");
				}
			});
		}
		catch (ExodusException e) {
			throw new DatabaseException("cannot write a block in the database", e);
		}

		LOGGER.info("height " + block.getHeight() + ": added block " + Hex.toHexString(hashOfBlock));

		return hashOfBlock;
	}

	/**
	 * Sets the head reference of the chain to the block with the given hash.
	 * 
	 * @param hash the hash of the block that becomes the head reference of the blockchain
	 * @throws DatabaseException if the head hash cannot be written, because the database is corrupted
	 */
	public void setHeadHash(byte[] hash) throws DatabaseException {
		try {
			environment.executeInTransaction(txn -> storeOfBlocks.put(txn, head, fromBytes(hash)));
		}
		catch (ExodusException e) {
			throw new DatabaseException("cannot write the head hash in the database", e);
		}
	}

	@Override
	public void close() throws DatabaseException {
		try {
			environment.close();
			LOGGER.info("closed the blockchain database");
		}
		catch (ExodusException e) {
			LOGGER.log(Level.WARNING, "failed to close the blockchain database", e);
			throw new DatabaseException("cannot close the database", e);
		}
	}

	private static byte[] concat(byte[] array1, byte[] array2) {
		var merge = new byte[array1.length + array2.length];
		System.arraycopy(array1, 0, merge, 0, array1.length);
		System.arraycopy(array2, 0, merge, array1.length, array2.length);
		return merge;
	}

	private Environment createBlockchainEnvironment(Config config) {
		var env = new Environment(config.dir + "/blockchain");
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
}
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.exceptions.CheckRunnable;
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

	/**
	 * The key mapped in the {@link #storeOfPeers} to the unique identifier of the node
	 * having this database.
	 */
	private final static ByteIterable uuid = fromByte((byte) 23);

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
		ensureNodeUUID();
	}

	/**
	 * A marshallable UUID.
	 */
	private static class MarshallableUUID extends AbstractMarshallable {
		private final UUID uuid;
	
		private MarshallableUUID(UUID uuid) {
			this.uuid = uuid;
		}

		@Override
		public void into(MarshallingContext context) throws IOException {
			context.writeLong(uuid.getMostSignificantBits());
			context.writeLong(uuid.getLeastSignificantBits());
		}
	
		/**
		 * Unmarshals an UUID from the given byte iterable.
		 * 
		 * @param bi the byte iterable
		 * @return the UUID
		 * @throws IOException if the UUID cannot be unmarshalled
		 */
		private static MarshallableUUID from(ByteIterable bi) throws IOException {
			try (var bais = new ByteArrayInputStream(bi.getBytes()); var context = UnmarshallingContexts.of(bais)) {
				return new MarshallableUUID(new UUID(context.readLong(), context.readLong()));
			}
		}
	}

	private void ensureNodeUUID() throws DatabaseException {
		try {
			CheckRunnable.check(IOException.class, () -> {
				environment.executeInTransaction(txn -> {
					var bi = storeOfPeers.get(txn, uuid);
					if (bi == null) {
						var nodeUUID = UUID.randomUUID();
						storeOfPeers.put(txn, uuid, fromBytes(new MarshallableUUID(nodeUUID).toByteArray()));
						LOGGER.info("created a new UUID for the node: " + nodeUUID);
					}
					else {
						var nodeUUID = uncheck(MarshallableUUID::from).apply(bi).uuid;
						LOGGER.info("the UUID of the node is " + nodeUUID);
					}
				});
			});
		}
		catch (ExodusException | IOException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the UUID of the node having this database.
	 * 
	 * @return the UUID
	 * @throws DatabaseException if the database is corrupted
	 */
	public UUID getUUID() throws DatabaseException {
		try {
			var bi = environment.computeInReadonlyTransaction(txn -> storeOfPeers.get(txn, uuid));
			if (bi == null)
				throw new DatabaseException("The UUID of the node is not in the database");

			return MarshallableUUID.from(bi).uuid;
		}
		catch (ExodusException | IOException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the block with the given hash, if it is contained in this database.
	 * 
	 * @param hash the hash
	 * @return the block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws DatabaseException if the database is corrupted
	 */
	public Optional<Block> getBlock(byte[] hash) throws NoSuchAlgorithmException, DatabaseException {
		try {
			return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
				Optional.ofNullable(environment.computeInReadonlyTransaction(txn -> storeOfBlocks.get(txn, fromBytes(hash))))
					.map(uncheck(Database::blockFrom))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Unmarshals a block from the given bytes.
	 * 
	 * @param bi the bytes
	 * @return the resulting block
	 * @throws NoSuchAlgorithmException if the block uses an unknown hashing algorithm
	 * @throws DatabaseException if the block cannot be unmarshalled
	 */
	private static Block blockFrom(ByteIterable bi) throws NoSuchAlgorithmException, DatabaseException {
		try {
			return Blocks.from(bi.getBytes());
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
				.map(uncheck(hash -> getBlock(hash).orElseThrow(() -> new DatabaseException("the genesis hash is set but it is not in the database"))))
				.map(uncheck(block -> castToGenesis(block).orElseThrow(() -> new DatabaseException("the genesis hash is set but it refers to a non-genesis block in the database"))))
		);
	}

	private static Optional<GenesisBlock> castToGenesis(Block block) {
		return block instanceof GenesisBlock gb ? Optional.of(gb) : Optional.empty();
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
				.map(uncheck(hash -> getBlock(hash).orElseThrow(() -> new DatabaseException("the head hash is set but it is not in the database"))))
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
		return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(txn -> getForwards(txn, fromBytes(hash)))));
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
	 * A marshallable array of peers.
	 */
	private static class ArrayOfPeers extends AbstractMarshallable {
		private final Peer[] peers;
	
		private ArrayOfPeers(Stream<Peer> peers) {
			this.peers = peers.distinct().sorted().toArray(Peer[]::new);
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
	 *         that the peer was already present or that it was not forced
	 *         and there are already {@link maxPeers} peers
	 * @throws DatabaseException if the database is corrupted
	 */
	public boolean add(Peer peer, boolean force) throws DatabaseException {
		try {
			return check(URISyntaxException.class, IOException.class,
					() -> environment.computeInTransaction(uncheck(txn -> add(txn, peer, force))));
		}
		catch (IOException | URISyntaxException | ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private boolean add(Transaction txn, Peer peer, boolean force) throws IOException, URISyntaxException {
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
	public boolean remove(Peer peer) throws DatabaseException {
		try {
			return check(URISyntaxException.class, IOException.class,
					() -> environment.computeInTransaction(uncheck(txn -> remove(txn, peer))));
		}
		catch (IOException | URISyntaxException | ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private boolean remove(Transaction txn, Peer peer) throws IOException, URISyntaxException {
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
	 * @return true if the block has been actually added to the database, false otherwise.
	 *         There are a few situations when the result can be false. For instance,
	 *         if {@code block} was already in the database, or if {@code block} is
	 *         a genesis block but the genesis block is already set in the database, or
	 *         if {@code block} ...
	 * @throws DatabaseException if the block cannot be added, because the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 */
	public boolean add(Block block) throws DatabaseException, NoSuchAlgorithmException {
		try {
			return check(DatabaseException.class, NoSuchAlgorithmException.class, () -> environment.computeInTransaction(uncheck(txn -> add(txn, block))));
		}
		catch (ExodusException e) {
			throw new DatabaseException("cannot write block " + Hex.toHexString(block.getHash(hashingForBlocks)) + " in the database", e);
		}
	}

	private boolean add(Transaction txn, Block block) throws NoSuchAlgorithmException, DatabaseException {
		byte[] hashOfBlock = block.getHash(hashingForBlocks);
		String hex = Hex.toHexString(hashOfBlock);
		ByteIterable key = fromBytes(hashOfBlock);

		if (storeOfBlocks.get(txn, key) != null)
			return false;
		else if (block instanceof NonGenesisBlock ngb) {
			storeOfBlocks.put(txn, key, fromBytes(block.toByteArray()));
			var previous = fromBytes(ngb.getHashOfPreviousBlock());
			var oldForwards = storeOfForwards.get(txn, previous);
			var newForwards = fromBytes(oldForwards != null ? concat(oldForwards.getBytes(), hashOfBlock) : hashOfBlock);
			storeOfForwards.put(txn, previous, newForwards);
			updateHead(txn, previous);
			LOGGER.info("height " + block.getHeight() + ": added block " + hex);
			return true;
		}
		else if (storeOfBlocks.get(txn, genesis) == null) {
			storeOfBlocks.put(txn, key, fromBytes(block.toByteArray()));
			storeOfBlocks.put(txn, genesis, key);
			LOGGER.info("set block " + hex + " as genesis");
			storeOfBlocks.put(txn, head, key);
			LOGGER.info("set block " + hex + " as head");
			updateHead(txn, key);
			LOGGER.info("height " + block.getHeight() + ": added block " + hex);
			return true;
		}
		else {
			LOGGER.warning("discarding genesis block " + hex + " since the database already contains a genesis block");
			return false;
		}
	}

	/**
	 * Checks if a block is part of the current chain; in that case, it updates the
	 * head with the highest reachable block through the forwards map;
	 * for the same height, the block with the smallest total cumulative time is preferred as the new head.
	 * 
	 * @param txn the transaction that can be used to access the database
	 * @param hash the hash of the block
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if the database contains a block using an unknown hashing algorithm
	 */
	private void updateHead(Transaction txn, ByteIterable hash) throws DatabaseException, NoSuchAlgorithmException {
		var currentHead = storeOfBlocks.get(txn, head);
		if (currentHead == null)
			return;

		ByteIterable bi = storeOfBlocks.get(txn, hash);
		if (bi == null)
			return;

		boolean reachedCurrentHead = false;
		ByteIterable highest = hash;
		Block highestBlock = blockFrom(bi);

		var ws = new ArrayList<ByteIterable>();
		ws.add(hash);
		var seen = new HashSet<ByteIterable>();

		do {
			int size = ws.size();
			ByteIterable cursor = ws.remove(size - 1);
			reachedCurrentHead |= cursor.equals(currentHead);

			int seenSize = seen.size();
			getForwards(txn, cursor)
				.map(ByteIterable::fromBytes)
				.filter(seen::add)
				.forEach(ws::add);

			// if we have seen something already, the database of blocks is not a tree rooted at the genesis
			if (seen.size() - seenSize < ws.size() - size + 1)
				throw new DatabaseException("the database of blocks does not form a tree");

			if (ws.size() == size - 1) {
				// we have reached a block without forwards: we check if it is better than highest
				ByteIterable cursorBlockBytes = storeOfBlocks.get(txn, cursor);
				if (cursorBlockBytes == null)
					throw new DatabaseException("block " + Hex.toHexString(cursor.getBytes()) + " is in the forward map codomain but not in the database");

				Block cursorBlock = blockFrom(cursorBlockBytes);
				long cbh = cursorBlock.getHeight(), hbh = highestBlock.getHeight();

				// we prefer longest chains; if two chains have the same length, we prefer that with the smaller total time
				if (cbh > hbh || (cbh == hbh && cursorBlock.getTotalWaitingTime() < highestBlock.getTotalWaitingTime())) {
					highest = cursor;
					highestBlock = cursorBlock;
				}
			}
		}
		while (!ws.isEmpty());

		if (reachedCurrentHead && !highest.equals(currentHead)) {
			storeOfBlocks.put(txn, head, highest);
			LOGGER.info("set block " + Hex.toHexString(highest.getBytes()) + " as head");
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
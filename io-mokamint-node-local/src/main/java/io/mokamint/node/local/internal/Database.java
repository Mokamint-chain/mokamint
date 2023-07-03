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
import io.hotmoka.exceptions.UncheckedIOException;
import io.hotmoka.exceptions.UncheckedNoSuchAlgorithmException;
import io.hotmoka.exceptions.UncheckedURISyntaxException;
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
	 * The maximal number of candidate peers kept in the database.
	 */
	private final long maxCandidatePeers;

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
	 * The key mapped in the {@link #storeOfPeers} to the sequence of candidate peers.
	 */
	private final static ByteIterable candidatePeers = fromByte((byte) 23);

	private final static Logger LOGGER = Logger.getLogger(Database.class.getName());

	/**
	 * Creates the database.
	 * 
	 * @param config the configuration of the node using this database
	 */
	public Database(Config config) {
		this.hashingForBlocks = config.getHashingForBlocks();
		this.maxPeers = config.maxPeers;
		this.maxCandidatePeers = config.maxCandidatePeers;
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
			return getInternal(hash);
		}
		catch (IOException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the block with the given hash, if it is contained in this database.
	 * 
	 * @param hash the hash
	 * @return the block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws IOException if the database is corrupted
	 */
	private Optional<Block> getInternal(byte[] hash) throws NoSuchAlgorithmException, IOException {
		return check(UncheckedNoSuchAlgorithmException.class, UncheckedIOException.class, () ->
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
	 * @throws DatabaseException if the database is corrupted
	 */
	public Optional<GenesisBlock> getGenesis() throws NoSuchAlgorithmException, DatabaseException {
		try {
			return check(UncheckedNoSuchAlgorithmException.class, UncheckedIOException.class, () ->
				getGenesisHash()
					.map(uncheck(hash -> getInternal(hash).orElseThrow(() -> new IOException("the genesis hash is set but it is not in the database"))))
					.map(uncheck(block -> castToGenesis(block).orElseThrow(() -> new IOException("the genesis hash is set but it refers to a non-genesis block in the database"))))
			);
		}
		catch (IOException e) {
			throw new DatabaseException(e);
		}
	}

	private static Optional<GenesisBlock> castToGenesis(Block block) {
		return block instanceof GenesisBlock ? Optional.of((GenesisBlock) block) : Optional.empty();
	}

	/**
	 * Yields the hash of the head block of the blockchain in the database, if it has been set already.
	 * 
	 * @return the hash of the head block, if any
	 */
	public Optional<byte[]> getHeadHash() {
		return Optional.ofNullable(environment.computeInReadonlyTransaction(txn -> storeOfBlocks.get(txn, head))).map(ByteIterable::getBytes);
	}

	/**
	 * Yields the head block of the blockchain in the database, if it has been set already.
	 * 
	 * @return the head block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws DatabaseException if the database is corrupted
	 */
	public Optional<Block> getHead() throws NoSuchAlgorithmException, DatabaseException {
		try {
			return check(UncheckedNoSuchAlgorithmException.class, UncheckedIOException.class, () ->
				getHeadHash()
					.map(uncheck(hash -> getInternal(hash).orElseThrow(() -> new IOException("the head hash is set but it is not in the database"))))
			);
		}
		catch (IOException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the hashes of the blocks that follow the block with the given hash, if any.
	 * 
	 * @param hash the hash of the parent block
	 * @return the hashes
	 * @throws DatabaseException if the database is corrupted
	 */
	public Stream<byte[]> getForwards(byte[] hash) throws DatabaseException {
		var forwards = environment.computeInReadonlyTransaction(txn -> storeOfForwards.get(txn, fromBytes(hash)));
		
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
		var bi = environment.computeInReadonlyTransaction(txn -> storeOfPeers.get(txn, peers));
		try {
			return bi == null ? Stream.empty() : ArrayOfPeers.from(bi).stream();
		}
		catch (IOException | URISyntaxException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the set of candidate peers saved in this database, if any.
	 * 
	 * @return the candidate peers
	 * @throws IOException of the database is corrupted
	 * @throws URISyntaxException if the database contains a URI whose syntax is illegal
	 */
	public Stream<Peer> getCandidatePeers() throws URISyntaxException, IOException {
		var bi = environment.computeInReadonlyTransaction(txn -> storeOfPeers.get(txn, candidatePeers));
		return bi == null ? Stream.empty() : ArrayOfPeers.from(bi).stream();
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
			return check(UncheckedURISyntaxException.class, UncheckedIOException.class,
					() -> environment.computeInTransaction(uncheck(txn -> addPeer(txn, peer, force))));
		}
		catch (IOException | URISyntaxException e) {
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
	 * Adds the given peer to the set of candidate peers in this database.
	 * If the maximal number of candidate peers is reached, the oldest peers
	 * are removed in a FIFO way.
	 * 
	 * @param peer the candidate peer to add
	 * @return true if the candidate peer has been added; false otherwise, which means
	 *         that the database already contains the same peer or candidate peer
	 * @throws IOException if there is an I/O error in the access to the database
	 * @throws URISyntaxException if the database contains a peer with an illegal URI
	 */
	public boolean addCandidatePeer(Peer peer) throws IOException, URISyntaxException {
		return check(UncheckedURISyntaxException.class, UncheckedIOException.class,
			() -> environment.computeInTransaction(uncheck(txn -> addCandidatePeer(txn, peer))));
	}

	private boolean addCandidatePeer(Transaction txn, Peer peer) throws IOException, URISyntaxException {
		if (maxCandidatePeers <= 0)
			return false;

		var bi = storeOfPeers.get(txn, candidatePeers);
		if (bi == null) {
			storeOfPeers.put(txn, candidatePeers, fromBytes(new ArrayOfPeers(Stream.of(peer)).toByteArray()));
			return true;
		}
		else {
			var aop = ArrayOfPeers.from(bi);
			if (aop.contains(peer))
				return false;
			else {
				var bi2 = storeOfPeers.get(txn, peers);
				if (bi2 != null && ArrayOfPeers.from(bi2).contains(peer))
					return false;

				// we put the new peer at the end and remove the oldest peers, at the beginning of the stream
				var concat = Stream.concat(aop.stream(), Stream.of(peer)).skip(Math.max(0L, aop.length() - maxCandidatePeers + 1L));
				storeOfPeers.put(txn, candidatePeers, fromBytes(new ArrayOfPeers(concat).toByteArray()));
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
			return check(UncheckedURISyntaxException.class, UncheckedIOException.class,
					() -> environment.computeInTransaction(uncheck(txn -> removePeer(txn, peer, peers))));
		}
		catch (IOException | URISyntaxException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Removes the given candidate peer from those stored in this database.
	 * 
	 * @param peer the candidate peer to remove
	 * @return true if the candidate peer has been actually removed; false otherwise, which means
	 *         that the candidate peer was not in the database
	 * @throws IOException if there is an I/O error in the access to the database
	 * @throws URISyntaxException if the database contains a peer with an illegal URI
	 */
	public boolean removeCandidatePeer(Peer peer) throws IOException, URISyntaxException {
		return check(UncheckedURISyntaxException.class, UncheckedIOException.class,
			() -> environment.computeInTransaction(uncheck(txn -> removePeer(txn, peer, candidatePeers))));
	}

	private boolean removePeer(Transaction txn, Peer peer, ByteIterable key) throws IOException, URISyntaxException {
		var bi = storeOfPeers.get(txn, key);
		if (bi == null)
			return false;
		else {
			var aop = ArrayOfPeers.from(bi);
			if (aop.contains(peer)) {
				Stream<Peer> result = aop.stream().filter(p -> !peer.equals(p));
				storeOfPeers.put(txn, key, fromBytes(new ArrayOfPeers(result).toByteArray()));
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
	 */
	public byte[] add(Block block) {
		// TODO: this does not throw IOException because no exception is thrown by Xodus. Is it OK?
		var bytesOfBlock = block.toByteArray();
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

	private Store openStore(String name) {
		var store = new AtomicReference<Store>();
		environment.executeInTransaction(txn -> store.set(environment.openStoreWithoutDuplicates(name, txn)));
		LOGGER.info("opened the store of " + name + " inside the blockchain database");
		return store.get();
	}
}
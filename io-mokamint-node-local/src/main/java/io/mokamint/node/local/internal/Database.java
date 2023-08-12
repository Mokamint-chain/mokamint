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
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import io.hotmoka.crypto.Hex;
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
import io.mokamint.node.ChainInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.Peer;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.LocalNodeImpl.Event;

/**
 * The database where the blockchain is persisted.
 */
public class Database implements AutoCloseable {

	/**
	 * The node having this database.
	 */
	private final LocalNodeImpl node;

	/**
	 * The configuration of the node having this database.
	 */
	private final Config config;

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
	private final Store storeOfForwards; // TODO: maybe useless?

	/**
	 * The Xodus store that holds the list of hashes of the blocks in the current best chain.
	 * It maps 0 to the genesis block, 1 to the block at height 1 in the current best chain and so on.
	 */
	private final Store storeOfChain;

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
	 * The key mapped in the {@link #storeOfChain} to the height of the current best chain.
	 */
	private final static ByteIterable height = fromByte((byte) 29);

	/**
	 * The key mapped in the {@link #storeOfPeers} to the sequence of peers.
	 */
	private final static ByteIterable peers = fromByte((byte) 17);

	/**
	 * The key mapped in the {@link #storeOfPeers} to the unique identifier of the node
	 * having this database.
	 */
	private final static ByteIterable uuid = fromByte((byte) 23);

	/**
	 * The lock used to block new calls when the database has been requested to close.
	 */
	private final ClosureLock closureLock = new ClosureLock();

	private final static Logger LOGGER = Logger.getLogger(Database.class.getName());

	/**
	 * Creates the database of a node.
	 * 
	 * @param node the node
	 * @throws DatabaseException if the database cannot be opened, because it is corrupted
	 */
	public Database(LocalNodeImpl node) throws DatabaseException {
		this.node = node;
		this.config = node.getConfig();
		this.maxPeers = config.maxPeers;
		this.environment = createBlockchainEnvironment(config);
		this.storeOfBlocks = openStore("blocks");
		this.storeOfForwards = openStore("forwards");
		this.storeOfChain = openStore("chain");
		this.storeOfPeers = openStore("peers");
		ensureNodeUUID();
	}

	/**
	 * Creates the database of a node, allowing to require initialization with a new genesis block.
	 * 
	 * @param node the node
	 * @param init if the database must be initialized with a genesis block (if empty)
	 * @throws DatabaseException if the database cannot be opened, because it is corrupted
	 * @throws AlreadyInitializedException if {@code init} is true but the database already contains a genesis block
	 */
	public Database(LocalNodeImpl node, boolean init) throws DatabaseException, AlreadyInitializedException {
		this(node);

		if (init)
			initialize();
	}

	@Override
	public void close() throws DatabaseException, InterruptedException {
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
	 * Yields the UUID of the node having this database.
	 * 
	 * @return the UUID
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public UUID getUUID() throws DatabaseException, ClosedDatabaseException {
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			var bi = environment.computeInReadonlyTransaction(txn -> storeOfPeers.get(txn, uuid));
			if (bi == null)
				throw new DatabaseException("The UUID of the node is not in the database");

			return MarshallableUUID.from(bi).uuid;
		}
		catch (ExodusException | IOException e) {
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
			return check(DatabaseException.class, () -> environment.computeInReadonlyTransaction(uncheck(txn -> getChainInfo(txn))));
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
	 * Yields the set of peers saved in this database, if any.
	 * 
	 * @return the peers
	 * @throws DatabaseException of the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Stream<Peer> getPeers() throws DatabaseException, ClosedDatabaseException {
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			var bi = environment.computeInReadonlyTransaction(txn -> storeOfPeers.get(txn, peers));
			return bi == null ? Stream.empty() : ArrayOfPeers.from(bi).stream();
		}
		catch (IOException | URISyntaxException | ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
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
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean add(Peer peer, boolean force) throws DatabaseException, ClosedDatabaseException {
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return check(URISyntaxException.class, IOException.class,
					() -> environment.computeInTransaction(uncheck(txn -> add(txn, peer, force))));
		}
		catch (IOException | URISyntaxException | ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	/**
	 * Removes the given peer from those stored in this database.
	 * 
	 * @param peer the peer to remove
	 * @return true if the peer has been actually removed; false otherwise, which means
	 *         that the peer was not in the database
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean remove(Peer peer) throws DatabaseException, ClosedDatabaseException {
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return check(URISyntaxException.class, IOException.class,
					() -> environment.computeInTransaction(uncheck(txn -> remove(txn, peer))));
		}
		catch (IOException | URISyntaxException | ExodusException e) {
			throw new DatabaseException(e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	/**
	 * Determines if this database contains a block with the given hash.
	 * This is more efficient than checking the outcome of {@link #getBlock(byte[])},
	 * since it does not construct the block.
	 * 
	 * @param hash the hash of the block
	 * @return true if and only if that condition holds
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean containsBlock(byte[] hash) throws DatabaseException, ClosedDatabaseException {
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return environment.computeInReadonlyTransaction(txn -> isInStore(txn, hash));
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
	public boolean add(Block block, AtomicReference<Block> updatedHead) throws DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException {
		closureLock.beforeCall(ClosedDatabaseException::new);

		try {
			return check(DatabaseException.class, NoSuchAlgorithmException.class, () -> environment.computeInTransaction(uncheck(txn -> add(txn, block, updatedHead))));
		}
		catch (ExodusException e) {
			throw new DatabaseException("cannot write block " + block.getHexHash(config.getHashingForBlocks()) + " in the database", e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	/**
	 * An event triggered when a block gets added to the database, not necessarily
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

	private void initialize() throws DatabaseException, AlreadyInitializedException {
		try {
			if (getGenesisHash().isPresent())
				throw new AlreadyInitializedException("init cannot be required for an already initialized node");

			var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.initialAcceleration));

			add(genesis, new AtomicReference<>());
		}
		catch (NoSuchAlgorithmException | ClosedDatabaseException e) {
			// the database cannot be closed at this moment
			// moreover, if the database is empty, there is no way it can contain a non-genesis block (that contains a hashing algorithm)
			LOGGER.log(Level.SEVERE, "unexpected exception", e);
			throw new RuntimeException("unexpected exception", e);
		}
	}

	/**
	 * Yields the head block of the blockchain in the database, if it has been set already,
	 * running inside the given transaction.
	 * 
	 * @param txn the transaction
	 * @return the head block, if any
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws DatabaseException if the database is corrupted
	 */
	private Optional<Block> getHead(Transaction txn) throws NoSuchAlgorithmException, DatabaseException {
		Optional<byte[]> maybeHeadHash = getHeadHash(txn);
	
		return check(NoSuchAlgorithmException.class, DatabaseException.class, () ->
			maybeHeadHash
				.map(uncheck(hash -> getBlock(txn, hash).orElseThrow(() -> new DatabaseException("the head hash is set but it is not in the database"))))
		);
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
			int size = config.getHashingForBlocks().length();
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

		private ByteIterable toByteIterable() {
			return fromBytes(toByteArray());
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

	private boolean add(Transaction txn, Peer peer, boolean force) throws IOException, URISyntaxException {
		var bi = storeOfPeers.get(txn, peers);
		if (bi == null) {
			if (force || maxPeers >= 1) {
				storeOfPeers.put(txn, peers, new ArrayOfPeers(Stream.of(peer)).toByteIterable());
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
				storeOfPeers.put(txn, peers, new ArrayOfPeers(concat).toByteIterable());
				return true;
			}
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
				storeOfPeers.put(txn, peers, new ArrayOfPeers(result).toByteIterable());
				return true;
			}
			else
				return false;
		}
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
			return check(NoSuchAlgorithmException.class, IOException.class, () ->
				Optional.ofNullable(storeOfBlocks.get(txn, fromBytes(hash)))
					.map(ByteIterable::getBytes)
					.map(uncheck(Blocks::from))
			);
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
		catch (IOException e) {
			throw new DatabaseException(e);
		}
	}

	private void putInStore(Transaction txn, Block block, byte[] hashOfBlock, byte[] bytesOfBlock) {
		storeOfBlocks.put(txn, fromBytes(hashOfBlock), fromBytes(bytesOfBlock));
	}

	private boolean isInStore(Transaction txn, byte[] hashOfBlock) {
		return storeOfBlocks.get(txn, fromBytes(hashOfBlock)) != null;
	}

	private boolean updateHead(Transaction txn, NonGenesisBlock block, byte[] hashOfBlock) throws DatabaseException, NoSuchAlgorithmException {
		Optional<Block> maybeHead = getHead(txn);
		if (maybeHead.isEmpty())
			throw new DatabaseException("the database of blocks is non-empty but the head is not set");

		// we choose the branch with more power
		if (block.getPower().compareTo(maybeHead.get().getPower()) > 0) {
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
		byte[] bytesOfBlock = block.toByteArray(), hashOfBlock = config.getHashingForBlocks().hash(bytesOfBlock);

		if (isInStore(txn, hashOfBlock)) {
			LOGGER.warning("not adding block " + block.getHexHash(config.getHashingForBlocks()) + " since it is already in the database");
			return false;
		}
		else if (block instanceof NonGenesisBlock ngb) {
			if (containsBlock(ngb.getHashOfPreviousBlock())) {
				putInStore(txn, block, hashOfBlock, bytesOfBlock);
				addToForwards(txn, ngb, hashOfBlock);
				if (updateHead(txn, ngb, hashOfBlock))
					updatedHead.set(ngb);

				node.submit(new BlockAddedEvent(ngb));
				return true;
			}
			else {
				LOGGER.warning("not adding block " + block.getHexHash(config.getHashingForBlocks()) + " since its previous block is not in the database");
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
				LOGGER.warning("not adding genesis block " + block.getHexHash(config.getHashingForBlocks()) + " since the database already contains a genesis block");
				return false;
			}
		}
	}

	private void setGenesisHash(Transaction txn, GenesisBlock newGenesis, byte[] newGenesisHash) {
		storeOfBlocks.put(txn, genesis, fromBytes(newGenesisHash));
		LOGGER.info("height " + newGenesis.getHeight() + ": block " + newGenesis.getHexHash(config.getHashingForBlocks()) + " set as genesis");
	}

	private void setHeadHash(Transaction txn, Block newHead, byte[] newHeadHash) throws NoSuchAlgorithmException, DatabaseException {
		storeOfBlocks.put(txn, head, fromBytes(newHeadHash));
		updateChain(txn, newHead, newHeadHash);
		LOGGER.info("height " + newHead.getHeight() + ": block " + newHead.getHexHash(config.getHashingForBlocks()) + " set as head");
	}

	private void updateChain(Transaction txn, Block block, byte[] blockHash) throws NoSuchAlgorithmException, DatabaseException {
		long height = block.getHeight();
		var heightBI = ByteIterable.fromBytes(longToBytes(height));
		storeOfChain.put(txn, Database.height, heightBI);
		var _new = ByteIterable.fromBytes(blockHash);
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
				heightBI = ByteIterable.fromBytes(longToBytes(height));
				_new = ByteIterable.fromBytes(blockHash);
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
		else {
			var maybeHeadHash = getHeadHash(txn);
			if (maybeHeadHash.isEmpty())
				throw new DatabaseException("The hash of the genesis is set but there is no head hash set in the database");

			ByteIterable heightBI = storeOfChain.get(txn, Database.height);
			if (heightBI == null)
				throw new DatabaseException("The hash of the genesis is set but the height of the current best chain is missing");

			long chainHeight = bytesToLong(heightBI.getBytes());
			if (chainHeight < 0L)
				throw new DatabaseException("The database contains a negative chain length");

			return ChainInfos.of(chainHeight, maybeGenesisHash, maybeHeadHash);
		}
	}

	private Stream<byte[]> getChain(Transaction txn, long start, long count) throws DatabaseException {
		if (start < 0L || count <= 0L)
			return Stream.empty();

		ByteIterable heightBI = storeOfChain.get(txn, Database.height);
		if (heightBI == null)
			return Stream.empty();

		long chainHeight = bytesToLong(heightBI.getBytes());
		if (chainHeight < 0L)
			throw new DatabaseException("The database contains a negative chain length");

		ByteIterable[] hashes = LongStream.range(start, Math.min(start + count, chainHeight + 1))
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

	private Environment createBlockchainEnvironment(Config config) {
		var env = new Environment(config.dir.resolve("blockchain").toString());
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
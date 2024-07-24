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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.closeables.AbstractAutoCloseableWithLock;
import io.hotmoka.exceptions.CheckRunnable;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.UnmarshallingContexts;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.xodus.ByteIterable;
import io.hotmoka.xodus.ExodusException;
import io.hotmoka.xodus.env.Environment;
import io.hotmoka.xodus.env.Store;
import io.hotmoka.xodus.env.Transaction;
import io.mokamint.node.DatabaseException;
import io.mokamint.node.Peers;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.local.api.LocalNodeConfig;

/**
 * The database where the peers are persisted.
 */
public class PeersDatabase extends AbstractAutoCloseableWithLock<ClosedDatabaseException> {

	/**
	 * The maximal number of non-forced peers kept in the database.
	 */
	private final long maxPeers;

	/**
	 * The Xodus environment that holds the database.
	 */
	private final Environment environment;

	/**
	 * The Xodus store that contains the set of peers of the node.
	 */
	private final Store storeOfPeers;

	/**
	 * The key mapped in the {@link #storeOfPeers} to the sequence of peers.
	 */
	private final static ByteIterable PEERS = fromByte((byte) 17);

	/**
	 * The key mapped in the {@link #storeOfPeers} to the unique identifier of the node
	 * having this database.
	 */
	private final static ByteIterable UUID = fromByte((byte) 23);

	private final static Logger LOGGER = Logger.getLogger(PeersDatabase.class.getName());

	/**
	 * Creates the database of a node.
	 * 
	 * @param node the node
	 * @throws NodeException if the node is misbehaving
	 */
	public PeersDatabase(LocalNodeImpl node) throws NodeException {
		super(ClosedDatabaseException::new);

		LocalNodeConfig config = node.getConfig();
		this.maxPeers = config.getMaxPeers();
		this.environment = createBlockchainEnvironment(config);
		this.storeOfPeers = openStore("peers");
		ensureNodeUUID();
	}

	@Override
	public void close() throws NodeException, InterruptedException {
		if (stopNewCalls()) {
			try {
				environment.close(); // the lock guarantees that there are no unfinished transactions at this moment
				LOGGER.info("db: closed the peers database");
			}
			catch (ExodusException e) {
				LOGGER.log(Level.WARNING, "db: failed to close the peers database", e);
				throw new DatabaseException("Cannot close the peers database", e);
			}
		}
	}

	/**
	 * Yields the UUID of the node having this database.
	 * 
	 * @return the UUID
	 * @throws DatabaseException if the database is corrupted
	 * @throws NodeException if the database is already closed
	 */
	public UUID getUUID() throws DatabaseException, NodeException {
		try (var scope = mkScope()) {
			var bi = environment.computeInReadonlyTransaction(txn -> storeOfPeers.get(txn, UUID));
			if (bi == null)
				throw new DatabaseException("The UUID of the node is not in the peers database");

			return MarshallableUUID.from(bi).uuid;
		}
		catch (ExodusException | IOException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Yields the set of peers saved in this database, if any.
	 * 
	 * @return the peers
	 * @throws DatabaseException of the database is corrupted
	 * @throws NodeException if the node is misbehaving
	 */
	public Stream<Peer> getPeers() throws DatabaseException, NodeException {
		try (var scope = mkScope()) {
			var bi = environment.computeInReadonlyTransaction(txn -> storeOfPeers.get(txn, PEERS));
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
	 * @throws NodeException if the node is misbehaving
	 */
	public boolean add(Peer peer, boolean force) throws NodeException {
		try (var scope = mkScope()) {
			return check(URISyntaxException.class, IOException.class, NodeException.class,
				() -> environment.computeInTransaction(uncheck(txn -> add(txn, peer, force))));
		}
		catch (IOException | URISyntaxException | ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Removes the given peer from those stored in this database.
	 * 
	 * @param peer the peer to remove
	 * @return true if the peer has been actually removed; false otherwise, which means
	 *         that the peer was not in the database
	 * @throws DatabaseException if the database is corrupted
	 * @throws NodeException if the node is misbehaving
	 */
	public boolean remove(Peer peer) throws DatabaseException, NodeException {
		try (var scope = mkScope()) {
			return check(URISyntaxException.class, IOException.class, DatabaseException.class,
					() -> environment.computeInTransaction(uncheck(txn -> remove(txn, peer))));
		}
		catch (IOException | URISyntaxException | ExodusException e) {
			throw new DatabaseException(e);
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

	private void ensureNodeUUID() throws NodeException {
		try {
			CheckRunnable.check(IOException.class, () -> {
				environment.executeInTransaction(txn -> {
					var bi = storeOfPeers.get(txn, UUID);
					if (bi == null) {
						var nodeUUID = java.util.UUID.randomUUID();
						storeOfPeers.put(txn, UUID, fromBytes(new MarshallableUUID(nodeUUID).toByteArray()));
						LOGGER.info("db: created a new UUID for the node: " + nodeUUID);
					}
					else {
						var nodeUUID = uncheck(MarshallableUUID::from).apply(bi).uuid;
						LOGGER.info("db: the UUID of the node is " + nodeUUID);
					}
				});
			});
		}
		catch (ExodusException | IOException e) {
			throw new DatabaseException(e);
		}
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

	private boolean add(Transaction txn, Peer peer, boolean force) throws IOException, URISyntaxException, NodeException {
		try {
			var bi = storeOfPeers.get(txn, PEERS);
			if (bi == null) {
				if (force || maxPeers >= 1) {
					storeOfPeers.put(txn, PEERS, new ArrayOfPeers(Stream.of(peer)).toByteIterable());
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
					storeOfPeers.put(txn, PEERS, new ArrayOfPeers(concat).toByteIterable());
					return true;
				}
			}
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private boolean remove(Transaction txn, Peer peer) throws IOException, URISyntaxException, DatabaseException {
		try {
			var bi = storeOfPeers.get(txn, PEERS);
			if (bi == null)
				return false;
			else {
				var aop = ArrayOfPeers.from(bi);
				if (aop.contains(peer)) {
					Stream<Peer> result = aop.stream().filter(p -> !peer.equals(p));
					storeOfPeers.put(txn, PEERS, new ArrayOfPeers(result).toByteIterable());
					return true;
				}
				else
					return false;
			}
		}
		catch (ExodusException e) {
			throw new DatabaseException(e);
		}
	}

	private Environment createBlockchainEnvironment(LocalNodeConfig config) {
		var env = new Environment(config.getDir().resolve("peers").toString());
		LOGGER.info("db: opened the peers database");
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

		LOGGER.info("db: opened the store of " + name);
		return store.get();
	}
}
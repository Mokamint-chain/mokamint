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
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.closeables.AbstractAutoCloseableWithLock;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.UnmarshallingContexts;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.xodus.ByteIterable;
import io.hotmoka.xodus.ExodusException;
import io.hotmoka.xodus.env.Environment;
import io.hotmoka.xodus.env.Store;
import io.hotmoka.xodus.env.Transaction;
import io.mokamint.node.Peers;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.local.api.LocalNodeConfig;

/**
 * The database where peers are persisted.
 */
public class PeersDatabase extends AbstractAutoCloseableWithLock<ClosedDatabaseException> {

	/**
	 * The maximal number of non-forced peers kept in the database.
	 */
	private final int maxPeers;

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
	 * @throws ClosedNodeException if the node is already closed
	 */
	public PeersDatabase(LocalNodeImpl node) throws ClosedNodeException {
		super(ClosedDatabaseException::new);

		LocalNodeConfig config = node.getConfig();
		this.maxPeers = config.getMaxPeers();
		this.environment = createPeersEnvironment(config);
		this.storeOfPeers = openStore("peers");
		ensureNodeUUID();
	}

	@Override
	public void close() {
		try {
			if (stopNewCalls()) {
				try {
					environment.close(); // the lock guarantees that there are no unfinished transactions at this moment
					LOGGER.info("db: closed the peers database");
				}
				catch (ExodusException e) {
					LOGGER.log(Level.SEVERE, "db: failed to close the peers database", e);
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Yields the UUID of the node having this database.
	 * 
	 * @return the UUID
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public UUID getUUID() throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			var bi = environment.computeInReadonlyTransaction(txn -> storeOfPeers.get(txn, UUID));
			if (bi == null)
				throw new UncheckedDatabaseException("The UUID of the node is not in the peers database");

			return MarshallableUUID.from(bi).uuid;
		}
		catch (ExodusException | IOException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Yields the set of peers saved in this database, if any.
	 * 
	 * @return the peers
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Stream<Peer> getPeers() throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			var bi = environment.computeInReadonlyTransaction(txn -> storeOfPeers.get(txn, PEERS));
			return bi == null ? Stream.empty() : ArrayOfPeers.from(bi).stream();
		}
		catch (IOException | ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Adds the given peer to the set of peers in this database.
	 * 
	 * @param peer the peer to add
	 * @param force true if the peer must be added, regardless of the total amount
	 *              of peers already added; false otherwise, which means that the maximal
	 *              number of peers has been reached
	 * @return true if the peer has been added; false otherwise, which means
	 *         that the peer was already present or that it was not forced
	 *         and there are already {@link maxPeers} peers
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean add(Peer peer, boolean force) throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInTransaction(txn -> add(txn, peer, force));
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	/**
	 * Removes the given peer from those stored in this database.
	 * 
	 * @param peer the peer to remove
	 * @return true if the peer has been actually removed; false otherwise, which means
	 *         that the peer was not in the database
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public boolean remove(Peer peer) throws ClosedDatabaseException {
		try (var scope = mkScope()) {
			return environment.computeInTransaction(txn -> remove(txn, peer));
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
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

	private void ensureNodeUUID() {
		environment.executeInTransaction(txn -> {
			try {
				var bi = storeOfPeers.get(txn, UUID);
				if (bi == null) {
					var nodeUUID = java.util.UUID.randomUUID();
					storeOfPeers.put(txn, UUID, fromBytes(new MarshallableUUID(nodeUUID).toByteArray()));
					LOGGER.info("db: created a new UUID for the node: " + nodeUUID);
				}
				else
					LOGGER.info("db: the UUID of the node is " + MarshallableUUID.from(bi).uuid);
			}
			catch (IOException | ExodusException e) {
				throw new UncheckedDatabaseException(e);
			}
		});
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
		 */
		private static ArrayOfPeers from(ByteIterable bi) throws IOException {
			try (var bais = new ByteArrayInputStream(bi.getBytes()); var context = UnmarshallingContexts.of(bais)) {
				var peers = new Peer[context.readCompactInt()];
				for (int pos = 0; pos < peers.length; pos++)
					peers[pos] = Peers.from(context);
		
				return new ArrayOfPeers(Stream.of(peers));
			}
		}
	}

	private boolean add(Transaction txn, Peer peer, boolean force) {
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
		catch (ExodusException | IOException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	private boolean remove(Transaction txn, Peer peer) {
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
		catch (ExodusException | IOException e) {
			throw new UncheckedDatabaseException(e);
		}
	}

	private Environment createPeersEnvironment(LocalNodeConfig config) {
		var path = config.getDir().resolve("peers");
		var env = new Environment(path.toString());
		LOGGER.info("db: opened the peers database at " + path);
		return env;
	}

	private Store openStore(String name) {
		try {
			Store store = environment.computeInTransaction(txn -> environment.openStoreWithoutDuplicates(name, txn));
			LOGGER.info("db: opened the store of " + name);
			return store;
		}
		catch (ExodusException e) {
			throw new UncheckedDatabaseException(e);
		}
	}
}
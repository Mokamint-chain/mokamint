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
import static java.util.function.Predicate.not;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.exceptions.CheckRunnable;
import io.hotmoka.exceptions.CheckSupplier;
import io.hotmoka.exceptions.UncheckConsumer;
import io.hotmoka.exceptions.UncheckFunction;
import io.hotmoka.exceptions.UncheckPredicate;
import io.hotmoka.exceptions.UncheckedException;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.SanitizedStrings;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.LocalNodeImpl.Event;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.messages.WhisperPeersMessages;
import io.mokamint.node.messages.api.WhisperBlockMessage;
import io.mokamint.node.messages.api.WhisperPeersMessage;
import io.mokamint.node.messages.api.Whisperer;
import io.mokamint.node.remote.RemotePublicNode;
import io.mokamint.node.remote.RemotePublicNodes;
import jakarta.websocket.DeploymentException;

/**
 * The set of peers of a local node. This class guarantees that,
 * if a peer has a remote, then it is in the database of peers
 * (but the converse might not hold since, for instance, a peer
 * might be currently unreachable).
 */
@ThreadSafe
public class NodePeers implements AutoCloseable {

	/**
	 * The node.
	 */
	private final LocalNodeImpl node;

	/**
	 * The configuration of the node.
	 */
	private final Config config;

	/**
	 * The database containing the peers.
	 */
	private final PeersDatabase db;

	/**
	 * The peers of the node.
	 */
	private final PunishableSet<Peer> peers;

	/**
	 * Lock used to guarantee that if there is a peer among the keys of the {@link #remotes}
	 * map or among the keys of the {@link #timeDifferences} map then the peer is
	 * in the {@link #peers} set (the converse might well not hold).
	 */
	private final Object lock = new Object();

	/**
	 * The remote nodes connected to each peer of {@link #node}.
	 */
	private final ConcurrentMap<Peer, RemotePublicNode> remotes = new ConcurrentHashMap<>();

	/**
	 * The time difference (in milliseconds) between the {@link #node} and each of its peers.
	 */
	private final ConcurrentMap<Peer, Long> timeDifferences = new ConcurrentHashMap<>();

	private final static Logger LOGGER = Logger.getLogger(NodePeers.class.getName());

	/**
	 * Creates the set of peers of a local node.
	 * 
	 * @param node the node having these peers
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database of {@code node} is already closed
	 */
	public NodePeers(LocalNodeImpl node) throws DatabaseException, ClosedDatabaseException {
		this.node = node;
		this.config = node.getConfig();
		this.db = new PeersDatabase(node);
		this.peers = new PunishableSet<>(db.getPeers(), config.peerInitialPoints, this::onAdd, this::onRemove);
	}

	/**
	 * Connects to the peers.
	 * 
	 * @throws ClosedNodeException if the node is already closed
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws InterruptedException if the connection gets interrupted
	 * @throws IOException if an I/O error occurs
	 */
	void connect() throws ClosedNodeException, DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
		openConnectionToPeers();
		node.submitWithFixedDelay(new PingPeersRecreateRemotesAndCollectPeersTask(), 0L, config.peerPingInterval, TimeUnit.MILLISECONDS);
		tryToAdd(config.seeds().map(Peers::of), true, true);
	}

	/**
	 * Yields information about these peers.
	 * 
	 * @return the peers information
	 */
	public Stream<PeerInfo> get() {
		return peers.getActorsWithPoints().map(entry -> PeerInfos.of(entry.getKey(), entry.getValue(), remotes.containsKey(entry.getKey())));
	}

	/**
	 * Yields the UUID of the node having these peers.
	 * 
	 * @return the UUID
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public UUID getUUID() throws DatabaseException, ClosedDatabaseException {
		return db.getUUID();
	}

	/**
	 * Adds the given peer. This might fail because
	 * the peer is already present in the node, or because a connection to the peer
	 * cannot be established, or because the peer is incompatible with the node.
	 * If the peer was present but was disconnected, it tries to reconnect it.
	 * 
	 * @param peer the peer to add
	 * @param force true if the peer must be added also if the maximum number of peers for the node has been reached
	 * @param whisper true if and only if the added peer must be whispered to all peers after the addition
	 * @return true if and only if the peer has been added
	 * @throws IOException if a connection to the peer cannot be established
	 * @throws PeerRejectedException if the addition of {@code peer} was rejected for some reason
	 * @throws DatabaseException if the database of the node is corrupted
	 * @throws TimeoutException if the addition does not complete in time
	 * @throws InterruptedException if the current thread is interrupted while waiting for the addition to complete
	 * @throws ClosedNodeException if the node is closed
	 * @throws ClosedDatabaseException if the database of the node is closed
	 */
	public boolean add(Peer peer, boolean force, boolean whisper) throws TimeoutException, InterruptedException, IOException, PeerRejectedException, DatabaseException, ClosedNodeException, ClosedDatabaseException {
		if (containsDisconnected(peer)) {
			tryToRecreateRemote(peer);
			return false;
		}
		else {
			// we uncheck the exceptions of onAdd
			boolean result = check2(TimeoutException.class,
					InterruptedException.class,
					IOException.class,
					PeerRejectedException.class,
					DatabaseException.class,
					ClosedNodeException.class,
					ClosedDatabaseException.class,
					() -> peers.add(peer, force));

			if (result)
				node.submit(new PeersAddedEvent(Stream.of(peer), whisper));

			return result;
		}
	}

	/**
	 * Removes a peer.
	 * 
	 * @param peer the peer to remove
	 * @return true if and only if the peer has been removed
	 * @throws DatabaseException if the database of the node is corrupted
	 * @throws ClosedDatabaseException if the database of the node is already closed
	 * @throws InterruptedException if the operation was interrupted
	 * @throws IOException if an I/O exception occurred while contacting the peer
	 */
	public boolean remove(Peer peer) throws DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
		// we uncheck the exceptions of onRemove
		return check(DatabaseException.class, ClosedDatabaseException.class, InterruptedException.class, IOException.class, () -> peers.remove(peer));
	}

	/**
	 * Whispers some peers to this container of peers. It forwards the message
	 * to the peers in this container and adds the peers in the message to this
	 * container, if requested.
	 * 
	 * @param message the message containing the whispered peers
	 * @param seen the whisperers already seen during whispering
	 * @param tryToAdd if the peers must be added to those in this container
	 */
	public void whisper(WhisperPeersMessage message, Predicate<Whisperer> seen, boolean tryToAdd) {
		if (tryToAdd) {
			// we check if the node needs any of the whispered peers
			var usefulToAdd = message.getPeers().distinct().toArray(Peer[]::new);
			if (usefulToAdd.length > 0)
				node.submit(new AddWhisperedPeersTask(usefulToAdd));
		}
	
		// in any case, we forward the message to our peers
		remotes.values().forEach(remote -> remote.whisper(message, seen));
	}

	/**
	 * Whispers a block to this container of peers. It forwards the message
	 * to the peers in this container.
	 * 
	 * @param message the message containing the whispered block
	 * @param seen the whisperers already seen during whispering
	 */
	public void whisper(WhisperBlockMessage message, Predicate<Whisperer> seen) {
		// we forward the message to our peers
		remotes.values().forEach(remote -> remote.whisper(message, seen));
	}

	/**
	 * Yields the given date and time, normalized wrt the network time, ie,
	 * the average time among that of each connected peers and of the node itself.
	 * 
	 * @param ldt the date and time to normalize
	 * @return the normalized {@code ldt}. If there are no peers, this will be {@code ldt} itself
	 */
	public LocalDateTime asNetworkDateTime(LocalDateTime ldt) {
		long averageTimeDifference = (long)
			// we add the difference of the time of the node with itself: 0L
			Stream.concat(timeDifferences.values().stream(), Stream.of(0L))
				.mapToLong(Long::valueOf)
				.average()
				.getAsDouble(); // we know there is at least an element (0L) hence the average exists

		return ldt.plus(averageTimeDifference, ChronoUnit.MILLIS);
	}

	@Override
	public void close() throws IOException, InterruptedException, DatabaseException {
		IOException ioException = null;
		InterruptedException interruptedException = null;

		try {
			synchronized (lock) {
				for (var entry: remotes.entrySet()) {
					try {
						deletePeer(entry.getKey(), entry.getValue());
					}
					catch (IOException e) {
						ioException = e;
					}
					catch (InterruptedException e) {
						interruptedException = e;
					}
				}
			}
		}
		finally {
			try {
				db.close();
			}
			finally {
				if (interruptedException != null)
					throw interruptedException;
				else if (ioException != null)
					throw ioException;
			}
		}
	}

	/**
	 * Yields the remote note that can be used to interact with the given peer.
	 * 
	 * @param peer the peer
	 * @return the remote, if any. This might be missing if, for instance, the
	 *         peer is currently unreachable
	 */
	public Optional<RemotePublicNode> getRemote(Peer peer) {
		return Optional.ofNullable(remotes.get(peer));
	}

	/**
	 * A task that adds whispered peers to the node.
	 */
	public class AddWhisperedPeersTask implements Task {
		private final Peer[] peers;

		private AddWhisperedPeersTask(Peer[] peers) {
			this.peers = peers;
		}

		@Override
		public String toString() {
			return "addition of whispered peers " + SanitizedStrings.of(Stream.of(peers));
		}

		@Override
		public void body() throws ClosedNodeException, DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
			tryToAdd(Stream.of(peers), false, false);
		}

		@Override
		public String logPrefix() {
			return "peers: ";
		}
	}

	/**
	 * An event fired to signal that some peers have been added to the node.
	 */
	public class PeersAddedEvent implements Event {
		private final Peer[] peers;
		private final boolean whisper;

		private PeersAddedEvent(Stream<Peer> peers, boolean whisper) {
			this.peers = peers.toArray(Peer[]::new);
			this.whisper = whisper;
		}

		@Override
		public String toString() {
			return "addition event for peers " + SanitizedStrings.of(Stream.of(peers));
		}

		/**
		 * Yields the added peers.
		 * 
		 * @return the added peers
		 */
		public Stream<Peer> getPeers() {
			return Stream.of(peers);
		}

		@Override
		public void body() {
			if (whisper)
				node.whisper(WhisperPeersMessages.of(getPeers(), UUID.randomUUID().toString()), _whisperer -> false);
		}

		@Override
		public String logPrefix() {
			return "peers: ";
		}
	}

	/**
	 * A task that pings all peers, tries to recreate their remote (if missing)
	 * and collects their peers, in case they might be useful for the node.
	 */
	public class PingPeersRecreateRemotesAndCollectPeersTask implements Task {

		private PingPeersRecreateRemotesAndCollectPeersTask() {}

		@Override
		public void body() throws ClosedNodeException, DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
			var allPeers = CheckSupplier.check(ClosedNodeException.class, DatabaseException.class, ClosedDatabaseException.class, InterruptedException.class, IOException.class, () ->
				peers.getElements().parallel().flatMap(UncheckFunction.uncheck(this::pingPeerRecreateRemoteAndCollectPeers))
			);
			tryToAdd(allPeers, false, true);
		}

		@Override
		public String logPrefix() {
			return "peers: ";
		}

		@Override
		public String toString() {
			return "pinging to all peers to create missing remotes and collect their peers";
		}

		/**
		 * Contacts the peer and, if successful, collects all its peers.
		 * 
		 * @param peer the peer to contact
		 * @return the peers of {@code peer}
		 * @throws ClosedNodeException if {@link #node} is closed
		 * @throws DatabaseException if the database of {@link #node} is corrupted
		 * @throws ClosedDatabaseException if the database of {@link #node} is already closed
		 * @throws InterruptedException if the execution was interrupted while waiting to establish a connection to the peer
		 * @throws IOException if an I/O exception occurred while contacting the peer
		 */
		private Stream<Peer> pingPeerRecreateRemoteAndCollectPeers(Peer peer) throws ClosedNodeException, DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
			Optional<RemotePublicNode> remote = getRemote(peer);
			if (remote.isEmpty())
				remote = tryToRecreateRemote(peer);

			if (remote.isPresent())
				return askForPeers(peer, remote.get());
			else
				return Stream.empty();
		}

		private Stream<Peer> askForPeers(Peer peer, RemotePublicNode remote) throws InterruptedException, DatabaseException, ClosedDatabaseException, IOException {
			try {
				Stream<PeerInfo> infos = remote.getPeerInfos();
				pardonBecauseReachable(peer);

				if (peers.getElements().count() >= config.maxPeers) // optimization
					return Stream.empty();
				else
					return infos.filter(PeerInfo::isConnected).map(PeerInfo::getPeer).filter(not(peers::contains));
			}
			catch (TimeoutException | ClosedNodeException e) {
				LOGGER.log(Level.WARNING, logPrefix() + "cannot contact peer " + peer + ": " + e.getMessage());
				punishBecauseUnreachable(peer);
				return Stream.empty();
			}
		}
	}

	public void punishBecauseUnreachable(Peer peer) throws DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
		check(DatabaseException.class, ClosedDatabaseException.class, InterruptedException.class, IOException.class, () -> peers.punish(peer, config.peerPunishmentForUnreachable));
	}

	public void pardonBecauseReachable(Peer peer) {
		peers.pardon(peer, config.peerPunishmentForUnreachable);
	}

	// TODO: remove after pushing the new hotmoka jars to Maven Central
	@SuppressWarnings("unchecked")
	private static <R, T1 extends Throwable, T2 extends Throwable, T3 extends Throwable, T4 extends Throwable, T5 extends Throwable, T6 extends Throwable, T7 extends Throwable>
			R check2(Class<T1> exception1, Class<T2> exception2, Class<T3> exception3, Class<T4> exception4, Class<T5> exception5, Class<T6> exception6, Class<T7> exception7,
					Supplier<R> supplier) throws T1, T2, T3, T4, T5, T6, T7 {
	
		try {
			return supplier.get();
		}
		catch (UncheckedException e) {
			var cause = e.getCause();
			if (exception1.isInstance(cause))
				throw (T1) cause;
			else if (exception2.isInstance(cause))
				throw (T2) cause;
			else if (exception3.isInstance(cause))
				throw (T3) cause;
			else if (exception4.isInstance(cause))
				throw (T4) cause;
			else if (exception5.isInstance(cause))
				throw (T5) cause;
			else if (exception6.isInstance(cause))
				throw (T6) cause;
			else if (exception7.isInstance(cause))
				throw (T7) cause;
			else
				throw e;
		}
	}

	/**
	 * Add some peers to the node. If the peer was already present but was disconnected,
	 * it tries to open a connection to the peer. Peers might not be added because
	 * they are already present in the node, or because a connection cannot be established
	 * to them, or because they are incompatible with the node. In such cases, the peers are
	 * simply ignored and no exception is thrown.
	 * 
	 * @param peers the peers to add
	 * @param force true if and only if the peers must be added also when the maximal number of peers
	 *              for the node has been reached
	 * @param whisper true if and only if the added peers must be whispered to all peers
	 *                after the addition
	 * @throws InterruptedException if the execution was interrupted
	 * @throws ClosedDatabaseException if the database of {@link #node} is closed
	 * @throws DatabaseException if the database of {@link #node} is corrupted
	 * @throws ClosedNodeException if {@link #node} is closed
	 * @throws IOException if an I/O error occurs
	 */
	private void tryToAdd(Stream<Peer> peers, boolean force, boolean whisper) throws ClosedNodeException, DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
		var peersAsArray = peers.toArray(Peer[]::new);
	
		var added = CheckSupplier.check(ClosedNodeException.class, DatabaseException.class, ClosedDatabaseException.class, InterruptedException.class, IOException.class, () ->
			Stream.of(peersAsArray)
				.parallel()
				.distinct()
				.filter(UncheckPredicate.uncheck(peer -> reconnectOrAdd(peer, force)))
				.toArray(Peer[]::new)
			);
	
		if (added.length > 0) // just to avoid useless events
			node.submit(new PeersAddedEvent(Stream.of(added), whisper));
	}

	/**
	 * If the peer is not in this container, adds it, if possible.
	 * This might fail if the peer was already
	 * present, or a connection to the peer cannot be established, or the peer
	 * is incompatible with the node. In such cases, this method just ignores
	 * the addition and nothing happens. If the peer was already in this container,
	 * but was disconnected, tries to reconnect to it.
	 * 
	 * @param peer the peer to add
	 * @param force true if and only if the addition must be performed also if
	 *              the maximal number of peers for the node has been reached
	 * @return true if and only if the peer has been added
	 * @throws InterruptedException if the execution was interrupted
	 * @throws ClosedDatabaseException if the database of {@link #node} is closed
	 * @throws DatabaseException if the database of {@link #node} is corrupted
	 * @throws ClosedNodeException if {@link #node} is closed
	 * @throws IOException if an I/O error occurred
	 */
	private boolean reconnectOrAdd(Peer peer, boolean force) throws ClosedNodeException, DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
		if (containsDisconnected(peer)) {
			tryToRecreateRemote(peer);
			return false;
		}
		else {
			try {
				// we do not spawn an event since we will spawn one at the end for all peers
				return check2(TimeoutException.class,
						ClosedNodeException.class,
						ClosedDatabaseException.class,
						InterruptedException.class,
						IOException.class,
						PeerRejectedException.class,
						DatabaseException.class,
						() -> peers.add(peer, force));
			}
			catch (PeerRejectedException | IOException | TimeoutException e) {
				return false;
			}
		}
	}

	/**
	 * Determines if this container contains a given peer, but disconnected.
	 * 
	 * @param peer the peer
	 * @return true if and only if this container contains {@code peer}, but disconnected
	 */
	private boolean containsDisconnected(Peer peer) {
		return peers.contains(peer) && remotes.get(peer) == null;
	}

	/**
	 * Opens a remote for each peer, if possible.
	 * 
	 * @throws ClosedNodeException if {@link #node} is closed
	 * @throws DatabaseException if the database of {@link #node} is corrupted
	 * @throws ClosedDatabaseException if the database of {@link #node} is already closed
	 * @throws InterruptedException if the execution was interrupted while waiting to establish a connection to the peer
	 * @throws IOException if an I/O error occurs
	 */
	private void openConnectionToPeers() throws ClosedNodeException, DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
		CheckRunnable.check(ClosedNodeException.class, DatabaseException.class, ClosedDatabaseException.class, InterruptedException.class, IOException.class, () ->
			get().parallel().filter(not(PeerInfo::isConnected)).map(PeerInfo::getPeer).forEach(UncheckConsumer.uncheck(this::tryToRecreateRemote))
		);
	}

	private boolean onAdd(Peer peer, boolean force) {
		// optimization: this avoids opening a remote for a peer that would not be added anyway
		if (!force && peers.getElements().count() >= config.maxPeers)
			return false;

		try {
			RemotePublicNode remote = null;

			try {
				remote = openRemote(peer);
				long timeDifference = ensurePeerIsCompatible(remote);

				synchronized (lock) {
					if (db.add(peer, force)) {
						LOGGER.info("peers: added peer " + peer + " to the database");
						storePeer(peer, remote, timeDifference);
						remote = null; // so that it won't be closed in the finally clause
						return true;
					}
					else
						return false;
				}
			}
			finally {
				deletePeer(peer, remote);
			}
		}
		catch (InterruptedException e) {
			LOGGER.log(Level.WARNING, "peers: interrupted while adding " + peer + " to the peers: " + e.getMessage());
			Thread.currentThread().interrupt();
			throw new UncheckedException(e);
		}
		catch (IOException | TimeoutException | ClosedNodeException | DatabaseException | ClosedDatabaseException | PeerRejectedException e) {
			LOGGER.log(Level.WARNING, "peers: cannot add peer " + peer + ": " + e.getMessage());
			throw new UncheckedException(e);
		}
	}

	private boolean onRemove(Peer peer) {
		try {
			synchronized (lock) {
				if (db.remove(peer)) {
					LOGGER.info("peers: removed peer " + peer + " from the database");
					deletePeer(peer, remotes.get(peer));
					return true;
				}
				else
					return false;
			}
		}
		catch (DatabaseException | ClosedDatabaseException | InterruptedException | IOException e) {
			LOGGER.log(Level.SEVERE, "peers: cannot remove peer " + peer + ": " + e.getMessage());
			throw new UncheckedException(e);
		}
	}

	/**
	 * Tries to recreate a remote for the given peer. This might fail for a few
	 * reasons, for instance, because the peer is incompatible with {@link #node}
	 * or because the peer is closed or the connection timed out.
	 * 
	 * @param peer the peer
	 * @return the remote, if it was possible to create it
	 * @throws ClosedNodeException if {@link #node} is closed
	 * @throws DatabaseException if the database of {@link #node} is corrupted
	 * @throws ClosedDatabaseException if the database of {@link #node} is already closed
	 * @throws InterruptedException if the execution was interrupted while waiting to establish a connection to the peer
	 * @throws IOException if an I/O error occurred
	 */
	private Optional<RemotePublicNode> tryToRecreateRemote(Peer peer) throws ClosedNodeException, DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
		try {
			LOGGER.info("peers: trying to create a connection to peer " + peer);
			RemotePublicNode remote = null;

			try {
				remote = openRemote(peer);
				RemotePublicNode remoteCopy = remote;
				long timeDifference = ensurePeerIsCompatible(remote);

				synchronized (lock) {
					// we check if the peer is actually contained in the set of peers,
					// since it might have been removed in the meanwhile and we not not
					// want to store remotes for peers not in the set of peers of this object
					if (containsDisconnected(peer)) {
						storePeer(peer, remote, timeDifference);
						remote = null; // so that it won't be closed in the finally clause
					}
				}

				return Optional.of(remoteCopy);
			}
			finally {
				deletePeer(peer, remote);
			}
		}
		catch (IOException | TimeoutException e) {
			LOGGER.log(Level.WARNING, "peers: cannot contact peer " + peer + ": " + e.getMessage());
			punishBecauseUnreachable(peer);
		}
		catch (PeerRejectedException e) {
			LOGGER.log(Level.WARNING, "peers: " + e.getMessage());
			remove(peer);
		}

		return Optional.empty();
	}

	private RemotePublicNode openRemote(Peer peer) throws IOException {
		try {
			var remote = RemotePublicNodes.of(peer.getURI(), config.peerTimeout, config.whisperingMemorySize);
			LOGGER.info("peers: opened connection to peer " + peer);
			return remote;
		}
		catch (DeploymentException e) {
			throw new IOException(e);  // we consider it as a special case of IOException
		}
	}

	/**
	 * Checks if the peer whose remote is provided is compatible with {@link #node}.
	 * 
	 * @param remote the remote of the peer
	 * @return the time difference (in milliseconds) between the local time of {@link #node}
	 *         and the local time of the peer (this is positive if the clock of the peer is
	 *         in the future wrt the clock of this node; it is positive in the opposite case)
	 * @throws PeerRejectedException if the peer was rejected for some reason
	 * @throws TimeoutException if the peer could not be contacted through the {@code remote}
	 * @throws InterruptedException if the connection to the peer though {@code remote} was interrupted
	 * @throws ClosedNodeException if {@link #node} is closed
	 * @throws DatabaseException if the database of {@link #node} is corrupted
	 * @throws ClosedDatabaseException  if the database is already closed
	 */
	private long ensurePeerIsCompatible(RemotePublicNode remote) throws PeerRejectedException, TimeoutException, InterruptedException, ClosedNodeException, DatabaseException, ClosedDatabaseException {
		NodeInfo info1;

		try {
			info1 = remote.getInfo();
		}
		catch (ClosedNodeException e) {
			// it's the remote peer that is closed, not our node
			throw new PeerRejectedException("the peer is closed", e);
		}

		NodeInfo info2 = node.getInfo();

		long timeDifference = ChronoUnit.MILLIS.between(info2.getLocalDateTimeUTC(), info1.getLocalDateTimeUTC());
		if (Math.abs(timeDifference) > config.peerMaxTimeDifference)
			throw new PeerRejectedException("the time of the peer is more than " + config.peerMaxTimeDifference + " ms away from the time of this node");
			
		UUID uuid1 = info1.getUUID();
		if (uuid1.equals(db.getUUID()))
			throw new PeerRejectedException("a peer cannot be added as a peer of itself: same UUID " + uuid1);

		var version1 = info1.getVersion();
		var version2 = info2.getVersion();
		if (!version1.canWorkWith(version2))
			throw new PeerRejectedException("peer version " + version1 + " is incompatible with this node's version " + version2);

		ChainInfo chain1;

		try {
			chain1 = remote.getChainInfo();
		}
		catch (ClosedNodeException e) {
			// it's the remote peer that is closed, not our node
			throw new PeerRejectedException("the peer is closed", e);
		}
		catch (DatabaseException e) {
			// it's the remote peer that has database problems, not our node
			throw new PeerRejectedException("the peer's database is corrupted", e);
		}

		Optional<byte[]> genesisHash1 = chain1.getGenesisHash();
		if (genesisHash1.isPresent()) {
			ChainInfo chain2 = node.getChainInfo();
			Optional<byte[]> genesisHash2 = chain2.getGenesisHash();
			if (genesisHash2.isPresent() && !Arrays.equals(genesisHash1.get(), genesisHash2.get()))
				throw new PeerRejectedException("the peers have distinct genesis blocks");
		}

		return timeDifference;
	}

	private void storePeer(Peer peer, RemotePublicNode remote, long timeDifference) {
		remotes.put(peer, remote);
		timeDifferences.put(peer, timeDifference);
		remote.bindWhisperer(node);
		// if the remote gets closed, then it will get unlinked from the map of remotes
		remote.addOnClosedHandler(() -> peerDisconnected(remote, peer));
		node.submit(new PeerConnectedEvent(peer));
	}

	/**
	 * An event fired to signal that a peer of the node has been connected.
	 */
	public class PeerConnectedEvent implements Event {
		private final Peer peer;

		private PeerConnectedEvent(Peer peer) {
			this.peer = peer;
		}

		@Override
		public String toString() {
			return "connection event for peer " + peer;
		}

		/**
		 * Yields the connected peer.
		 * 
		 * @return the connected peer
		 */
		public Peer getPeer() {
			return peer;
		}

		@Override
		public void body() throws DatabaseException, ClosedDatabaseException {
			node.whisperItsServices();

			// if the blockchain was empty, it might be the right moment to attempt a synchronization
			if (node.getBlockchain().isEmpty())
				node.getBlockchain().startSynchronization(0L);
		}

		@Override
		public String logPrefix() {
			return "peers: ";
		}
	}

	/**
	 * Called when a peer gets closed: it removes its remote and generates an event.
	 * 
	 * @param remote the remote of the peer, which is what is actually being closed
	 * @param peer the peer
	 * @throws InterruptedException if the closure operation has been interrupted
	 * @throws IOException if an I/O exceptions occurs while closing the remote of the peer
	 */
	private void peerDisconnected(RemotePublicNode remote, Peer peer) throws InterruptedException, IOException {
		deletePeer(peer, remote);
		node.submit(new PeerDisconnectedEvent(peer));
	}

	/**
	 * An event fired to signal that a peer of the node have been disconnected.
	 */
	public class PeerDisconnectedEvent implements Event {
		private final Peer peer;

		private PeerDisconnectedEvent(Peer peer) {
			this.peer = peer;
		}

		@Override
		public String toString() {
			return "disconnection event for peer " + peer;
		}

		/**
		 * Yields the disconnected peer.
		 * 
		 * @return the disconnected peer
		 */
		public Peer getPeer() {
			return peer;
		}

		@Override
		public void body() throws DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
			punishBecauseUnreachable(peer);
		}

		@Override
		public String logPrefix() {
			return "peers: ";
		}
	}

	private void deletePeer(Peer peer, RemotePublicNode remote) throws InterruptedException, IOException {
		if (remote != null) {
			remote.unbindWhisperer(node);
			remotes.remove(peer);
			timeDifferences.remove(peer);
			remote.close();
			LOGGER.info("peers: closed connection to peer " + peer);
		}
	}
}
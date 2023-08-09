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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.exceptions.UncheckedException;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.SanitizedStrings;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
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
	 * The database of the node.
	 */
	private final Database db;

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
	 */
	public NodePeers(LocalNodeImpl node) throws DatabaseException {
		this.node = node;
		this.config = node.getConfig();
		this.db = node.getDatabase();
		this.peers = PunishableSets.of(db.getPeers(), config.peerInitialPoints, this::onAdd, this::onRemove);
		openConnectionToPeers();
		node.submitWithFixedDelay(new PingPeersRecreateRemotesAndCollectPeersTask(), 0L, 1000L,
				// TODO config.peerPingInterval
				TimeUnit.MILLISECONDS);
	}

	/**
	 * Yields information about the peers.
	 * 
	 * @return the peers information
	 */
	public Stream<PeerInfo> get() {
		return peers.getActorsWithPoints().map(entry -> PeerInfos.of(entry.getKey(), entry.getValue(), remotes.containsKey(entry.getKey())));
	}

	/**
	 * Add some peers to the node. Peers might not be added because
	 * they are already present in the node, or because a connection cannot be established
	 * to them, or because they are incompatible with the node. In such cases, the peers are
	 * simply ignored and no exception is thrown.
	 * 
	 * @param peers the peers to add
	 * @param force true if and only if the peers must be added also when the maximal number of peers
	 *              for the node has been reached
	 * @param whisper true if and only if the added peers must be whispered to all peers
	 *                after the addition
	 */
	public void add(Stream<Peer> peers, boolean force, boolean whisper) {
		var added = peers
			.parallel()
			.distinct()
			.filter(not(this.peers::contains))
			.filter(peer -> addNoException(peer, force))
			.toArray(Peer[]::new);
	
		if (added.length > 0) // just to avoid useless events
			node.submit(new PeersAddedEvent(Stream.of(added), whisper));
	}

	/**
	 * Adds the given peer. This might fail because
	 * the peer is already present in the node, or because a connection to the peer
	 * cannot be established, or because the peer is incompatible with the node.
	 * 
	 * @param peer the peer to add
	 * @param force true if the peer must be added also if the maximum number of peers for the node has been reached
	 * @param whisper true if and only if the added peer must be whispered to all peers after the addition
	 * @return true if and only if the peer has been added
	 * @throws IOException if a connection to the peer cannot be established
	 * @throws IncompatiblePeerException if the version of {@code peer} is incompatible with that of the node
	 * @throws DatabaseException if the database is corrupted
	 * @throws TimeoutException if the addition does not complete in time
	 * @throws InterruptedException if the current thread is interrupted while waiting for the addition to complete
	 */
	public boolean add(Peer peer, boolean force, boolean whisper) throws TimeoutException, InterruptedException, IOException, IncompatiblePeerException, DatabaseException {
		boolean result = check(TimeoutException.class,
				 InterruptedException.class,
				 IOException.class,
				 IncompatiblePeerException.class,
				 DatabaseException.class,
				 () -> peers.add(peer, force));

		if (result)
			node.submit(new PeersAddedEvent(Stream.of(peer), whisper));

		return result;
	}
	
	/**
	 * Removes a peer.
	 * 
	 * @param peer the peer to remove
	 * @return true if and only if the peer has been removed
	 * @throws DatabaseException if the database is corrupted
	 */
	public boolean remove(Peer peer) throws DatabaseException {
		return check(DatabaseException.class, () -> peers.remove(peer));
	}

	/**
	 * Determines if this container contains a given peer.
	 * 
	 * @param peer the peer
	 * @return true if and only if this container contains {@code peer}
	 */
	public boolean contains(Peer peer) {
		return peers.contains(peer);
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
		LOGGER.info("peers: got whispered peers " + SanitizedStrings.of(message.getPeers()));
		
		if (tryToAdd)
			// we check if this node needs any of the whispered peers
			addWhisperedAsync(message.getPeers());
	
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
		LOGGER.info("peers: got whispered block " + message.getBlock().getHexHash(config.getHashingForBlocks()));
		
		// in any case, we forward the message to our peers
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
	public void close() throws IOException, InterruptedException {
		IOException ioException = null;
		InterruptedException interruptedException = null;

		synchronized (lock) {
			for (var entry: remotes.entrySet()) {
				try {
					deletePeerWithExceptions(entry.getKey(), entry.getValue());
				}
				catch (IOException e) {
					ioException = e;
				}
				catch (InterruptedException e) {
					interruptedException = e;
				}
			}
		}

		if (ioException != null)
			throw ioException;
		else if (interruptedException != null)
			throw interruptedException;
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
	 * Add the given peers to the node, asynchronously. Peers might not be added because there
	 * are already enough peers, or because a connection cannot be established to them,
	 * or because they are incompatible with the node. In such cases, this method
	 * does not add the peer and nothing happens.
	 * 
	 * @param peers the peers to add
	 */
	private void addWhisperedAsync(Stream<Peer> peers) {
		if (this.peers.getElements().count() < config.maxPeers) {
			var usefulToAdd = peers.distinct()
					.filter(not(this.peers::contains))
					.toArray(Peer[]::new);

			if (usefulToAdd.length > 0)
				node.submit(new AddWhisperedPeersTask(usefulToAdd));
		}
	}

	/**
	 * A task that adds whispered peers to the node.
	 */
	public class AddWhisperedPeersTask implements Task {
		private final Peer[] toAdd;

		private AddWhisperedPeersTask(Peer[] toAdd) {
			this.toAdd = toAdd;
		}

		@Override
		public String toString() {
			return "addition of whispered peers " + SanitizedStrings.of(Stream.of(toAdd));
		}

		@Override
		public void body() {
			add(Stream.of(toAdd), false, false);
		}

		@Override
		public String logPrefix() {
			return "peers: ";
		}
	}

	/**
	 * Adds the given peer, if possible. This might fail if the peer was already
	 * present, or a connection to the peer cannot be established, or the peer
	 * is incompatible with the node. In such cases, this method just ignores
	 * the addition and nothing happens.
	 * 
	 * @param peer the peer to add
	 * @param force true if and only if the addition must be performed also if
	 *              the maximal number of peers for the node has been reached
	 * @return true if and only if the peer has been added
	 */
	private boolean addNoException(Peer peer, boolean force) {
		try {
			// we do not spawn an event since we will spawn one at the end for all peers
			return check(TimeoutException.class,
					 InterruptedException.class,
					 IOException.class,
					 IncompatiblePeerException.class,
					 DatabaseException.class,
					 () -> peers.add(peer, force));
		}
		catch (InterruptedException e) {
			LOGGER.log(Level.WARNING, "peers: addition of " + peer + " as a peer interrupted");
			Thread.currentThread().interrupt();
			return false;
		}
		catch (IncompatiblePeerException | DatabaseException | IOException | TimeoutException e) {
			return false;
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
		public void body() throws DatabaseException {
			if (whisper)
				node.whisper(WhisperPeersMessages.of(getPeers(), UUID.randomUUID().toString()), _whisperer -> false);

			// if the blockchain is empty, the addition of a new peer might be the right moment for attempting a synchronization
			if (node.getDatabase().getHeadHash().isEmpty())
				node.getBlockchain().startSynchronization();
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
		public void body() {
			var allPeers = peers.getElements().parallel().flatMap(this::pingPeerRecreateRemoteAndCollectPeers);
			add(allPeers, false, true);
		}

		@Override
		public String logPrefix() {
			return "peers: ";
		}

		@Override
		public String toString() {
			return "pinging to all peers to create missing remotes and collect their peers";
		}

		private Stream<Peer> pingPeerRecreateRemoteAndCollectPeers(Peer peer) {
			return getRemote(peer).or(() -> tryToCreateRemote(peer))
				.map(remote -> askForPeers(peer, remote))
				.orElse(Stream.empty());
		}

		private Stream<Peer> askForPeers(Peer peer, RemotePublicNode remote) {
			try {
				Stream<PeerInfo> infos = remote.getPeerInfos();
				pardonBecauseReachable(peer);

				if (peers.getElements().count() >= config.maxPeers)
					return Stream.empty();
				else
					return infos.filter(PeerInfo::isConnected).map(PeerInfo::getPeer).filter(not(peers::contains));
			}
			catch (InterruptedException e) {
				LOGGER.log(Level.WARNING, logPrefix() + "interrupted while asking the peers of " + peer + ": " + e.getMessage());
				Thread.currentThread().interrupt();
				return Stream.empty();
			}
			catch (TimeoutException | ClosedNodeException e) {
				LOGGER.log(Level.WARNING, logPrefix() + "cannot contact peer " + peer + ": " + e.getMessage());
				punishBecauseUnreachable(peer);
				return Stream.empty();
			}
		}
	}

	public void punishBecauseUnreachable(Peer peer) {
		try {
			check(DatabaseException.class, () -> peers.punish(peer, config.peerPunishmentForUnreachable));
		}
		catch (DatabaseException e) {
			LOGGER.log(Level.SEVERE, "peers: cannot reduce the points of " + peer, e);
		}
	}

	public void pardonBecauseReachable(Peer peer) {
		peers.pardon(peer, config.peerPunishmentForUnreachable);
	}

	private void openConnectionToPeers() {
		get().parallel().filter(not(PeerInfo::isConnected)).map(PeerInfo::getPeer).forEach(this::tryToCreateRemote);
	}

	private boolean onAdd(Peer peer, boolean force) {
		RemotePublicNode remote = null;

		try {
			// optimization: this avoids opening a remote for an already existing peer
			// or trying to add a peer if there are already enough peers for this node
			if (peers.contains(peer) || (!force && peers.getElements().count() >= config.maxPeers))
				return false;

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
		catch (InterruptedException e) {
			LOGGER.log(Level.WARNING, "peers: interrupted while adding " + peer + " to the peers: " + e.getMessage());
			Thread.currentThread().interrupt();
			throw new UncheckedException(e);
		}
		catch (IOException | TimeoutException | ClosedNodeException | DatabaseException | IncompatiblePeerException e) {
			LOGGER.log(Level.SEVERE, "peers: cannot add peer " + peer + ": " + e.getMessage());
			throw new UncheckedException(e);
		}
		finally {
			deletePeer(peer, remote);
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
		catch (DatabaseException e) {
			LOGGER.log(Level.SEVERE, "peers: cannot remove peer " + peer, e);
			throw new UncheckedException(e);
		}
	}

	private Optional<RemotePublicNode> tryToCreateRemote(Peer peer) {
		RemotePublicNode remote = null;
	
		try {
			LOGGER.info("peers: trying to create a connection to peer " + peer);
			remote = openRemote(peer);
			RemotePublicNode remoteCopy = remote;
			long timeDifference = ensurePeerIsCompatible(remote);
	
			synchronized (lock) {
				// we check if the peer is actually contained in the set of peers,
				// since it might have been removed in the meanwhile and we not not
				// want to store remotes for peers not in the set of peers of this object
				if (peers.contains(peer)) {
					storePeer(peer, remote, timeDifference);
					remote = null; // so that it won't be closed in the finally clause
				}
			}

			return Optional.of(remoteCopy);
		}
		catch (IncompatiblePeerException e) {
			LOGGER.log(Level.WARNING, "peers: " + e.getMessage());
			try {
				remove(peer);
			}
			catch (DatabaseException e1) {
				LOGGER.log(Level.SEVERE, "peers: cannot remove " + peer + " from the database", e);
			}
		}
		catch (InterruptedException e) {
			LOGGER.log(Level.WARNING, "peers: interrupted while creating a remote for " + peer + ": " + e.getMessage());
			Thread.currentThread().interrupt();
		}
		catch (IOException | TimeoutException | ClosedNodeException e) {
			LOGGER.log(Level.WARNING, "peers: cannot contact peer " + peer + ": " + e.getMessage());
			punishBecauseUnreachable(peer);
		}
		finally {
			deletePeer(peer, remote);
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
	 * @throws IncompatiblePeerException if the peers are incompatible
	 * @throws TimeoutException if the peer could not be contacted through the {@code remote}
	 * @throws InterruptedException if the connection to the peer though {@code remote} was interrupted
	 * @throws ClosedNodeException if the peer is closed
	 */
	private long ensurePeerIsCompatible(RemotePublicNode remote) throws IncompatiblePeerException, TimeoutException, InterruptedException, ClosedNodeException {
		NodeInfo info1 = remote.getInfo();
		NodeInfo info2 = node.getInfo();

		long timeDifference = ChronoUnit.MILLIS.between(info2.getLocalDateTimeUTC(), info1.getLocalDateTimeUTC());
		if (Math.abs(timeDifference) > config.peerMaxTimeDifference)
			throw new IncompatiblePeerException("the time of the peer is more than " + config.peerMaxTimeDifference + "ms away from the time of this node");
			
		UUID uuid1 = info1.getUUID();
		if (uuid1.equals(info2.getUUID()))
			throw new IncompatiblePeerException("a peer cannot be added as a peer of itself: same UUID " + uuid1);

		var version1 = info1.getVersion();
		var version2 = info2.getVersion();
		if (!version1.canWorkWith(version2))
			throw new IncompatiblePeerException("peer version " + version1 + " is incompatible with this node's version " + version2);

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
		public void body() {
			node.whisperItself();
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
	 */
	private void peerDisconnected(RemotePublicNode remote, Peer peer) {
		deletePeer(peer, remote);
		punishBecauseUnreachable(peer);
		node.submit(new PeerDisconnectedEvent(peer));
	}

	/**
	 * An event fired to signal that a peer of the node have been disconnected.
	 */
	public static class PeerDisconnectedEvent implements Event {
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
		public void body() {}

		@Override
		public String logPrefix() {
			return "peers: ";
		}
	}

	private void deletePeer(Peer peer, RemotePublicNode remote) {
		try {
			deletePeerWithExceptions(peer, remote);
		}
		catch (IOException | InterruptedException e) {
			LOGGER.log(Level.SEVERE, "peers: cannot close the connection to peer " + peer, e);
		}
	}

	private void deletePeerWithExceptions(Peer peer, RemotePublicNode remote) throws IOException, InterruptedException {
		if (remote != null) {
			remote.unbindWhisperer(node);
			remotes.remove(peer);
			remote.close();
			timeDifferences.remove(peer);
			LOGGER.info("peers: closed connection to peer " + peer);
		}
	}
}
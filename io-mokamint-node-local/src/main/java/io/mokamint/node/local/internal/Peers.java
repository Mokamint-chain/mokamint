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

import static java.util.function.Predicate.not;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.exceptions.CheckSupplier;
import io.hotmoka.exceptions.UncheckFunction;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.SanitizedStrings;
import io.mokamint.node.Versions;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.api.Version;
import io.mokamint.node.api.Whispered;
import io.mokamint.node.api.Whisperer;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.remote.RemotePublicNodes;
import io.mokamint.node.remote.api.RemotePublicNode;
import jakarta.websocket.DeploymentException;

/**
 * The set of peers of a local node. This class guarantees that,
 * if a peer has a remote, then it is in the database of peers
 * (but the converse might not hold since, for instance, a peer
 * might be currently unreachable).
 */
@ThreadSafe
public class Peers implements AutoCloseable {

	/**
	 * The node having these peers.
	 */
	private final LocalNodeImpl node;

	/**
	 * The configuration of the node.
	 */
	private final LocalNodeConfig config;

	/**
	 * The database containing the peers.
	 */
	private final PeersDatabase db;

	/**
	 * The UUID of the node having these peers.
	 */
	private final UUID uuid;

	/**
	 * The version of the node having these peers.
	 */
	private final Version version;

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

	private final static Logger LOGGER = Logger.getLogger(Peers.class.getName());

	/**
	 * Creates the set of peers of a local node. If a database of peers already exists,
	 * this constructor will recover the peers saved in the database and attempt
	 * to create a connection to them. In any case, it adds the seeds of the node
	 * and attempts a connection to them.
	 * 
	 * @param node the node having these peers
	 * @throws DatabaseException if the database is corrupted
	 * @throws IOException if an I/O error occurs
	 */
	public Peers(LocalNodeImpl node) throws DatabaseException, IOException {
		this.node = node;
		this.config = node.getConfig();
		this.db = new PeersDatabase(node);
		this.version = Versions.current();

		try {
			this.uuid = db.getUUID();
			this.peers = new PunishableSet<>(db.getPeers(), config.getPeerInitialPoints());
		}
		catch (ClosedDatabaseException e) {
			LOGGER.log(Level.SEVERE, "node: unexpected exception", e);
			throw new RuntimeException("Unexpected exception", e);
		}
	}

	/**
	 * Recovers the peers saved in the database and attempt
	 * to create a connection to them. In any case, it adds the seeds of the node
	 * and attempts a connection to them.
	 * 
	 * @throws DatabaseException if the database is corrupted
	 * @throws IOException if an I/O error occurs
	 * @throws InterruptedException if the thread is interrupted while contacting the peers
	 * @throws ClosedNodeException if the node is closed
	 * @throws ClosedDatabaseException if the database of peers is already closed
	 */
	public void reconnectToSeedsAndPreviousPeers() throws DatabaseException, IOException, ClosedNodeException, InterruptedException, ClosedDatabaseException {
		Set<Peer> seeds = config.getSeeds().map(io.mokamint.node.Peers::of).collect(Collectors.toSet());
		tryToReconnectOrAdd(Stream.concat(peers.getElements(), seeds.stream()), seeds::contains);
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
	 * Yields information about the node having these peers, extracted from the database.
	 * 
	 * @return the node information
	 */
	public NodeInfo getNodeInfo() {
		return NodeInfos.of(version, uuid, LocalDateTime.now(ZoneId.of("UTC")));
	}

	/**
	 * Adds the given peer. This might fail because
	 * the peer is already present in the node, or because a connection to the peer
	 * cannot be established, or because the peer is incompatible with the node.
	 * If the peer was present but was disconnected, it tries to reconnect it.
	 * 
	 * @param peer the peer to add
	 * @return the information about the peer; this is empty if the peer has not been added nor reconnected,
	 *         for instance, because it was already present or a maximum number of peers has been already reached
	 * @throws IOException if an I/O error occurs
	 * @throws PeerRejectedException if the addition of {@code peer} was rejected for some reason
	 * @throws DatabaseException if the database of the node is corrupted
	 * @throws TimeoutException if the addition does not complete in time
	 * @throws InterruptedException if the current thread is interrupted while waiting for the addition to complete
	 * @throws ClosedNodeException if the node is closed
	 * @throws ClosedDatabaseException if the database of the node is closed
	 */
	public Optional<PeerInfo> add(Peer peer) throws TimeoutException, InterruptedException, IOException, PeerRejectedException, DatabaseException, ClosedNodeException, ClosedDatabaseException {
		if (tryToReconnectOrAdd(peer, true))
			return Optional.of(PeerInfos.of(peer, config.getPeerInitialPoints(), true));
		else
			return Optional.empty();
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
		boolean removed;

		synchronized (lock) {
			if (removed = peers.remove(peer)) {
				db.remove(peer);
				disconnect(peer, remotes.get(peer));
			}
		}
		
		if (removed)
			node.onRemoved(peer);

		return removed;
	}

	/**
	 * Whispers an object to this container of peers. It forwards it to the peers in this container.
	 * 
	 * @param whispered the whispered objects
	 * @param seen the whisperers already seen during whispering
	 */
	public void whisper(Whispered whispered, Predicate<Whisperer> seen, String description) {
		remotes.values().forEach(remote -> remote.whisper(whispered, seen, description)); // we forward the message to our peers
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
						disconnect(entry.getKey(), entry.getValue());
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
	 * Pings all peers, tries to recreate their remote (if missing)
	 * and collects their peers, in case they might be useful for the node.
	 */
	public void pingAllRecreateRemotesAndAddTheirPeers() throws ClosedNodeException, DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
		var allPeers = CheckSupplier.check(ClosedNodeException.class, DatabaseException.class, ClosedDatabaseException.class, InterruptedException.class, IOException.class, () ->
			peers.getElements()
				.flatMap(UncheckFunction.uncheck(this::pingPeerRecreateRemoteAndCollectPeers))
				.toArray(Peer[]::new)
		);

		tryToReconnectOrAdd(Stream.of(allPeers), _peer -> false);
	}

	public void punishBecauseUnreachable(Peer peer) throws DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
		long lost = config.getPeerPunishmentForUnreachable();
		boolean removed;
		
		synchronized (lock) {
			if (removed = peers.punish(peer, lost)) {
				db.remove(peer);
				disconnect(peer, remotes.get(peer));
			}
		}
		
		if (removed)
			node.onRemoved(peer);
	
		LOGGER.warning("peers: " + SanitizedStrings.of(peer) + " lost " + lost + " points because it is unreachable");
	}

	private void pardonBecauseReachable(Peer peer) {
		long gained = peers.pardon(peer, config.getPeerPunishmentForUnreachable());
		if (gained > 0L)
			LOGGER.info("peers: " + SanitizedStrings.of(peer) + " gained " + gained + " points because it is reachable");
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
			remote = tryToCreateRemote(peer);

		return remote.isPresent() ? askForPeers(peer, remote.get()) : Stream.empty();
	}

	private Stream<Peer> askForPeers(Peer peer, RemotePublicNode remote) throws InterruptedException, DatabaseException, ClosedDatabaseException, IOException {
		if (peers.getElements().count() < config.getMaxPeers()) { // optimization
			try {
				var peerInfos = remote.getPeerInfos();
				pardonBecauseReachable(peer);
				return peerInfos.filter(PeerInfo::isConnected)
						.map(PeerInfo::getPeer)
						.filter(not(peers::contains));
			}
			catch (TimeoutException | ClosedNodeException e) {
				LOGGER.log(Level.WARNING, "peers: cannot contact " + SanitizedStrings.of(peer) + ": " + e.getMessage());
				punishBecauseUnreachable(peer);
			}
		}

		return Stream.empty();
	}

	/**
	 * If the peer is not in this container, adds it, if possible.
	 * This might fail if the peer was already
	 * present, or a connection to the peer cannot be established, or the peer
	 * is incompatible with the node. In such cases, this method just ignores
	 * the addition and nothing happens. If the peer was already in this container,
	 * but was disconnected, this method tries to reconnect to it.
	 * 
	 * @param peer the peer to add
	 * @param force true if and only if the addition must be performed also if
	 *              the maximal number of peers for the node has been reached
	 * @return true if and only if the peer has been added or reconnected
	 * @throws InterruptedException if the execution was interrupted
	 * @throws ClosedDatabaseException if the database of {@link #node} is closed
	 * @throws DatabaseException if the database of {@link #node} is corrupted
	 * @throws ClosedNodeException if {@link #node} is closed
	 * @throws IOException if an I/O error occurred
	 */
	private boolean tryToReconnectOrAdd(Peer peer, boolean force) throws ClosedNodeException, DatabaseException, ClosedDatabaseException, InterruptedException, IOException, PeerRejectedException, TimeoutException {
		if (peers.contains(peer))
			return remotes.get(peer) == null && tryToCreateRemote(peer).isPresent();
		else
			return add(peer, force);
	}

	/**
	 * Add some peers to the node. If the peer was already present but was disconnected,
	 * it tries to open a connection to the peer. Peers might not be added because
	 * they are already present in the node, or because a connection cannot be established
	 * to them, or because they are incompatible with the node. In such cases, the peers are
	 * simply ignored and no exception is thrown.
	 * 
	 * @param peers the peers to add
	 * @param force true if and only if a peer must be added also when the maximal number of peers
	 *              for the node has been reached
	 * @throws InterruptedException if the execution was interrupted
	 * @throws ClosedDatabaseException if the database of {@link #node} is closed
	 * @throws DatabaseException if the database of {@link #node} is corrupted
	 * @throws ClosedNodeException if {@link #node} is closed
	 * @throws IOException if an I/O error occurs
	 */
	private void tryToReconnectOrAdd(Stream<Peer> peers, Predicate<Peer> force) throws ClosedNodeException, DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
		boolean somethingChanged = false;
		for (var peer: peers.distinct().toArray(Peer[]::new)) {
			try {
				somethingChanged |= tryToReconnectOrAdd(peer, force.test(peer));
			}
			catch (PeerRejectedException | IOException | TimeoutException e) {
				// the peer does not answer: never mind
			}
		}

		if (somethingChanged) {
			node.scheduleSynchronization(0L);
			node.scheduleWhisperingOfAllServices();
		}
	}

	private boolean add(Peer peer, boolean force) throws IOException, PeerRejectedException, TimeoutException, InterruptedException, ClosedNodeException, DatabaseException, ClosedDatabaseException {
		if (!force && peers.getElements().count() >= config.getMaxPeers())
			return false;

		RemotePublicNode remote = null;

		try {
			remote = openRemote(peer);
			long timeDifference = ensurePeerIsCompatible(remote);

			synchronized (lock) {
				if (db.add(peer, force) && peers.add(peer)) {
					connect(peer, remote, timeDifference);
					remote = null; // so that it won't be disconnected in the finally clause
				}
				else
					return false;
			}
			
			node.onAdded(peer);

			return true;
		}
		finally {
			disconnect(peer, remote);
		}
	}

	/**
	 * Tries to create a remote for the given peer. This might fail for a few
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
	private Optional<RemotePublicNode> tryToCreateRemote(Peer peer) throws ClosedNodeException, DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
		try {
			LOGGER.info("peers: trying to create a connection to " + SanitizedStrings.of(peer));
			RemotePublicNode remote = null;

			try {
				remote = openRemote(peer);
				RemotePublicNode remoteCopy = remote;
				long timeDifference = ensurePeerIsCompatible(remote);

				synchronized (lock) {
					// we check if the peer is actually contained in the set of peers,
					// since it might have been removed in the meanwhile and we not not
					// want to store remotes for peers not in the set of peers of this object
					if (peers.contains(peer) && remotes.get(peer) == null) {
						connect(peer, remote, timeDifference);
						remote = null; // so that it won't be disconnected in the finally clause
					}
				}

				return Optional.of(remoteCopy);
			}
			finally {
				disconnect(peer, remote);
			}
		}
		catch (IOException | TimeoutException e) {
			LOGGER.log(Level.WARNING, "peers: cannot contact " + SanitizedStrings.of(peer) + ": " + e.getMessage());
			punishBecauseUnreachable(peer);
		}
		catch (PeerRejectedException e) {
			LOGGER.log(Level.WARNING, "peers: " + e.getMessage());
			remove(peer);
		}

		return Optional.empty();
	}

	/**
	 * Checks if the peer whose remote is provided is compatible with {@link #node}.
	 * 
	 * @param remote the remote of the peer
	 * @return the time difference (in milliseconds) between the local time of {@link #node}
	 *         and the local time of the peer (this is positive if the clock of the peer is
	 *         in the future wrt the clock of this node; it is negative in the opposite case)
	 * @throws PeerRejectedException if the peer was rejected for some reason
	 * @throws TimeoutException if the peer could not be contacted through the {@code remote}
	 * @throws InterruptedException if the connection to the peer though {@code remote} was interrupted
	 * @throws ClosedNodeException if {@link #node} is closed
	 * @throws DatabaseException if the database of {@link #node} is corrupted
	 */
	private long ensurePeerIsCompatible(RemotePublicNode remote) throws PeerRejectedException, TimeoutException, InterruptedException, ClosedNodeException, DatabaseException {
		NodeInfo peerInfo;

		try {
			peerInfo = remote.getInfo();
		}
		catch (ClosedNodeException e) {
			// it's the remote peer that is closed, not our node
			throw new PeerRejectedException("The peer is closed", e);
		}

		var nodeInfo = getNodeInfo();

		long timeDifference = ChronoUnit.MILLIS.between(nodeInfo.getLocalDateTimeUTC(), peerInfo.getLocalDateTimeUTC());
		if (Math.abs(timeDifference) > config.getPeerMaxTimeDifference())
			throw new PeerRejectedException("The time of the peer is more than " + config.getPeerMaxTimeDifference() + " ms away from the time of this node");

		var peerUUID = peerInfo.getUUID();
		if (peerUUID.equals(uuid))
			throw new PeerRejectedException("A peer cannot be added as a peer of itself: same UUID " + peerUUID);

		var peerVersion = peerInfo.getVersion();
		var nodeVersion = nodeInfo.getVersion();
		if (!peerVersion.canWorkWith(nodeVersion))
			throw new PeerRejectedException("Peer version " + peerVersion + " is incompatible with this node's version " + nodeVersion);

		ChainInfo peerChainInfo;

		try {
			peerChainInfo = remote.getChainInfo();
		}
		catch (ClosedNodeException e) {
			// it's the remote peer that is closed, not our node
			throw new PeerRejectedException("The peer is closed", e);
		}
		catch (DatabaseException e) {
			// it's the remote peer that has database problems, not our node
			throw new PeerRejectedException("The peer's database is corrupted", e);
		}

		Optional<byte[]> peerGenesisHash = peerChainInfo.getGenesisHash();
		if (peerGenesisHash.isPresent()) {
			var nodeChainInfo = node.getChainInfo();
			Optional<byte[]> nodeGenesisHash = nodeChainInfo.getGenesisHash();
			if (nodeGenesisHash.isPresent() && !Arrays.equals(peerGenesisHash.get(), nodeGenesisHash.get()))
				throw new PeerRejectedException("The peers have distinct genesis blocks");
		}

		return timeDifference;
	}

	private RemotePublicNode openRemote(Peer peer) throws IOException {
		try {
			// -1L: to disable the periodic broadcast of the remote node's services
			return RemotePublicNodes.of(peer.getURI(), config.getPeerTimeout(), -1L, config.getWhisperingMemorySize());
		}
		catch (DeploymentException e) {
			throw new IOException("Cannot deploy a remote connected to " + SanitizedStrings.of(peer), e);  // we consider it as a special case of IOException
		}
	}

	/**
	 * Called when a remote gets closed: it disconnects the peer and punishes it.
	 * 
	 * @param remote the remote of the peer, that is being closed
	 * @param peer the peer having the {@code remote}
	 * @throws InterruptedException if the closure operation has been interrupted
	 * @throws IOException if an I/O exceptions occurs while closing the remote of the peer
	 */
	private void remoteHasBeenClosed(RemotePublicNode remote, Peer peer) throws InterruptedException, IOException {
		disconnect(peer, remote);
	
		try {
			punishBecauseUnreachable(peer);
		}
		catch (ClosedDatabaseException | DatabaseException e) {
			throw new IOException(e);
		}
	}

	private void connect(Peer peer, RemotePublicNode remote, long timeDifference) {
		remotes.put(peer, remote);
		timeDifferences.put(peer, timeDifference);
		remote.bindWhisperer(node);
		// if the remote gets closed, then it will get unlinked from the map of remotes
		remote.addOnClosedHandler(() -> remoteHasBeenClosed(remote, peer));
		node.onConnected(peer);
	}

	private void disconnect(Peer peer, RemotePublicNode remote) throws InterruptedException, IOException {
		if (remote != null) {
			remote.unbindWhisperer(node);
			remotes.remove(peer);
			timeDifferences.remove(peer);
			remote.close();
			node.onDisconnected(peer);
		}
	}
}
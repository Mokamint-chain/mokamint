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
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
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

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.Versions;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.api.Version;
import io.mokamint.node.api.WhisperMessage;
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
public class PeersSet implements AutoCloseable {

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
	 * Lock used to guarantee that if there is a peer among the keys of the {@link #remotes}
	 * map or among the keys of the {@link #timeDifferences} map then the peer is
	 * in {@link #peers} (the converse might well not hold).
	 */
	private final Object lock = new Object();

	/**
	 * The peers of the node. These are all guaranteed to be in {@link #db}.
	 */
	private final PunishableSet<Peer> peers;

	/**
	 * The remote nodes connected to each peer of {@link #node}. The keys in this map
	 * are all guaranteed to be in {@link #peers} and consequently in {@link #db}.
	 */
	private final ConcurrentMap<Peer, RemotePublicNode> remotes = new ConcurrentHashMap<>();

	/**
	 * The time difference (in milliseconds) between {@link #node} and each of its peers.
	 * The keys in this map are all guaranteed to be in {@link #peers} and consequently in {@link #db}.
	 */
	private final ConcurrentMap<Peer, Long> timeDifferences = new ConcurrentHashMap<>();

	/**
	 * A set of URIs that have been banned: attempts to connect to peers with these URIs will be rejected.
	 */
	private final Set<URI> bannedURIs = ConcurrentHashMap.newKeySet();

	private final static Logger LOGGER = Logger.getLogger(PeersSet.class.getName());

	/**
	 * Creates the set of peers of a local node. If a database of peers already exists,
	 * this constructor will recover the peers saved in the database and attempt
	 * to create a connection to them. In any case, it adds the seeds of the node
	 * and attempts a connection to them.
	 * 
	 * @param node the node
	 * @throws NodeException if the node is misbehaving
	 * @throws InterruptedException if the current thread gets interrupted while contacting the peers
	 */
	public PeersSet(LocalNodeImpl node) throws NodeException, InterruptedException {
		this.node = node;
		this.config = node.getConfig();
		this.db = new PeersDatabase(node);

		try {
			this.version = Versions.current();
		}
		catch (IOException e) {
			throw new NodeException(e);
		}

		this.uuid = db.getUUID();
		this.peers = new PunishableSet<>(db.getPeers(), config.getPeerInitialPoints());
		Set<Peer> seeds = config.getSeeds().map(Peers::of).collect(Collectors.toSet());
		var all = new HashSet<>(seeds);
		all.addAll(peers.getElements().collect(Collectors.toSet()));
		tryToReconnectOrAdd(all, seeds::contains);
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
	 * @throws PeerRejectedException if the addition of {@code peer} was rejected for some reason
	 * @throws IOException if the connection to the peer failed
	 * @throws TimeoutException if the addition does not complete in time
	 * @throws InterruptedException if the current thread gets interrupted while waiting for the addition to complete
	 * @throws NodeException if the node is misbehaving
	 */
	public Optional<PeerInfo> add(Peer peer) throws TimeoutException, IOException, InterruptedException, PeerRejectedException, NodeException {
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
	 * @throws NodeException if the node is misbehaving
	 * @throws InterruptedException if the current thread gets interrupted
	 */
	public boolean remove(Peer peer) throws NodeException, InterruptedException {
		boolean removed;

		synchronized (lock) {
			if (removed = peers.remove(peer)) {
				disconnect(peer, remotes.get(peer));
				db.remove(peer);
			}
		}

		if (removed)
			node.onRemoved(peer);

		return removed;
	}

	/**
	 * Bans a peer. It gets removed and it will not be allowed to be added anymore.
	 * 
	 * @param peer the peer to ban
	 * @return true if and only if the peer has been removed
	 * @throws NodeException if the node is misbehaving
	 * @throws InterruptedException if the operation was interrupted
	 */
	public boolean ban(Peer peer) throws NodeException, InterruptedException {
		bannedURIs.add(peer.getURI());
		LOGGER.warning("peers: " + peer.toStringSanitized() + " has been banned");

		return remove(peer);
	}

	/**
	 * Whispers a message to this container of peers. It forwards it to the peers in this container.
	 * 
	 * @param message the message to whisper
	 * @param seen the whisperers already seen during whispering
	 */
	public void whisper(WhisperMessage<?> message, Predicate<Whisperer> seen, String description) {
		remotes.forEach((_peer, remote) -> remote.whisper(message, seen, description)); // we forward the message to all peers
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
	public void close() throws InterruptedException, NodeException {
		try {
			closeRemotes(remotes.entrySet().iterator());
		}
		finally {
			db.close();
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
	 * 
	 * @return true if some peer has been added or reconnected
	 */
	public boolean pingAllRecreateRemotesAndAddTheirPeers() throws NodeException, InterruptedException {
		var all = new HashSet<Peer>();
		for (var peer: peers.getElements().collect(Collectors.toSet()))
			pingPeerRecreateRemoteAndCollectUnknownPeers(peer, all);

		return tryToReconnectOrAdd(all, _peer -> false);
	}

	public void punishBecauseUnreachable(Peer peer) throws NodeException, InterruptedException {
		if (peers.contains(peer)) { // just for optimization and for keeping the logs clean
			long lost = config.getPeerPunishmentForUnreachable();
			boolean removed;

			synchronized (lock) {
				if (removed = peers.punish(peer, lost)) {
					disconnect(peer, remotes.get(peer));
					db.remove(peer);
				}
			}

			if (removed)
				node.onRemoved(peer);

			LOGGER.warning("peers: " + peer.toStringSanitized() + " lost " + lost + " points because it is unreachable");
		}
	}

	private void closeRemotes(Iterator<Entry<Peer, RemotePublicNode>> remotes) throws InterruptedException {
		if (remotes.hasNext()) {
			try {
				var entry = remotes.next();
				disconnect(entry.getKey(), entry.getValue());	
			}
			finally {
				closeRemotes(remotes);
			}
		}
	}

	private void pardonBecauseReachable(Peer peer) {
		long gained = peers.pardon(peer, config.getPeerPunishmentForUnreachable());
		if (gained > 0L)
			LOGGER.info("peers: " + peer.toStringSanitized() + " gained " + gained + " points because it is reachable");
	}

	/**
	 * Contacts the peer and, if successful, collects all its peers not known to the node
	 * and adds them to the given container.
	 * 
	 * @param peer the peer to contact
	 * @param container the container where the peers get added
	 * @throws NodeException if {@link #node} could not complete the operation
	 * @throws InterruptedException if the execution was interrupted while waiting to establish a connection to the peer
	 */
	private void pingPeerRecreateRemoteAndCollectUnknownPeers(Peer peer, Set<Peer> container) throws NodeException, InterruptedException {
		Optional<RemotePublicNode> remote = getRemote(peer);
		if (remote.isEmpty())
			remote = tryToCreateRemote(peer);

		if (remote.isPresent())
			collectUnknownPeers(peer, remote.get(), container);
	}

	private void collectUnknownPeers(Peer peer, RemotePublicNode remote, Set<Peer> container) throws InterruptedException, NodeException {
		if (peers.size() < config.getMaxPeers()) { // optimization
			try {
				var peerInfos = remote.getPeerInfos();
				pardonBecauseReachable(peer);
				peerInfos.filter(PeerInfo::isConnected)
					.map(PeerInfo::getPeer)
					.filter(not(peers::contains))
					.forEach(container::add);
			}
			catch (TimeoutException | NodeException e) {
				LOGGER.log(Level.WARNING, "peers: cannot contact " + peer.toStringSanitized() + ": " + e.getMessage());
				punishBecauseUnreachable(peer);
			}
		}
	}

	/**
	 * If the peer is not in this container, adds it, if possible.
	 * This might fail if the peer was already present or a maximal
	 * number of peers has been reached. In such cases, this method just ignores
	 * the addition and returns false.
	 * If a connection to the peer cannot be established, or the peer
	 * is incompatible with the node, an exception is thrown.
	 * If the peer was already in this container,
	 * but was disconnected, this method tries to reconnect to it.
	 * 
	 * @param peer the peer to add
	 * @param force true if and only if the addition must be performed also if
	 *              the maximal number of peers for the node has been reached
	 * @return true if and only if the peer has been added or reconnected
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws NodeException if the node is misbehaving
	 * @throws PeerRejectedException if the peer was rejected for some reason
	 * @throws TimeoutException if the peer did not answer in time
	 * @throws IOException if the connection to the peer failed
	 */
	private boolean tryToReconnectOrAdd(Peer peer, boolean force) throws NodeException, InterruptedException, PeerRejectedException, IOException, TimeoutException {
		if (bannedURIs.contains(peer.getURI()))
			return false;
		else if (peers.contains(peer))
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
	 * @returns true if and only if at least a peer has been added or reconnected
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws NodeException if the node is misbehaving
	 */
	private boolean tryToReconnectOrAdd(Set<Peer> peers, Predicate<Peer> force) throws NodeException, InterruptedException {
		boolean somethingChanged = false;
		for (var peer: peers) {
			try {
				somethingChanged |= tryToReconnectOrAdd(peer, force.test(peer));
			}
			catch (PeerRejectedException | IOException | TimeoutException e) {
				LOGGER.warning("peers: cannot connect to " + peer.toStringSanitized() + ": " + e.getMessage());
			}
		}

		return somethingChanged;
	}

	private boolean add(Peer peer, boolean force) throws IOException, PeerRejectedException, TimeoutException, InterruptedException, NodeException {
		if (!force && peers.size() >= config.getMaxPeers())
			return false;

		boolean connected = false;
		RemotePublicNode remote = null;

		try {
			remote = openRemote(peer);
			long timeDifference = ensurePeerIsCompatible(remote);

			synchronized (lock) {
				if (db.add(peer, force) && peers.add(peer)) {
					connect(peer, remote, timeDifference);
					connected = true;
				}
				else
					return false;
			}

			node.onConnected(peer);
			node.onAdded(peer);

			return true;
		}
		finally {
			if (!connected)
				free(peer, remote);
		}
	}

	/**
	 * Tries to create a remote for the given peer. This might fail for a few
	 * reasons, for instance, because the peer is incompatible with {@link #node}
	 * or because the peer is closed or the connection timed out.
	 * 
	 * @param peer the peer
	 * @return the remote, if it was possible to create it
	 * @throws NodeException if {@link #node} could not complete the operation
	 * @throws InterruptedException if the execution was interrupted while waiting to establish a connection to the peer
	 */
	private Optional<RemotePublicNode> tryToCreateRemote(Peer peer) throws NodeException, InterruptedException {
		try {
			LOGGER.info("peers: trying to create a connection to " + peer.toStringSanitized());
			boolean connected = false;
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
						connected = true;
					}
					else
						return Optional.of(remote);
				}

				node.onConnected(peer);

				return Optional.of(remoteCopy);
			}
			finally {
				if (!connected)
					free(peer, remote);
			}
		}
		catch (IOException | TimeoutException e) {
			LOGGER.log(Level.WARNING, "peers: cannot contact " + peer.toStringSanitized() + ": " + e.getMessage());
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
	 * @throws NodeException if {@link #node} could not complete the operation
	 */
	private long ensurePeerIsCompatible(RemotePublicNode remote) throws PeerRejectedException, NodeException, TimeoutException, InterruptedException {
		NodeInfo peerInfo;

		try {
			peerInfo = remote.getInfo();
		}
		catch (NodeException e) {
			// it's the remote peer that misbehaves, not our node
			throw new PeerRejectedException("The peer is misbehaving", e);
		}

		var nodeInfo = getNodeInfo();

		long timeDifference = ChronoUnit.MILLIS.between(nodeInfo.getLocalDateTimeUTC(), peerInfo.getLocalDateTimeUTC());
		if (Math.abs(timeDifference) > config.getPeerMaxTimeDifference())
			throw new PeerRejectedException("The time of the peer is more than " + config.getPeerMaxTimeDifference() + " ms away from the time of this node");

		var peerUUID = peerInfo.getUUID();
		if (peerUUID.equals(uuid))
			throw new PeerRejectedException("A peer cannot be added as a peer of itself: same UUID " + peerUUID);

		var peerVersion = peerInfo.getVersion();
		if (!peerVersion.canWorkWith(version))
			throw new PeerRejectedException("Peer version " + peerVersion + " is incompatible with this node's version " + version);

		ChainInfo peerChainInfo;

		try {
			peerChainInfo = remote.getChainInfo();
		}
		catch (NodeException e) {
			// it's the remote peer that misbehaves, not our node
			throw new PeerRejectedException("The peer is misbehaving", e);
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
			return RemotePublicNodes.of(peer.getURI(), config.getPeerTimeout(), -1, config.getWhisperingMemorySize());
		}
		catch (DeploymentException e) {
			throw new IOException("Cannot deploy a remote connected to " + peer.toStringSanitized(), e);  // we consider it as a special case of IOException
		}
	}

	/**
	 * Called when a remote gets closed: it disconnects the peer and punishes it.
	 * 
	 * @param remote the remote of the peer, that is being closed
	 * @param peer the peer having the {@code remote}
	 * @throws InterruptedException if the closure operation has been interrupted
	 * @throws NodeException if the node is misbehaving
	 */
	private void onRemoteClosed(RemotePublicNode remote, Peer peer) throws InterruptedException, NodeException {
		disconnect(peer, remote);
		punishBecauseUnreachable(peer);
	}

	@GuardedBy("this.lock")
	private void connect(Peer peer, RemotePublicNode remote, long timeDifference) {
		remotes.put(peer, remote);
		timeDifferences.put(peer, timeDifference);
		remote.bindWhisperer(node);
		// if the remote gets closed, then it will get unlinked from the map of remotes
		remote.addOnCloseHandler(() -> onRemoteClosed(remote, peer));
	}

	private void disconnect(Peer peer, RemotePublicNode remote) throws InterruptedException {
		if (remote != null) {
			remote.unbindWhisperer(node);
			remotes.remove(peer);
			timeDifferences.remove(peer);

			try {
				remote.close();
				node.onDisconnected(peer);
			}
			catch (NodeException e) { // it's the remote that misbehaves, not our node
				LOGGER.warning("cannot close the remote to peer " + peer.toStringSanitized() + ": " + e.getMessage());
			}
		}
	}

	private void free(Peer peer, RemotePublicNode remote) throws InterruptedException {
		try {
			remote.close();
		}
		catch (NodeException e) { // it's the remote that misbehaves, not our node
			LOGGER.warning("cannot close the remote to peer " + peer.toStringSanitized() + ": " + e.getMessage());
		}
	}
}
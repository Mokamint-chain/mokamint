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

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.exceptions.UncheckedException;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.local.Config;
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
	 * The db of the node.
	 */
	private final Database db;

	/**
	 * The peers of the node.
	 */
	private final PunishableSet<Peer> peers;

	/**
	 * Lock used to guarantee that the peers in the database are
	 * consistent with the peers having a remote in this container.
	 */
	private final Object lock = new Object();

	/**
	 * The remote nodes connected to each peer.
	 */
	private final ConcurrentMap<Peer, RemotePublicNode> remotes = new ConcurrentHashMap<>();

	/**
	 * The listener to call when some peers suggests some more peers to add.
	 */
	private final Consumer<Stream<Peer>> onPeersAddedListener;

	private final static Logger LOGGER = Logger.getLogger(NodePeers.class.getName());

	/**
	 * Creates the set of peers of a local node.
	 * 
	 * @throws DatabaseException if the database is corrupted
	 */
	public NodePeers(LocalNodeImpl node) throws DatabaseException {
		this.node = node;
		this.config = node.getConfig();
		this.db = node.getDatabase();
		this.onPeersAddedListener = peers -> node.executeAddPeersTask(peers, false);
		this.peers = PunishableSets.of(db.getPeers(), config.peerInitialPoints, this::onAdd, this::onRemove);
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
	 * Adds the given peer.
	 * 
	 * @param peer the peer to add
	 * @param force true if the peer must be added also if the maximum number of peers has been reached
	 * @return true if and only if the peer was not present and has been added
	 * @throws IOException if a connection to the peer cannot be established
	 * @throws IncompatiblePeerException if the version of {@code peer} is incompatible with that of the node
	 * @throws DatabaseException if the database is corrupted
	 * @throws TimeoutException if no answer arrives within a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	public boolean add(Peer peer, boolean force) throws TimeoutException, InterruptedException, IOException, IncompatiblePeerException, DatabaseException {
		return check(TimeoutException.class,
					 InterruptedException.class,
					 IOException.class,
					 IncompatiblePeerException.class,
					 DatabaseException.class,
			() -> peers.add(peer, force));
	}

	/**
	 * Punishes a peer by reducing its points. If they reach zero (or below),
	 * the peer is removed.
	 * 
	 * @param peer the peer
	 * @param points the points to remove
	 * @throws DatabaseException if the database is corrupted
	 */
	public void punish(Peer peer, long points) throws DatabaseException {
		check(DatabaseException.class, () -> peers.punish(peer, points));
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

	@Override
	public void close() throws IOException {
		IOException exception = null;

		synchronized (lock) {
			for (var entry: remotes.entrySet())
				try {
					closeRemoteWithException(entry.getValue(), entry.getKey());
				}
				catch (IOException e) {
					exception = e;
				}
		}

		if (exception != null)
			throw exception;
	}

	/**
	 * Tries to create the remotes of the peers that do not have a remote currently.
	 */
	void tryToCreateMissingRemotes() {
		LOGGER.info("trying to create missing remotes for the peers, if any");
		try (var pool = new ForkJoinPool()) {
			pool.execute(() ->
				peers.getElements().parallel().filter(peer -> !remotes.containsKey(peer)).forEach(this::tryToCreateRemote)
			);
		}
	}

	private void tryToCreateRemote(Peer peer) {
		RemotePublicNode remote = null;

		try {
			remote = openRemote(peer);
			ensurePeerIsCompatible(remote);

			synchronized (lock) {
				// we check if the peer is actually contained in the set of peers,
				// since it might have been removed in the meanwhile and we not not
				// want to store remotes for peers not in the set of peers of this object
				if (peers.contains(peer)) {
					storeRemote(remote, peer);
					remote = null; // so that it won't be closed in the finally clause
				}
			}
		}
		catch (IncompatiblePeerException e) {
			LOGGER.log(Level.SEVERE, e.getMessage());
			try {
				remove(peer);
			}
			catch (DatabaseException e1) {
				LOGGER.log(Level.SEVERE, "cannot remove " + peer + " from the database", e);
			}
		}
		catch (IOException | TimeoutException | InterruptedException e) {
			LOGGER.log(Level.SEVERE, "cannot contact peer " + peer + ": " + e.getMessage());
			try {
				punish(peer, config.peerPunishmentForUnreachable);
			}
			catch (DatabaseException e1) {
				LOGGER.log(Level.SEVERE, "cannot reduce the points of " + peer, e1);
			}
		}
		finally {
			closeRemote(remote, peer);
		}
	}

	private boolean onAdd(Peer peer, boolean force) {
		RemotePublicNode remote = null;

		try {
			remote = openRemote(peer);
			ensurePeerIsCompatible(remote);

			synchronized (lock) {
				if (db.addPeer(peer, force)) {
					LOGGER.info("added peer " + peer + " to the database");
					storeRemote(remote, peer);
					remote = null; // so that it won't be closed in the finally clause
					return true;
				}
				else
					return false;
			}
		}
		catch (IOException | TimeoutException | InterruptedException | DatabaseException | IncompatiblePeerException e) {
			LOGGER.log(Level.SEVERE, "cannot add peer " + peer + ": " + e.getMessage());
			throw new UncheckedException(e);
		}
		finally {
			closeRemote(remote, peer);
		}
	}

	private boolean onRemove(Peer peer) {
		try {
			synchronized (lock) {
				if (db.removePeer(peer)) {
					LOGGER.info("removed peer " + peer + " from the database");
					closeRemote(remotes.get(peer), peer);
					return true;
				}
				else
					return false;
			}
		}
		catch (DatabaseException e) {
			LOGGER.log(Level.SEVERE, "cannot remove peer " + peer, e);
			throw new UncheckedException(e);
		}
	}

	private RemotePublicNode openRemote(Peer peer) throws IOException {
		try {
			RemotePublicNode remote = RemotePublicNodes.of(peer.getURI(), config.peerTimeout);
			LOGGER.info("opened connection to peer " + peer);
			return remote;
		}
		catch (DeploymentException e) {
			throw new IOException(e);  // we consider it as a special case of IOException
		}
	}

	private void ensurePeerIsCompatible(RemotePublicNode remote) throws IncompatiblePeerException, TimeoutException, InterruptedException {
		NodeInfo info1 = remote.getInfo();
		NodeInfo info2 = node.getInfo();
		UUID uuid1 = info1.getUUID();

		if (uuid1.equals(info2.getUUID()))
			throw new IncompatiblePeerException("a peer cannot be added as a peer of itself: same " + info1.getUUID() + " UUID");

		var version1 = info1.getVersion();
		var version2 = info2.getVersion();
	
		if (!version1.canWorkWith(version2))
			throw new IncompatiblePeerException("peer version " + version1 + " is incompatible with this node's version " + version2);
	}

	private void storeRemote(RemotePublicNode remote, Peer peer) {
		remotes.put(peer, remote);
		remote.addOnPeersAddedListener(onPeersAddedListener);
	}

	private void closeRemote(RemotePublicNode remote, Peer peer) {
		try {
			closeRemoteWithException(remote, peer);
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot close the connection to peer " + peer, e);
		}
	}

	private void closeRemoteWithException(RemotePublicNode remote, Peer peer) throws IOException {
		if (remote != null) {
			remote.removeOnPeersAddedListener(onPeersAddedListener);
			remotes.remove(peer);
			remote.close();
			LOGGER.info("closed connection to peer " + peer);
		}
	}
}
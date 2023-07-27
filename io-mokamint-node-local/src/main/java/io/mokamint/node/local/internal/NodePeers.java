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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hotmoka.annotations.OnThread;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.exceptions.UncheckedException;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
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
	 * Lock used to guarantee that the peers in the database are
	 * consistent with the peers having a remote in this container.
	 */
	private final Object lock = new Object();

	/**
	 * The remote nodes connected to each peer.
	 */
	private final ConcurrentMap<Peer, RemotePublicNode> remotes = new ConcurrentHashMap<>();

	private final static Logger LOGGER = Logger.getLogger(NodePeers.class.getName());

	/**
	 * Creates the set of peers of a local node.
	 * 
	 * @param node the node having these peers
	 * @param db the database of {@code node}
	 * @throws DatabaseException if the database is corrupted
	 */
	public NodePeers(LocalNodeImpl node, Database db) throws DatabaseException {
		this.node = node;
		this.config = node.getConfig();
		this.db = db;
		this.peers = PunishableSets.of(db.getPeers(), config.peerInitialPoints, this::onAdd, this::onRemove);
		tryToAdd(config.seeds().map(Peers::of), true, true);
		node.scheduleWithFixedDelay(new PingPeersAndRecreateRemotesTask(node), 0L, config.peerPingInterval, TimeUnit.MILLISECONDS);
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

	/**
	 * Determines if this container contains a given peer.
	 * 
	 * @param peer the peer
	 * @return true if and only if this container contains {@code peer}
	 */
	public boolean contains(Peer peer) {
		return peers.contains(peer);
	}

	@Override
	public void close() throws IOException, InterruptedException {
		IOException ioException = null;
		InterruptedException interruptedException = null;

		synchronized (lock) {
			for (var entry: remotes.entrySet()) {
				try {
					closeRemoteWithException(entry.getValue(), entry.getKey());
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
	 * Yields a string describing some peers. It truncates peers too long
	 * or too many peers, in order to cope with potential log injections.
	 * 
	 * @return the string
	 */
	public String asSanitizedString(Stream<Peer> peers) {
		var peersAsArray = peers.toArray(Peer[]::new);
		String result = Stream.of(peersAsArray).limit(20).map(NodePeers::truncate).collect(Collectors.joining(", "));
		if (peersAsArray.length > 20)
			result += ", ...";
	
		return result;
	}

	/**
	 * Try to add the given peers to the node. Peers might not be added because there
	 * are already enough peers, or because a connection cannot be established to them,
	 * or because they are incompatible with the node.
	 * 
	 * @param toAdd the peers to add
	 * @param force true if and only if the peers must be added also if the maximum number of peers
	 *              for the node has been reached
	 * @param whisper true if and only if the peers actually added, at the end, must be whispered
	 *                to all peers of this node
	 */
	public void tryToAdd(Stream<Peer> toAdd, boolean force, boolean whisper) {
		var peersAsArray = toAdd.distinct().toArray(Peer[]::new);
		LOGGER.info("trying to add " + asSanitizedString(Stream.of(peersAsArray)) + " as peers");

		// before scheduling a task, we check if there is some peer that we really need
		var newPeers = Stream.of(peersAsArray)
			.distinct()
			.filter(peer -> !peers.contains(peer))
			.toArray(Peer[]::new);

		/**
		 * A task that adds peers to a node.
		 */
		class AddPeersTask extends Task {

			private AddPeersTask(LocalNodeImpl node) {
				node.super();
			}

			@Override
			public String toString() {
				return "addition of " + asSanitizedString(Stream.of(newPeers)) + " as peers";
			}

			@Override @OnThread("tasks")
			protected void body() {
				var added = Stream.of(newPeers).parallel().filter(peer -> addPeer(peer, force)).toArray(Peer[]::new);
				if (added.length > 0) // just to avoid useless events
					node.submit(node.new PeersAddedEvent(Stream.of(added), whisper));
			}

			private boolean addPeer(Peer peer, boolean force) {
				try {
					return add(peer, force);
				}
				catch (InterruptedException e) {
					LOGGER.log(Level.WARNING, "addition of " + peer + " as a peer interrupted");
					Thread.currentThread().interrupt();
					return false;
				}
				catch (IncompatiblePeerException | DatabaseException | IOException | TimeoutException e) {
					LOGGER.log(Level.WARNING, "addition of " + peer + " as a peer failed: " + e.getMessage());
					return false;
				}
			}
		}

		if (newPeers.length > 0 && (force || peers.getElements().count() < config.maxPeers))
			node.submit(new AddPeersTask(node));
	}

	private static String truncate(Peer peer) {
		String uri = peer.toString();
		if (uri.length() > 50)
			return uri.substring(0, 50) + "...";
		else
			return uri;
	}

	/**
	 * A task that pings all peers, tries to recreate their remote (if missing)
	 * and collects their peers, in case they might be useful for the node.
	 */
	private class PingPeersAndRecreateRemotesTask extends Task {

		private PingPeersAndRecreateRemotesTask(LocalNodeImpl node) {
			node.super();
		}

		@Override
		protected void body() {
			tryToAdd(peers.getElements().parallel().flatMap(this::pingPeerAndRecreateRemote), false, true);
		}

		private Stream<Peer> pingPeerAndRecreateRemote(Peer peer) {
			Optional<RemotePublicNode> remote = getRemote(peer)
				.or(() -> tryToCreateRemote(peer));

			return remote.isPresent() ? askForPeers(peer, remote.get()) : Stream.empty();
		}

		private Optional<RemotePublicNode> tryToCreateRemote(Peer peer) {
			RemotePublicNode remote = null;
		
			try {
				LOGGER.info("trying to recreate a connection to peer " + peer);
				remote = openRemote(peer);
				RemotePublicNode remoteCopy = remote;
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

				return Optional.of(remoteCopy);
			}
			catch (IncompatiblePeerException e) {
				LOGGER.log(Level.WARNING, e.getMessage());
				try {
					remove(peer);
				}
				catch (DatabaseException e1) {
					LOGGER.log(Level.SEVERE, "cannot remove " + peer + " from the database", e);
				}
			}
			catch (InterruptedException e) {
				LOGGER.log(Level.WARNING, "interrupted while creating a remote for " + peer + ": " + e.getMessage());
				Thread.currentThread().interrupt();
			}
			catch (IOException | TimeoutException | ClosedNodeException e) {
				LOGGER.log(Level.WARNING, "cannot contact peer " + peer + ": " + e.getMessage());
				punishBecauseUnreachable(peer);
			}
			finally {
				closeRemote(remote, peer);
			}

			return Optional.empty();
		}

		private Stream<Peer> askForPeers(Peer peer, RemotePublicNode remote) {
			try {
				Stream<PeerInfo> infos = remote.getPeerInfos();
				pardonBecauseReachable(peer);

				long numberOfPeers = peers.getElements().count();
				if (numberOfPeers >= config.maxPeers)
					return Stream.empty();
				else
					return infos.filter(PeerInfo::isConnected).map(PeerInfo::getPeer).filter(p -> !peers.contains(p));
			}
			catch (InterruptedException e) {
				LOGGER.log(Level.WARNING, "interrupted while asking the peers of " + peer + ": " + e.getMessage());
				Thread.currentThread().interrupt();
				return Stream.empty();
			}
			catch (TimeoutException | ClosedNodeException e) {
				LOGGER.log(Level.WARNING, "cannot contact peer " + peer + ": " + e.getMessage());
				punishBecauseUnreachable(peer);
				return Stream.empty();
			}
		}

		@Override
		public String toString() {
			return "ping to all peers to create missing remotes and collect their peers";
		}
	}

	private void punishBecauseUnreachable(Peer peer) {
		try {
			punish(peer, config.peerPunishmentForUnreachable);
		}
		catch (DatabaseException e1) {
			LOGGER.log(Level.SEVERE, "cannot reduce the points of " + peer, e1);
		}
	}

	private void pardonBecauseReachable(Peer peer) {
		peers.pardon(peer, config.peerPunishmentForUnreachable);
	}

	private boolean onAdd(Peer peer, boolean force) {
		RemotePublicNode remote = null;

		try {
			// optimization: this avoids opening a remote for an already existing peer
			// or trying to add a peer if there are already enough peers for this node
			if (peers.contains(peer) || (!force && peers.getElements().count() >= config.maxPeers))
				return false;

			remote = openRemote(peer);
			ensurePeerIsCompatible(remote);

			synchronized (lock) {
				if (db.add(peer, force)) {
					LOGGER.info("added peer " + peer + " to the database");
					storeRemote(remote, peer);
					remote = null; // so that it won't be closed in the finally clause
					return true;
				}
				else
					return false;
			}
		}
		catch (InterruptedException e) {
			LOGGER.log(Level.WARNING, "interrupted while adding " + peer + " to the peers: " + e.getMessage());
			Thread.currentThread().interrupt();
			throw new UncheckedException(e);
		}
		catch (IOException | TimeoutException | ClosedNodeException | DatabaseException | IncompatiblePeerException e) {
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
				if (db.remove(peer)) {
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
			var remote = RemotePublicNodes.of(peer.getURI(), config.peerTimeout, config.whisperingMemorySize);
			LOGGER.info("opened connection to peer " + peer); // TODO
			return remote;
		}
		catch (DeploymentException e) {
			throw new IOException(e);  // we consider it as a special case of IOException
		}
	}

	private void ensurePeerIsCompatible(RemotePublicNode remote) throws IncompatiblePeerException, TimeoutException, InterruptedException, ClosedNodeException {
		NodeInfo info1 = remote.getInfo();
		NodeInfo info2 = node.getInfo();
		UUID uuid1 = info1.getUUID();

		if (uuid1.equals(info2.getUUID()))
			throw new IncompatiblePeerException("a peer cannot be added as a peer of itself: same UUID " + info1.getUUID());

		var version1 = info1.getVersion();
		var version2 = info2.getVersion();
	
		if (!version1.canWorkWith(version2))
			throw new IncompatiblePeerException("peer version " + version1 + " is incompatible with this node's version " + version2);
	}

	private void storeRemote(RemotePublicNode remote, Peer peer) {
		remotes.put(peer, remote);
		remote.bindWhisperer(node);
		// if the remote gets closed, then it will get unlinked from the map of remotes
		remote.addOnClosedHandler(() -> peerDisconnected(remote, peer));
		node.submit(node.new PeerConnectedEvent(peer));
	}

	/**
	 * Called when a peer gets closed: it removes its remote and generates an event.
	 * 
	 * @param remote the remote of the peer, which is what is actually being closed
	 * @param peer the peer
	 */
	private void peerDisconnected(RemotePublicNode remote, Peer peer) {
		closeRemote(remote, peer);
		punishBecauseUnreachable(peer);
		node.submit(node.new PeerDisconnectedEvent(peer));
	}

	private void closeRemote(RemotePublicNode remote, Peer peer) {
		try {
			closeRemoteWithException(remote, peer);
		}
		catch (IOException | InterruptedException e) {
			LOGGER.log(Level.SEVERE, "cannot close the connection to peer " + peer, e);
		}
	}

	private void closeRemoteWithException(RemotePublicNode remote, Peer peer) throws IOException, InterruptedException {
		if (remote != null) {
			remote.unbindWhisperer(node);
			remotes.remove(peer);
			remote.close();
			LOGGER.info("closed connection to peer " + peer);
		}
	}
}
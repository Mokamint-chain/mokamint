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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.exceptions.UncheckedException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerVersionException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.local.Config;
import io.mokamint.node.remote.RemotePublicNode;
import io.mokamint.node.remote.RemotePublicNodes;
import jakarta.websocket.DeploymentException;

/**
 * The set of peers of a local node.
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
	 * The listener to call when some peers suggests some more peers to add.
	 */
	private final Consumer<Stream<Peer>> onAddedPeersListener;

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
	private final Map<Peer, RemotePublicNode> remotes = new HashMap<>();

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
		this.onAddedPeersListener = peers -> node.scheduleAddPeersTask(peers, false);
		this.peers = PunishableSets.of(db.getPeers(), _peer -> config.peerInitialPoints, this::onAdd, this::onRemove);
		this.peers.forEach(this::mkRemote);
	}

	/**
	 * Yields the peers.
	 * 
	 * @return the peers
	 */
	public Stream<Peer> get() {
		return peers.getElements();
	}

	/**
	 * Adds the given peer.
	 * 
	 * @param peer the peer to add
	 * @param force true if the peer must be added also if the maximum number of peers has been reached
	 * @return true if and only if the peer was not present and has been added
	 * @throws IOException if a connection to the peer cannot be established
	 * @throws IncompatiblePeerVersionException if the version of {@code peer} is incompatible with that of the node
	 * @throws DatabaseException if the database is corrupted
	 * @throws TimeoutException if no answer arrives within a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	public boolean add(Peer peer, boolean force) throws TimeoutException, InterruptedException, IOException, IncompatiblePeerVersionException, DatabaseException {
		return check(TimeoutException.class,
					 InterruptedException.class,
					 IOException.class,
					 IncompatiblePeerVersionException.class,
					 DatabaseException.class,
			() -> peers.add(peer, force));
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
					entry.getValue().close();
					LOGGER.info("closed connection to peer " + entry.getKey());
				}
				catch (IOException e) {
					exception = e;
				}
		}

		if (exception != null)
			throw exception;
	}

	private void mkRemote(Peer peer) {
		try {
			RemotePublicNode remote = null;

			try {
				remote = RemotePublicNodes.of(peer.getURI(), config.peerTimeout);
				LOGGER.info("opened connection to peer " + peer);
				var version1 = remote.getInfo().getVersion();
				var version2 = node.getInfo().getVersion();

				if (!version1.canWorkWith(version2))
					throw new IncompatiblePeerVersionException("peer version " + version1 + " is incompatible with this node's version " + version2);
				else {
					remotes.put(peer, remote);
					remote.addOnPeersAddedListener(onAddedPeersListener);
					remote = null; // so that it won't be closed in the finally clause
				}
			}
			finally {
				if (remote != null) {
					remote.close();
					LOGGER.info("closed connection to peer " + peer);
				}
			}
		}
		catch (DeploymentException | IOException | TimeoutException | InterruptedException | IncompatiblePeerVersionException e) {
			LOGGER.log(Level.SEVERE, "cannot contact peer " + peer, e);
			peers.punish(peer, config.peerPunishmentForUnreachable);
		}
	}

	private boolean onAdd(Peer peer, boolean force) {
		try {
			RemotePublicNode remote = null;

			try {
				remote = RemotePublicNodes.of(peer.getURI(), config.peerTimeout);
				LOGGER.info("opened connection to peer " + peer);
				var version1 = remote.getInfo().getVersion();
				var version2 = node.getInfo().getVersion();

				if (!version1.canWorkWith(version2))
					throw new IncompatiblePeerVersionException("peer version " + version1 + " is incompatible with this node's version " + version2);
				else {
					synchronized (lock) {
						if (db.addPeer(peer, force)) {
							remotes.put(peer, remote);
							remote.addOnPeersAddedListener(onAddedPeersListener);
							remote = null; // so that it won't be closed in the finally clause
							LOGGER.info("added peer " + peer + " to the database");
							return true;
						}
						else
							return false;
					}
				}
			}
			finally {
				if (remote != null) {
					remote.close();
					LOGGER.info("closed connection to peer " + peer);
				}
			}
		}
		catch (DeploymentException e) {
			LOGGER.log(Level.SEVERE, "cannot add peer " + peer, e);
			throw new UncheckedException(new IOException(e)); // we consider it as a special case of IOException
		}
		catch (IOException | TimeoutException | InterruptedException | DatabaseException | IncompatiblePeerVersionException e) {
			LOGGER.log(Level.SEVERE, "cannot add peer " + peer, e);
			throw new UncheckedException(e);
		}
	}

	private boolean onRemove(Peer peer) {
		try {
			synchronized (lock) {
				if (db.removePeer(peer)) {
					RemotePublicNode remote = remotes.get(peer);
					if (remote == null)
						LOGGER.log(Level.SEVERE, "the peer " + peer + " was in the database, but its remote is missing");
					else {
						remote.removeOnPeersAddedListener(onAddedPeersListener);
						try {
							remote.close();
							LOGGER.info("closed connection to peer " + peer);
						}
						catch (IOException e) {
							LOGGER.log(Level.SEVERE, "cannot close the connection to peer " + peer, e);
						}

						remotes.remove(peer);
					}
					LOGGER.info("removed peer " + peer + " from the database");
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
}
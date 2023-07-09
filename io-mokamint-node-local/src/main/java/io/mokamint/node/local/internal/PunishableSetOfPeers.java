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
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.exceptions.UncheckedException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerVersionException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.local.Config;
import io.mokamint.node.remote.RemotePublicNodes;
import jakarta.websocket.DeploymentException;

/**
 * @author spoto
 *
 */
public class PunishableSetOfPeers {

	/**
	 * The node.
	 */
	private final LocalNodeImpl node;

	/**
	 * The peers of the node.
	 */
	private final PunishableSet<Peer> peers;

	/**
	 * The configuration of the node.
	 */
	private final Config config;

	/**
	 * The db of the node.
	 */
	private final Database db;

	private final static Logger LOGGER = Logger.getLogger(PunishableSetOfPeers.class.getName());

	/**
	 * @throws DatabaseException if the db is corrupted
	 */
	public PunishableSetOfPeers(LocalNodeImpl node) throws DatabaseException {
		this.node = node;
		this.config = node.getConfig();
		this.db = node.getDatabase();
		this.peers = PunishableSets.of(node.getDatabase().getPeers(), _peer -> config.peerInitialPoints, this::addPeerToDB, this::removePeerFromDB);
	}

	public Stream<Peer> getPeers() {
		return peers.getElements();
	}

	public boolean add(Peer peer, boolean force) throws TimeoutException, InterruptedException, IOException, IncompatiblePeerVersionException, DatabaseException {
		if (peers.contains(peer))
			return false;

		try (var remote = RemotePublicNodes.of(peer.getURI(), config.peerTimeout)) {
			var version1 = remote.getInfo().getVersion();
			var version2 = node.getInfo().getVersion();

			if (!version1.canWorkWith(version2))
				throw new IncompatiblePeerVersionException("peer version " + version1 + " is incompatible with this node's version " + version2);
			else 
				return check(DatabaseException.class, () -> peers.add(peer, force));
		}
		catch (DeploymentException | IOException e) {
			throw new IOException("cannot contact " + peer, e);
		}
	}

	public boolean remove(Peer peer) throws DatabaseException {
		return check(DatabaseException.class, () -> peers.remove(peer));
	}

	private boolean addPeerToDB(Peer peer, boolean force) {
		try {
			if (db.addPeer(peer, force)) {
				LOGGER.info("added peer " + peer + " to the db");
				return true;
			}
			else
				return false;
		}
		catch (DatabaseException e) {
			LOGGER.log(Level.SEVERE, "cannot add peer " + peer + ": the db seems corrupted", e);
			throw new UncheckedException(e);
		}
	}

	private boolean removePeerFromDB(Peer peer) {
		try {
			if (db.removePeer(peer)) {
				LOGGER.info("removed peer " + peer + " from the db");
				return true;
			}
			else
				return false;
		}
		catch (DatabaseException e) {
			LOGGER.log(Level.SEVERE, "cannot remove peer " + peer + ": the db seems corrupted", e);
			throw new UncheckedException(e);
		}
	}
}
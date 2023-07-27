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

package io.mokamint.node.local.internal.tasks;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.OnThread;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.NodePeers;

/**
 * A task that adds peers to a node.
 */
public class AddPeersTask extends Task {

	/**
	 * The peers to add.
	 */
	private final Peer[] toAdd;

	/**
	 * The container of the peers of the node.
	 */
	private final NodePeers peers;

	/**
	 * True if and only if the peers must be added also if the node has already
	 * enough peers.
	 */
	private final boolean force;

	/**
	 * True if and only if the peers successfully added will be whispered
	 * to all peers of the node at the end.
	 */
	private final boolean whisper;

	private final static Logger LOGGER = Logger.getLogger(AddPeersTask.class.getName());

	/**
	 * Creates a task that adds peers to a node.
	 * 
	 * @param toAdd the peers to add
	 * @param peers the manager of the peers of the node
	 * @param node the node for which this task is working
	 * @param force true if and only if the peers must be added also if the node has already
	 *              enough peers
	 * @param whisper true if and only if the peers successfully added will be whispered
	 *                to all peers of the node at the end
	 */
	public AddPeersTask(Stream<Peer> toAdd, NodePeers peers, LocalNodeImpl node, boolean force, boolean whisper) {
		node.super();

		this.toAdd = toAdd.distinct().toArray(Peer[]::new);
		this.peers = peers;
		this.force = force;
		this.whisper = whisper;
	}

	@Override
	public String toString() {
		return "addition of " + NodePeers.peersAsString(Stream.of(toAdd)) + " as peers";
	}

	@Override @OnThread("tasks")
	protected void body() {
		// TODO: could addPeer be spawned in parallel?
		var added = Stream.of(toAdd).filter(peer -> addPeer(peer, force)).toArray(Peer[]::new);
		if (added.length > 0) // just to avoid useless events
			node.submit(node.new PeersAddedEvent(Stream.of(added), whisper));
	}

	private boolean addPeer(Peer peer, boolean force) {
		try {
			return peers.add(peer, force);
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
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hotmoka.annotations.OnThread;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;

/**
 * A task that adds peers to a node.
 */
public class AddPeersTask extends Task {

	/**
	 * The peers to add.
	 */
	private final Peer[] peers;

	/**
	 * The task to execute to actually add a peer to the database of the node.
	 */
	private final PeerAddition adder;

	/**
	 * True if and only if the peers successfully added will be whispered
	 * to all peers of the node at the end.
	 */
	private final boolean whisper;

	private final static Logger LOGGER = Logger.getLogger(AddPeersTask.class.getName());

	/**
	 * The type of the code to execute to actually add the peer to the database of the node.
	 */
	public interface PeerAddition {
		
		/**
		 * Tries to add the given peer to the node.
		 * 
		 * @param peer the peer to add
		 * @return true if and only if the peer has been added
		 * @throws IOException if a connection to the peer cannot be established
		 * @throws IncompatiblePeerException if the version of {@code peer} is incompatible with that of this node
		 * @throws DatabaseException if the database is corrupted
		 * @throws TimeoutException if no answer arrives before a time window
		 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
		 */
		boolean add(Peer peer) throws TimeoutException, InterruptedException, IOException, IncompatiblePeerException, DatabaseException;
	}

	/**
	 * Creates a task that adds peers to a node.
	 * 
	 * @param peers the peers to add
	 * @param adder the code to execute to actually add a peer to the node
	 * @param node the node for which this task is working
	 * @param whisper true if and only if the peers successfully added will be whispered
	 *                to all peers of the node at the end
	 */
	public AddPeersTask(Stream<Peer> peers, PeerAddition adder, LocalNodeImpl node, boolean whisper) {
		node.super();

		this.peers = peers.distinct().toArray(Peer[]::new);
		this.adder = adder;
		this.whisper = whisper;
	}

	@Override
	public String toString() {
		return "addition of " + peersAsString() + " as peers";
	}

	/**
	 * Yields a string describing {@link #peers}. It truncates peers too long
	 * or too many peers, in order to cope with potential log injections.
	 * 
	 * @return the string
	 */
	private String peersAsString() {
		String result = Stream.of(peers).limit(20).map(this::truncate).collect(Collectors.joining(", "));
		if (peers.length > 20)
			result += ", ...";

		return result;
	}

	private String truncate(Peer peer) {
		String uri = peer.toString();
		if (uri.length() > 50)
			return uri.substring(0, 50) + "...";
		else
			return uri;
	}

	@Override @OnThread("tasks")
	protected void body() {
		// TODO: could addPeer be spawned in parallel?
		var added = Stream.of(peers).filter(this::addPeer).toArray(Peer[]::new);
		if (added.length > 0) // just to avoid useless events
			node.submit(node.new PeersAddedEvent(Stream.of(added), whisper));
	}

	private boolean addPeer(Peer peer) {
		try {
			return adder.add(peer);
		}
		catch (InterruptedException e) {
			LOGGER.log(Level.WARNING, this + " interrupted");
			Thread.currentThread().interrupt();
			return false;
		}
		catch (IncompatiblePeerException | DatabaseException | IOException | TimeoutException e) {
			LOGGER.log(Level.WARNING, "giving up adding " + peer + " as a peer");
			return false;
		}
	}
}
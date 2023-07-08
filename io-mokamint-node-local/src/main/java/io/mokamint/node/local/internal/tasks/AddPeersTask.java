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
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.OnThread;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerVersionException;
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

	private final static Logger LOGGER = Logger.getLogger(AddPeersTask.class.getName());

	/**
	 * The type of the code to execute to actually add the peer to the database of the node.
	 */
	public interface PeerAddition {
		void add(Peer peer) throws TimeoutException, InterruptedException, IOException, IncompatiblePeerVersionException, DatabaseException;
	}

	/**
	 * Creates a task that adds peers to a node.
	 * 
	 * @param peers the peers to add
	 * @param adder the function to call to actually add a peer to the node
	 * @param node the node for which this task is working
	 */
	public AddPeersTask(Stream<Peer> peers, PeerAddition adder, LocalNodeImpl node) {
		node.super();

		this.peers = peers.toArray(Peer[]::new);
		this.adder = adder;
	}

	@Override
	public String toString() {
		return "addition of " + Arrays.toString(peers) + " as peers";
	}

	@Override @OnThread("tasks")
	protected void body() {
		try (var customThreadPool = new ForkJoinPool()) {
			customThreadPool.execute(() -> Stream.of(peers).parallel().forEach(this::addPeer));
		}
	}

	@OnThread("customThreadPool")
	private void addPeer(Peer peer) {
		try {
			adder.add(peer);
		}
		catch (InterruptedException | IncompatiblePeerVersionException | DatabaseException | IOException | TimeoutException e) {
			LOGGER.log(Level.WARNING, "giving up adding " + peer + " as a peer", e);
		}
	}
}
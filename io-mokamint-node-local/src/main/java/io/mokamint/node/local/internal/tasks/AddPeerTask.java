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
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.OnThread;
import io.mokamint.node.api.IncompatiblePeerVersionException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.remote.RemotePublicNodes;
import jakarta.websocket.DeploymentException;

/**
 * A task that adds a peer to a node. It asks the peer about its version and checks
 * if it is compatible with the version of the node.
 */
public class AddPeerTask extends Task {

	/**
	 * The peer to add.
	 */
	private final Peer peer;

	/**
	 * True if and only if the addition of the peer must be forced, also if
	 * the maximal amount of peers has been reached.
	 */
	private final boolean force;

	/**
	 * The function to call to actually add the peer to the node.
	 */
	private final BiFunction<Peer, Boolean, Boolean> adder;

	private final static Logger LOGGER = Logger.getLogger(AddPeerTask.class.getName());

	/**
	 * Creates a task that adds a peer to a node.
	 * 
	 * @param peer the peer to add
	 * @param force true if and only if the addition of the peer must be forced, also if
	 *              the maximal amount of peers has been reached
	 * @param adder the function to call to actually add the peer to the node
	 * @param node the node for which this task is working
	 */
	public AddPeerTask(Peer peer, boolean force, BiFunction<Peer, Boolean, Boolean> adder, LocalNodeImpl node) {
		node.super();

		this.peer = peer;
		this.force = force;
		this.adder = adder;
	}

	@Override
	public String toString() {
		return "add " + peer + " as peer";
	}

	@Override @OnThread("tasks")
	protected void body() {
		try (var remote = RemotePublicNodes.of(peer.getURI(), node.getConfig().peerTimeout)) {
			var version1 = remote.getInfo().getVersion();
			var version2 = node.getInfo().getVersion();

			if (!version1.canWorkWith(version2))
				throw new IncompatiblePeerVersionException("peer version " + version1 + " is incompatible with this node's version " + version2);

			if (adder.apply(peer, force))
				node.emit(node.new PeerAddedEvent(peer));
		}
		catch (InterruptedException | IncompatiblePeerVersionException | IOException | DeploymentException | TimeoutException e) {
			LOGGER.log(Level.WARNING, "giving up adding " + peer + " as a peer", e);
		}
	}
}
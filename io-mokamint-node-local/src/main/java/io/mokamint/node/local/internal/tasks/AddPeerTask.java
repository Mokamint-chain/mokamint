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
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.mokamint.node.api.Peer;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.remote.RemotePublicNodes;
import jakarta.websocket.DeploymentException;

/**
 * A task that adds a peer to a node. It asks the peer about its version and checks
 * if it is compatible with the version of the node. If the peer does not answer,
 * it pings it a few times before giving up.
 */
public class AddPeerTask extends Task {

	/**
	 * The peer to add.
	 */
	private final Peer peer;

	private final static Logger LOGGER = Logger.getLogger(AddPeerTask.class.getName());

	/**
	 * Creates a task that adds a peer to a node.
	 * 
	 * @param peer the peer to add
	 * @param node the node for which this task is working
	 */
	public AddPeerTask(Peer peer, LocalNodeImpl node) {
		node.super();

		this.peer = peer;
	}

	@Override
	public void run() {
		var config = node.getConfig();
		var delay = config.peerTimeout;
		var furtherAttempts = config.peerAttempts;

		// TODO
		node.signal(node.new PeerAcceptedEvent(peer));

		/*
		while (furtherAttempts-- > 0) {
			try (var remote = RemotePublicNodes.of(peer.getURI(), config.peerTimeout)) {
				remote.getChainInfo();
				node.signal(node.new PeerAcceptedEvent(peer));
				return;
			}
			catch (InterruptedException | NoSuchAlgorithmException e) {
				LOGGER.log(Level.WARNING, "giving up adding " + peer + " as a peer", e);
				return;
			}
			catch (IOException | DeploymentException | TimeoutException e) {
				LOGGER.log(Level.WARNING, "peer " + peer + " did not answer: will retry in " + delay + "ms", e);

				try {
					Thread.sleep(delay);
				}
				catch (InterruptedException e1) {
					LOGGER.log(Level.WARNING, "giving up adding " + peer + " as a peer", e1);
					return;
				}

				delay *= 2;
			}
		}
		*/
	}
}
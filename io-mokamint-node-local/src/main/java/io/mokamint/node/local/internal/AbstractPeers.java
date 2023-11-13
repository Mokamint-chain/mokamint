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

/**
 * 
 */
package io.mokamint.node.local.internal;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.api.Peer;

/**
 * Bridge class to give access to protected methods to its subclass,
 * that represents the peers of a Mokamint node.
 */
@ThreadSafe
public abstract class AbstractPeers {

	/**
	 * The node having these peers.
	 */
	private final LocalNodeImpl node;

	/**
	 * Creates the peers of a Mokamint node.
	 * 
	 * @param node the node having the peers
	 */
	protected AbstractPeers(LocalNodeImpl node) {
		this.node = node;
	}

	/**
	 * Yields the node having these peers.
	 * 
	 * @return the node having these peers
	 */
	protected final LocalNodeImpl getNode() {
		return node;
	}

	/**
	 * @see LocalNodeImpl#onPeerAdded(Peer).
	 */
	protected void onPeerAdded(Peer peer) {
		node.onPeerAdded(peer);
	}

	/**
	 * @see LocalNodeImpl#onPeerRemoved(Peer).
	 */
	protected void onPeerRemoved(Peer peer) {
		node.onPeerRemoved(peer);
	}

	/**
	 * @see LocalNodeImpl#onPeerConnected(Peer).
	 */
	protected void onPeerConnected(Peer peer) {
		node.onPeerConnected(peer);
	}

	/**
	 * @see LocalNodeImpl#onPeerDisconnected(Peer).
	 */
	protected void onPeerDisconnected(Peer peer) {
		node.onPeerDisconnected(peer);
	}
}
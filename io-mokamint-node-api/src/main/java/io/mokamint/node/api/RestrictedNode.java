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

package io.mokamint.node.api;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import io.hotmoka.annotations.ThreadSafe;

/**
 * The restricted interface of a node of a Mokamint blockchain.
 * Typically, this API can be called from authorized machines only.
 */
@ThreadSafe
public interface RestrictedNode extends AutoCloseableNode {

	/**
	 * Adds the given peer to the set of peers of this node.
	 * 
	 * @param peer the peer to add
	 * @throws IOException if a connection to the peer cannot be established
	 * @throws IncompatiblePeerException if the version of {@code peer} is incompatible with that of this node or if they look to be the same node
	 * @throws DatabaseException if the database is corrupted
	 * @throws TimeoutException if no answer arrives within a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	void addPeer(Peer peer) throws IncompatiblePeerException, IOException, DatabaseException, TimeoutException, InterruptedException;

	/**
	 * Removes the given peer from the set of peers of this node.
	 * 
	 * @param peer the peer to remove
	 * @throws DatabaseException if the database is corrupted
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	void removePeer(Peer peer) throws TimeoutException, InterruptedException, DatabaseException;
}
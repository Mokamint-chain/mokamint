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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.miner.api.MinerException;

/**
 * The restricted interface of a node of a Mokamint blockchain.
 * Typically, this API can be called from authorized actors only.
 */
@ThreadSafe
public interface RestrictedNode extends Node {

	/**
	 * Adds the given peer to the set of peers of this node, if it was not already there.
	 * If the peer was present but was disconnected, it tries to reconnect it.
	 * 
	 * @param peer the peer to add
	 * @return the information about the added peer; this is empty if the peer has not been added nor reconnected,
	 *         for instance because it was already present or the node has already reached a maximum number of peers
	 * @throws PeerException if {@code peer} is misbehaving
	 * @throws PeerRejectedException if {@code peer} was rejected for some reason
	 * @throws TimeoutException if no answer arrives within a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws NodeException if the node could not complete the operation
	 */
	Optional<PeerInfo> add(Peer peer) throws PeerRejectedException, PeerException, TimeoutException, InterruptedException, NodeException;

	/**
	 * Removes the given peer from the set of peers of this node, if it was there.
	 * 
	 * @param peer the peer to remove
	 * @return true if and only if the peer has been actually removed
	 * @throws TimeoutException if no answer arrives within a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws NodeException if the node could not complete the operation
	 */
	boolean remove(Peer peer) throws TimeoutException, InterruptedException, NodeException;

	/**
	 * Opens a remote miner at the given port.
	 * 
	 * @param port the port
	 * @return the information about the opened miner; this is empty if the miner has not been opened
	 * @throws TimeoutException if no answer arrives within a time window
	 * @throws MinerException if a connection cannot be established (for instance, if the port is already bound to some service)
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws NodeException if the node could not complete the operation
	 */
	Optional<MinerInfo> openMiner(int port) throws TimeoutException, MinerException, InterruptedException, NodeException;

	/**
	 * Removes a miner. If that miner has been created through {@link #openMiner(int)}, it gets closed as well.
	 * 
	 * @param uuid the unique identifier of the miner to remove
	 * @return true if and only if the miner has been removed; this is false if, for instance, no miner
	 *         with the given {@code uuid} exists
	 * @throws TimeoutException if no answer arrives within a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws NodeException if the node could not complete the operation
	 */
	boolean removeMiner(UUID uuid) throws TimeoutException, InterruptedException, NodeException;
}
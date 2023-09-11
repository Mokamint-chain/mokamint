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
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import io.hotmoka.annotations.ThreadSafe;

/**
 * The restricted interface of a node of a Mokamint blockchain.
 * Typically, this API can be called from authorized machines only.
 */
@ThreadSafe
public interface RestrictedNode extends Node {

	/**
	 * Adds the given peer to the set of peers of this node, if it was not already there.
	 * 
	 * @param peer the peer to add
	 * @return if and only if the peer has been actually added
	 * @throws IOException if a connection to the peer cannot be established
	 * @throws PeerRejectedException if {@code peer} was rejected for some reason
	 * @throws DatabaseException if the database of this node is corrupted
	 * @throws TimeoutException if no answer arrives within a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if this node is closed
	 */
	boolean add(Peer peer) throws PeerRejectedException, IOException, DatabaseException, TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Removes the given peer from the set of peers of this node, if it was there.
	 * 
	 * @param peer the peer to remove
	 * @return true if and only if the peer has been actually removed
	 * @throws IOException if a connection to the peer cannot be established
	 * @throws DatabaseException if the database of this node is corrupted
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if this node is closed
	 */
	boolean remove(Peer peer) throws TimeoutException, IOException, InterruptedException, ClosedNodeException, DatabaseException;

	/**
	 * Opens a remote miner at the given port.
	 * 
	 * @param port the port
	 * @return true if and only if the remote miner has been opened
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws IOException if an I/O error occurred (for instance, if the port is already bound to some service)
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if this node is closed
	 */
	boolean openMiner(int port) throws TimeoutException, IOException, InterruptedException, ClosedNodeException;

	/**
	 * Closes a miner.
	 * 
	 * @param uuid the unique identifier of the miner to close
	 * @return true if and only if the miner has been closed; this is false if, for instance, no miner
	 *         with the given {@code uuid} is currently open
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if this node is closed
	 */
	boolean closeMiner(UUID uuid) throws TimeoutException, InterruptedException, ClosedNodeException;
}
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

import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;

/**
 * The public interface of a node of a Mokamint blockchain.
 * Typically, this API can be called from every machine.
 */
@ThreadSafe
public interface PublicNode extends AutoCloseableNode {

	/**
	 * Yields non-consensus information about the node.
	 * 
	 * @return the information
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	NodeInfo getInfo() throws TimeoutException, InterruptedException;

	/**
	 * Yields the consensus configuration parameters of this node.
	 * 
	 * @return the consensus parameters
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	ConsensusConfig getConfig() throws TimeoutException, InterruptedException;

	/**
	 * Yields information about the peers this node is connected to. There is a dynamic
	 * set of peers connected to a node, potentially zero or more peers.
	 * Peers might be connected or disconnected to the node at the moment.
	 * 
	 * @return the peers information
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	Stream<PeerInfo> getPeerInfos() throws TimeoutException, InterruptedException;

	/**
	 * Yields information about the current chain of this node.
	 * 
	 * @return the information
	 * @throws NoSuchAlgorithmException if the head block uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	ChainInfo getChainInfo() throws NoSuchAlgorithmException, DatabaseException, TimeoutException, InterruptedException;

	/**
	 * Yields the block with the given hash, if it has been seen by this node.
	 * 
	 * @param hash the hash of the block
	 * @return the block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if the block exists but uses an unknown hashing algorithm
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	Optional<Block> getBlock(byte[] hash) throws DatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException;
}
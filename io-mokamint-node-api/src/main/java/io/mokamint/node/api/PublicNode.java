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
public interface PublicNode extends AutoCloseable {

	/**
	 * Yields the block with the given hash, if it has been seen by this node.
	 * 
	 * @param hash the hash of the block
	 * @return the block, if any
	 * @throws NoSuchAlgorithmException if the block exists but uses an unknown hashing algorithm
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	Optional<Block> getBlock(byte[] hash) throws NoSuchAlgorithmException, TimeoutException, InterruptedException;

	/**
	 * Yields the peers this node is connected to. There can be zero or more peers
	 * and their set and amount is dynamic. There is no guarantee that such peers are
	 * active and/or reachable.
	 * 
	 * @return the peers
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	Stream<Peer> getPeers() throws TimeoutException, InterruptedException;

	/**
	 * Yields the consensus parameters of this node.
	 * 
	 * @return the consensus parameters
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	ConsensusConfig getConfig() throws TimeoutException, InterruptedException;

	/**
	 * Closes the node.
	 * 
	 * @throws IOException if an I/O error occurred
	 * @throws InterruptedException if some closing activity has been interrupted
	 */
	@Override
	void close() throws IOException, InterruptedException;
}
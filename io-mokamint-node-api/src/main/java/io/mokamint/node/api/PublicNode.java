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
import java.util.stream.Stream;

/**
 * The public interface of a node of a Mokamint blockchain.
 * Typically, this API can be called from every machine.
 */
public interface PublicNode {

	/**
	 * Yields the block with the given hash, if it has been seen by this node.
	 * 
	 * @param hash the hash of the block
	 * @return the block, if any
	 * @throws NoSuchAlgorithmException if the block exists but uses an unknown hashing algorithm
	 */
	Optional<Block> getBlock(byte[] hash) throws NoSuchAlgorithmException;

	/**
	 * Yields the peers this node is connected to. There can be zero or more peers
	 * and their set and amount is dynamic. There is no guarantee that such peers are
	 * active and/or reachable.
	 * 
	 * @return the peers
	 */
	Stream<Peer> getPeers();
}
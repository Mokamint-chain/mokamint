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

import java.time.LocalDateTime;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.Marshallable;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;

/**
 * A block of the Mokamint blockchain.
 */
@Immutable
public sealed interface Block extends BlockDescription, Marshallable permits GenesisBlock, NonGenesisBlock {

	/**
	 * Yields the hash of this block, by using the given hashing algorithm.
	 * This hash does not use the signature of the node (if any) which is, instead,
	 * computed from this hash and the private key of the signer.
	 * 
	 * @param hashing the hashing algorithm
	 * @return the hash of this block
	 */
	byte[] getHash(HashingAlgorithm hashing);

	/**
	 * Yields the hash of this block, by using the given hashing algorithm,
	 * as an hexadecimal string.
	 * 
	 * @param hashing the hashing algorithm
	 * @return the hash of this block, as a hexadecimal string
	 */
	String getHexHash(HashingAlgorithm hashing);

	/**
	 * Yields the description of the deadline that must be computed for the next block.
	 * 
	 * @param hashingForGenerations the hashing algorithm to use to compute the next generation signature
	 * @param hashingForDeadlines the hashing algorithm used for the deadlines and the plot files
	 * @return the description
	 */
	DeadlineDescription getNextDeadlineDescription(HashingAlgorithm hashingForGenerations, HashingAlgorithm hashingForDeadlines);

	/**
	 * Yields the description of the next block, assuming that the latter has the given deadline.
	 * 
	 * @param deadline the deadline of the next block
	 * @param targetBlockCreationTime the target time interval, in milliseconds, between the creation of a block
	 *                                and the creation of a next block
	 * @param hashingForBlocks the hashing algorithm used for the blocks
	 * @param hashingForDeadlines the hashing algorithm for the deadlines
	 * @return the description
	 */
	NonGenesisBlock getNextBlockDescription(Deadline deadline, long targetBlockCreationTime, HashingAlgorithm hashingForBlocks, HashingAlgorithm hashingForDeadlines);

	/**
	 * Checks if this block is equal to another object.
	 * 
	 * @param other the other object
	 * @return true if and only if {@code other} is a {@link Block} with the same data
	 */
	@Override
	boolean equals(Object other);

	/**
	 * Yields a string representation of this block. This yields a more informative
	 * representation of the block, with extra information computed by using the
	 * given configuration for the node.
	 * 
	 * @param config the configuration used to interpret and reconstruct the extra
	 *               information about the block
	 * @param startDateTimeUTC the creation time of the genesis block of the chain of the node
	 * @return the representation
	 */
	String toString(ConsensusConfig<?,?> config, LocalDateTime startDateTimeUTC);
	
}
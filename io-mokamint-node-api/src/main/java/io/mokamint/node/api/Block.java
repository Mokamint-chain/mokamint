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

import java.math.BigInteger;
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
public sealed interface Block extends Marshallable permits GenesisBlock, NonGenesisBlock {

	/**
	 * Yields the power of this block, computed as the sum, for each block from genesis to this,
	 * of 2^(hashing bits) / (value of the deadline in the block + 1). This allows one to compare
	 * forks and choose the one whose tip has the highest power. Intuitively, the power
	 * expresses the space used to compute the chain leading to the block.
	 * 
	 * @return the power
	 */
	BigInteger getPower();

	/**
	 * Yields the total waiting time, in milliseconds, from the genesis block
	 * until the creation of this block.
	 * 
	 * @return the total waiting time
	 */
	long getTotalWaitingTime();

	/**
	 * Yields the weighted waiting time, in milliseconds, from the genesis block
	 * until the creation of this block. This is an average waiting time that gives
	 * 5% weight to the waiting time for this block and 95% to the cumulative
	 * weighted waiting time at the previous block.
	 * 
	 * @return the weighted waiting time
	 */
	long getWeightedWaitingTime();

	/**
	 * Yields the acceleration used for the creation of this block, that is,
	 * a value used to divide the deadline to derive the time needed to wait for it.
	 * The higher, the shorter the time. This value changes from block to block in order
	 * to cope with varying mining power in the network. It is the inverse of Bitcoin's difficulty.
	 * 
	 * @return the acceleration
	 */
	BigInteger getAcceleration();

	/**
	 * Yields the height of the block, counting from 0 for the genesis block.
	 * 
	 * @return the height of the block
	 */
	long getHeight();

	/**
	 * Yields the hash of this block, by using the given hashing algorithm.
	 * 
	 * @param hashing the hashing algorithm
	 * @return the hash of this block
	 */
	byte[] getHash(HashingAlgorithm<byte[]> hashing);

	/**
	 * Yields the hash of this block, by using the given hashing algorithm,
	 * as an hexadecimal string.
	 * 
	 * @param hashing the hashing algorithm
	 * @return the hash of this block, as a hexadecimal string
	 */
	String getHexHash(HashingAlgorithm<byte[]> hashing);

	/**
	 * Yields the description of the deadline that must be computed for the next block.
	 * 
	 * @param hashingForGenerations the hashing algorithm to use to compute the next generation signature
	 * @param hashingForDeadlines the hashing algorithm used for the deadlines and the plot files
	 * @return the description
	 */
	DeadlineDescription getNextDeadlineDescription(HashingAlgorithm<byte[]> hashingForGenerations, HashingAlgorithm<byte[]> hashingForDeadlines);

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
	NonGenesisBlock getNextBlockDescription(Deadline deadline, long targetBlockCreationTime, HashingAlgorithm<byte[]> hashingForBlocks, HashingAlgorithm<byte[]> hashingForDeadlines);

	/**
	 * Checks if this block is equal to another object.
	 * 
	 * @param other the other object
	 * @return true if and only if {@code other} is a {@link Block} with the same data
	 */
	@Override
	boolean equals(Object other);

	@Override
	int hashCode();

	@Override
	String toString();

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
	String toString(ConsensusConfig config, LocalDateTime startDateTimeUTC);
	
}
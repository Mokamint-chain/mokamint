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

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.Marshallable;
import io.mokamint.nonce.api.DeadlineDescription;

/**
 * A block of the Mokamint blockchain.
 */
public interface Block extends Marshallable {

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
	 */
	BigInteger getAcceleration();

	/**
	 * Yields the height of the node, counting from 0 for the genesis block.
	 * 
	 * @return the height of the node
	 */
	long getHeight();

	/**
	 * Yields the description of the deadline that must be computed for the next block.
	 * 
	 * @param hashingForGenerations the hashing algorithm to use to compute the next generation signature
	 * @param hashingForDeadlines the hashing algorithm used for the deadlines and the plot files
	 * @return the description
	 */
	DeadlineDescription getNextDeadlineDescription(HashingAlgorithm<byte[]> hashingForGenerations, HashingAlgorithm<byte[]> hashingForDeadlines);
}
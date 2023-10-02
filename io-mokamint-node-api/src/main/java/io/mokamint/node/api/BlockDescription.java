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

import io.hotmoka.annotations.Immutable;

/**
 * The description of a block of the Mokamint blockchain.
 */
@Immutable
public interface BlockDescription {

	/**
	 * Yields the power of the block, computed as the sum, for each block from genesis to the block,
	 * of 2^(hashing bits) / (value of the deadline in the block + 1). This allows one to compare
	 * forks and choose the one whose tip has the highest power. Intuitively, the power
	 * expresses the space used to compute the chain leading to the block.
	 * 
	 * @return the power
	 */
	BigInteger getPower();

	/**
	 * Yields the total waiting time, in milliseconds, from the genesis block
	 * until the creation of the block.
	 * 
	 * @return the total waiting time
	 */
	long getTotalWaitingTime();

	/**
	 * Yields the weighted waiting time, in milliseconds, from the genesis block
	 * until the creation of the block. This is an average waiting time that gives
	 * 5% weight to the waiting time for the block and 95% to the cumulative
	 * weighted waiting time at the previous block.
	 * 
	 * @return the weighted waiting time
	 */
	long getWeightedWaitingTime();

	/**
	 * Yields the acceleration used for the creation of the block, that is,
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
	 * Checks if this block description is equal to another object.
	 * 
	 * @param other the other object
	 * @return true if and only if {@code other} is a {@link BlockDescription} with the same data
	 */
	@Override
	boolean equals(Object other);

	@Override
	int hashCode();

	@Override
	String toString();
}
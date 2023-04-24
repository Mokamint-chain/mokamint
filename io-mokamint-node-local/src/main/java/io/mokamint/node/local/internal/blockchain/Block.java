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

package io.mokamint.node.local.internal.blockchain;

import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.Marshallable;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.nonce.api.Deadline;

/**
 * A block of the Mokamint blockchain.
 */
public interface Block extends Marshallable {

	static NonGenesisBlock of(long blockNumber, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration,
			Deadline deadline, byte[] hashOfPreviousBlock) {
		return new NonGenesisBlock(blockNumber, totalWaitingTime, weightedWaitingTime, acceleration, deadline, hashOfPreviousBlock);
	}

	static GenesisBlock genesis(LocalDateTime startDateTimeUTC) {
		return new GenesisBlock(startDateTimeUTC);
	}

	/**
	 * Unmarshals a block from the given context.
	 * 
	 * @param context the context
	 * @return the block
	 * @throws IOException if the block cannot be unmarshalled
	 * @throws NoSuchAlgorithmException if the deadline of the block uses an unknown hashing algorithm
	 */
	static Block from(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		return AbstractBlock.from(context);
	}

	long getTotalWaitingTime();

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
	 * Yields the generation signature for the deadline of the next block.
	 * 
	 * @param hashing the hashing algorithm to use to compute the generation signature
	 * @return the generation signature
	 */
	byte[] getNewGenerationSignature(HashingAlgorithm<byte[]> hashing);

	/**
	 * Yields the scoop number for the deadline of the next block.
	 * 
	 * @param hashing the hashing algorithm to use to compute the scoop number
	 * @return the scoop number
	 */
	int getNewScoopNumber(HashingAlgorithm<byte[]> hashing);
}
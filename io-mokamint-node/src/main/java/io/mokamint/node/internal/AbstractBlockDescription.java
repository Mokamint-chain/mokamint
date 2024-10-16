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

/**
 * 
 */

package io.mokamint.node.internal;

import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;

/**
 * Shared code for block descriptions.
 */
public abstract sealed class AbstractBlockDescription extends AbstractMarshallable implements BlockDescription permits GenesisBlockDescriptionImpl, NonGenesisBlockDescriptionImpl {


	private final static BigInteger SCOOPS_PER_NONCE = BigInteger.valueOf(Deadline.MAX_SCOOP_NUMBER + 1);

	/**
	 * Creates a block description.
	 */
	protected AbstractBlockDescription() {
	}

	/**
	 * Unmarshals a block description from the given context.
	 * 
	 * @param context the context
	 * @param config the consensus configuration of the node storing the block description
	 * @return the block description
	 * @throws NoSuchAlgorithmException if some hashing or signature algorithm is not available
	 * @throws IOException if the block description cannot be unmarshalled
	 */
	public static BlockDescription from(UnmarshallingContext context, ConsensusConfig<?,?> config) throws NoSuchAlgorithmException, IOException {
		// by reading the height, we can determine if it's a genesis block description or not
		var height = context.readLong();
		if (height == 0L)
			return new GenesisBlockDescriptionImpl(context);
		else
			return new NonGenesisBlockDescriptionImpl(height, context, config);
	}

	@Override
	public final Challenge getNextChallenge(HashingAlgorithm hashingForGenerations, HashingAlgorithm hashingForDeadlines) {
		var nextGenerationSignature = getNextGenerationSignature(hashingForGenerations);
		return Challenges.of(getNextScoopNumber(nextGenerationSignature, hashingForGenerations), nextGenerationSignature, hashingForDeadlines, hashingForGenerations);
	}

	private int getNextScoopNumber(byte[] nextGenerationSignature, HashingAlgorithm hashingForGenerations) {
		var generationHash = hashingForGenerations.getHasher(Function.identity()).hash(concat(nextGenerationSignature, longToBytesBE(getHeight() + 1)));
		return new BigInteger(1, generationHash).remainder(SCOOPS_PER_NONCE).intValue();
	}

	private static byte[] concat(byte[] array1, byte[] array2) {
		var merge = new byte[array1.length + array2.length];
		System.arraycopy(array1, 0, merge, 0, array1.length);
		System.arraycopy(array2, 0, merge, array1.length, array2.length);
		return merge;
	}

	private static byte[] longToBytesBE(long l) {
		var target = new byte[8];
		for (int i = 0; i <= 7; i++)
			target[7 - i] = (byte) ((l >> (8 * i)) & 0xFF);

		return target;
	}

	/**
	 * Fills the given builder with information inside this description.
	 * 
	 * @param builder the builder
	 * @param config the configuration of the node, if available
	 * @param startDateTimeUTC the creation time of the genesis block of the chain of the block, if available
	 */
	protected void populate(StringBuilder builder, Optional<ConsensusConfig<?,?>> config, Optional<LocalDateTime> startDateTimeUTC) {
		builder.append("* height: " + getHeight() + "\n");
		builder.append("* power: " + getPower() + "\n");
		builder.append("* total waiting time: " + getTotalWaitingTime() + " ms\n");
		builder.append("* weighted waiting time: " + getWeightedWaitingTime() + " ms\n");
		config.map(ConsensusConfig::getHashingForGenerations).ifPresent(hashingForGenerations ->
			builder.append("* next generation signature: " + Hex.toHexString(getNextGenerationSignature(hashingForGenerations)) + " (" + hashingForGenerations + ")\n"));
		builder.append("* acceleration: " + getAcceleration());
	}

	/**
	 * Yields the generation signature of any block that can legally follow this block.
	 * 
	 * @param hashingForGenerations the hashing used for the generation of deadlines.
	 * @return the generation signature
	 */
	protected abstract byte[] getNextGenerationSignature(HashingAlgorithm hashingForGenerations);

	@Override
	public final String toString() {
		var builder = new StringBuilder();
		populate(builder, Optional.empty(), Optional.empty());
		return builder.toString();
	}

	@Override
	public final String toString(Optional<ConsensusConfig<?,?>> config, Optional<LocalDateTime> startDateTimeUTC) {
		var builder = new StringBuilder();
		populate(builder, config, startDateTimeUTC);
		return builder.toString();
	}
}
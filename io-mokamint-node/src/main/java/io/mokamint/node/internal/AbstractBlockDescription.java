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
import java.util.Objects;
import java.util.function.Function;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.internal.gson.BlockDescriptionJson;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.api.Challenge;

/**
 * Shared code for block descriptions.
 */
public abstract sealed class AbstractBlockDescription extends AbstractMarshallable implements BlockDescription permits GenesisBlockDescriptionImpl, NonGenesisBlockDescriptionImpl {

	/**
	 * The target time for the creation of the blocks, in milliseconds.
	 */
	private final int targetBlockCreationTime;

	/**
	 * The hashing algorithm used for the blocks.
	 */
	private final HashingAlgorithm hashingForBlocks;

	/**
	 * The hashing algorithm used for the transactions in the block.
	 */
	private final HashingAlgorithm hashingForTransactions;

	/**
	 * Creates a block description.
	 * 
	 * @param targetBlockCreationTime the target time for the creation of the blocks, in milliseconds
	 * @param hashingForBlocks the hashing algorithm used for the blocks
	 * @param hashingForTransactions the hashing algorithm used for the transactions
	 */
	protected AbstractBlockDescription(int targetBlockCreationTime, HashingAlgorithm hashingForBlocks, HashingAlgorithm hashingForTransactions) {
		if (targetBlockCreationTime <= 0)
			throw new IllegalArgumentException("The target block creation time must be positive");
	
		this.targetBlockCreationTime = targetBlockCreationTime;
		this.hashingForBlocks = Objects.requireNonNull(hashingForBlocks);
		this.hashingForTransactions = Objects.requireNonNull(hashingForTransactions);
	}

	/**
	 * Unmarshals a block description. It assumes that the description was marshalled
	 * by using {@link BlockDescription#into(MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @throws IOException if unmarshalling failed
	 * @throws NoSuchAlgorithmException if some cryptographic algorithm is not available
	 */
	protected AbstractBlockDescription(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		this.targetBlockCreationTime = context.readCompactInt();
		if (targetBlockCreationTime <= 0)
			throw new IOException("The target block creation time must be positive");

		this.hashingForBlocks = HashingAlgorithms.of(context.readStringShared());
		this.hashingForTransactions = HashingAlgorithms.of(context.readStringShared());
	}

	/**
	 * Creates a block description from the given consensus configuration.
	 * 
	 * @param config the consensus configuration
	 */
	protected AbstractBlockDescription(ConsensusConfig<?,?> config) {
		this.targetBlockCreationTime = config.getTargetBlockCreationTime();
		this.hashingForBlocks = config.getHashingForBlocks();
		this.hashingForTransactions = config.getHashingForTransactions();
	}

	/**
	 * Creates a block description from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 * @throws NoSuchAlgorithmException if the JSON refers to an unknown hashing algorithm
	 */
	protected AbstractBlockDescription(BlockDescriptionJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
		int targetBlockCreationTime = json.getTargetBlockCreationTime();
		if (targetBlockCreationTime <= 0)
			throw new InconsistentJsonException("The target block creation time must be positive");
	
		this.targetBlockCreationTime = targetBlockCreationTime;

		String hashingForBlocks = json.getHashingForBlocks();
		if (hashingForBlocks == null)
			throw new InconsistentJsonException("hashingForBlocks cannot be null");

		this.hashingForBlocks = HashingAlgorithms.of(hashingForBlocks);

		String hashingForTransactions = json.getHashingForTransactions();
		if (hashingForTransactions == null)
			throw new InconsistentJsonException("hashingForTransactions cannot be null");

		this.hashingForTransactions = HashingAlgorithms.of(hashingForTransactions);
	}

	/**
	 * Unmarshals a block description from the given context. It assumes that it was marshalled by using
	 * {@link BlockDescription#intoWithoutConfigurationData(io.hotmoka.marshalling.api.MarshallingContext)}.
	 * 
	 * @param context the context
	 * @param config the consensus configuration of the node storing the block description
	 * @return the block description
	 * @throws IOException if the block description cannot be unmarshalled
	 */
	public static BlockDescription from(UnmarshallingContext context, ConsensusConfig<?,?> config) throws IOException {
		// by reading the height, we can determine if it's a genesis block description or not
		var height = context.readCompactLong();
		return height == 0L ? new GenesisBlockDescriptionImpl(context, config) : new NonGenesisBlockDescriptionImpl(height, context, config);
	}

	/**
	 * Yields a block description from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @return the block
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 * @throws NoSuchAlgorithmException if {@code json} refers to some unknown cryptographic algorithm
	 */
	public static BlockDescription from(BlockDescriptionJson json) throws NoSuchAlgorithmException, InconsistentJsonException {
		String startDateTimeUTC = json.getStartDateTimeUTC();
		return startDateTimeUTC == null ? new NonGenesisBlockDescriptionImpl(json) : new GenesisBlockDescriptionImpl(json);
	}

	/**
	 * Unmarshals a block description from the given context. It assumes that it was marshalled by using
	 * {@link BlockDescription#into(io.hotmoka.marshalling.api.MarshallingContext)}.
	 * 
	 * @param context the context
	 * @return the block description
	 * @throws IOException if the block description cannot be unmarshalled
	 * @throws NoSuchAlgorithmException if the block description refers to an unknown cryptographic algorithm
	 */
	public static BlockDescription from(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		// by reading the height, we can determine if it's a genesis block description or not
		var height = context.readCompactLong();
		return height == 0L ? new GenesisBlockDescriptionImpl(context) : new NonGenesisBlockDescriptionImpl(height, context);
	}

	@Override
	public final int getTargetBlockCreationTime() {
		return targetBlockCreationTime;
	}

	@Override
	public final HashingAlgorithm getHashingForBlocks() {
		return hashingForBlocks;
	}

	@Override
	public final HashingAlgorithm getHashingForTransactions() {
		return hashingForTransactions;
	}

	@Override
	public final Challenge getNextChallenge() {
		var nextGenerationSignature = getNextGenerationSignature();
		var hashingForGenerations = getHashingForGenerations();
		var generationHash = hashingForGenerations.getHasher(Function.identity()).hash(concat(nextGenerationSignature, longToBytesBE(getHeight() + 1)));
		int nextScoopNumber = new BigInteger(1, generationHash).remainder(BigInteger.valueOf(Challenge.SCOOPS_PER_NONCE)).intValue();
		return Challenges.of(nextScoopNumber, nextGenerationSignature, getHashingForDeadlines(), hashingForGenerations);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof AbstractBlockDescription abd &&
			targetBlockCreationTime == abd.getTargetBlockCreationTime() &&
			hashingForBlocks.equals(abd.getHashingForBlocks()) &&
			hashingForTransactions.equals(abd.getHashingForTransactions());
	}

	@Override
	public int hashCode() {
		return targetBlockCreationTime ^ hashingForBlocks.hashCode() ^ hashingForTransactions.hashCode();
	}

	@Override
	public final String toString() {
		var builder = new StringBuilder();
		populate(builder);
		return builder.toString();
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		context.writeCompactLong(getHeight());
		context.writeCompactInt(targetBlockCreationTime);
		context.writeStringShared(hashingForBlocks.getName());
		context.writeStringShared(hashingForTransactions.getName());
	}

	@Override
	public void intoWithoutConfigurationData(MarshallingContext context) throws IOException {
		context.writeCompactLong(getHeight());
	}

	/**
	 * Fills the given builder with information inside this description.
	 * 
	 * @param builder the builder
	 */
	protected void populate(StringBuilder builder) {
		builder.append("* height: " + getHeight() + "\n");
		builder.append("* power: " + getPower() + "\n");
		builder.append("* total waiting time: " + getTotalWaitingTime() + " ms\n");
		builder.append("* weighted waiting time: " + getWeightedWaitingTime() + " ms (target is " + targetBlockCreationTime + " ms)\n");
		builder.append("* next generation signature: " + Hex.toHexString(getNextGenerationSignature()) + " (" + getHashingForGenerations() + ")\n");
		builder.append("* acceleration: " + getAcceleration());
	}

	/**
	 * Yields the generation signature of any block that can legally follow this block.
	 * 
	 * @return the generation signature
	 */
	protected abstract byte[] getNextGenerationSignature();

	protected static byte[] concat(byte[] array1, byte[] array2) {
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
}
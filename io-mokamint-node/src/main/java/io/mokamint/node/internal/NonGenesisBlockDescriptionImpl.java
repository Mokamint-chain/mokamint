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

package io.mokamint.node.internal;

import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;

/**
 * The implementation of the description of a non-genesis block of the Mokamint blockchain.
 */
@Immutable
public non-sealed class NonGenesisBlockDescriptionImpl extends AbstractBlockDescription implements NonGenesisBlockDescription {

	/**
	 * The block height, non-negative, counting from 0, which is the genesis block.
	 */
	private final long height;

	/**
	 * The power of this block, computed as the sum, for each block from genesis to this
	 * block, of 2^(hashing bits) / (value of the deadline in the block + 1). This allows one to compare
	 * forks and choose the one whose tip has the highest power. Intuitively, the power
	 * expresses the space used to compute the chain leading to the block.
	 */
	private final BigInteger power;

	/**
	 * The total waiting time between the creation of the genesis block and the creation of this block.
	 */
	private final long totalWaitingTime;

	/**
	 * The weighted waiting time between the creation of the genesis block and the creation of this block.
	 */
	private final long weightedWaitingTime;

	/**
	 * A value used to divide the deadline to derive the time needed to wait for it.
	 * The higher, the shorter the time. This value changes dynamically to cope with
	 * varying mining power in the network. It is similar to Bitcoin's difficulty.
	 */
	private final BigInteger acceleration;

	/**
	 * The deadline computed for this block.
	 */
	private final Deadline deadline;

	/**
	 * The reference to the previous block.
	 */
	private final byte[] hashOfPreviousBlock;

	/**
	 * Creates a new non-genesis block description.
	 */
	public NonGenesisBlockDescriptionImpl(long height, BigInteger power, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration, Deadline deadline, byte[] hashOfPreviousBlock, int targetBlockCreationTime, HashingAlgorithm hashingForBlocks, HashingAlgorithm hashingForTransactions) {
		super(targetBlockCreationTime, hashingForBlocks, hashingForTransactions);

		this.height = height;
		this.power = power;
		this.totalWaitingTime = totalWaitingTime;
		this.weightedWaitingTime = weightedWaitingTime;
		this.acceleration = acceleration;
		this.deadline = deadline;
		this.hashOfPreviousBlock = hashOfPreviousBlock;

		verify();
	}

	/**
	 * Unmarshals a non-genesis block. The height of the block has been already read.
	 * It assumes that the description was marshalled by using
	 * {@link BlockDescription#intoWithoutConfigurationData(MarshallingContext)}.
	 * 
	 * @param height the height of the block
	 * @param context the unmarshalling context
	 * @param config the consensus configuration of the node storing the block description
	 * @throws IOException if unmarshalling failed
	 */
	NonGenesisBlockDescriptionImpl(long height, UnmarshallingContext context, ConsensusConfig<?,?> config) throws IOException {
		super(config.getTargetBlockCreationTime(), config.getHashingForBlocks(), config.getHashingForTransactions());

		this.height = height;

		try {
			this.power = context.readBigInteger();
			this.totalWaitingTime = context.readLong();
			this.weightedWaitingTime = context.readCompactLong();
			this.acceleration = context.readBigInteger();
			this.deadline = Deadlines.from(context, config.getChainId(), config.getHashingForDeadlines(), config.getHashingForGenerations(), config.getSignatureForBlocks(), config.getSignatureForDeadlines());
			this.hashOfPreviousBlock = context.readBytes(getHashingForBlocks().length(), "Previous block hash length mismatch");

			verify();
		}
		catch (RuntimeException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Unmarshals a non-genesis block. The height of the block has been already read.
	 * It assumes that the description was marshalled by using
	 * {@link BlockDescription#into(MarshallingContext)}.
	 * 
	 * @param height the height of the block
	 * @param context the unmarshalling context
	 * @param config the consensus configuration of the node storing the block description
	 * @throws IOException if unmarshalling failed
	 * @throws NoSuchAlgorithmException if the block description refers to an unknown cryptographic algorithm
	 */
	NonGenesisBlockDescriptionImpl(long height, UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		super(context.readCompactInt(), HashingAlgorithms.of(context.readStringShared()), HashingAlgorithms.of(context.readStringShared()));

		this.height = height;

		try {
			this.power = context.readBigInteger();
			this.totalWaitingTime = context.readLong();
			this.weightedWaitingTime = context.readCompactLong();
			this.acceleration = context.readBigInteger();
			this.deadline = Deadlines.from(context);
			this.hashOfPreviousBlock = context.readBytes(getHashingForBlocks().length(), "Previous block hash length mismatch");

			verify();
		}
		catch (RuntimeException e) {
			throw new IOException(e);
		}
	}

	@Override
	public BigInteger getPower() {
		return power;
	}

	@Override
	public long getTotalWaitingTime() {
		return totalWaitingTime;
	}

	@Override
	public long getWeightedWaitingTime() {
		return weightedWaitingTime;
	}

	@Override
	public BigInteger getAcceleration() {
		return acceleration;
	}

	@Override
	public long getHeight() {
		return height;
	}

	@Override
	public HashingAlgorithm getHashingForDeadlines() {
		return deadline.getChallenge().getHashingForDeadlines();
	}

	@Override
	public HashingAlgorithm getHashingForGenerations() {
		return deadline.getChallenge().getHashingForGenerations();
	}

	@Override
	public SignatureAlgorithm getSignatureForBlock() {
		return deadline.getProlog().getSignatureForBlocks();
	}

	@Override
	public PublicKey getPublicKeyForSigningBlock() {
		return deadline.getProlog().getPublicKeyForSigningBlocks();
	}

	@Override
	public String getPublicKeyForSigningBlockBase58() {
		return deadline.getProlog().getPublicKeyForSigningBlocksBase58();
	}

	@Override
	public Deadline getDeadline() {
		return deadline;
	}

	@Override
	public byte[] getHashOfPreviousBlock() {
		return hashOfPreviousBlock.clone();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof NonGenesisBlockDescription ngbd &&
			height == ngbd.getHeight() &&
			power.equals(ngbd.getPower()) &&
			totalWaitingTime == ngbd.getTotalWaitingTime() &&
			weightedWaitingTime == ngbd.getWeightedWaitingTime() &&
			acceleration.equals(ngbd.getAcceleration()) &&
			deadline.equals(ngbd.getDeadline()) &&
			getHashingForBlocks().equals(ngbd.getHashingForBlocks()) &&
			Arrays.equals(hashOfPreviousBlock, other instanceof NonGenesisBlockDescriptionImpl ngbdi ? ngbdi.hashOfPreviousBlock : ngbd.getHashOfPreviousBlock());
	}

	@Override
	public int hashCode() {
		return ((int) height) ^ power.hashCode() ^ ((int) totalWaitingTime) ^ ((int) weightedWaitingTime) ^ acceleration.hashCode() ^ deadline.hashCode();
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		context.writeLong(height);
		context.writeCompactInt(getTargetBlockCreationTime());
		context.writeStringShared(getHashingForBlocks().getName());
		context.writeStringShared(getHashingForTransactions().getName());
		context.writeBigInteger(power);
		context.writeLong(totalWaitingTime);
		context.writeCompactLong(weightedWaitingTime);
		context.writeBigInteger(acceleration);
		deadline.into(context);
		context.writeBytes(hashOfPreviousBlock);
	}

	@Override
	public void intoWithoutConfigurationData(MarshallingContext context) throws IOException {
		context.writeLong(height);
		context.writeBigInteger(power);
		context.writeLong(totalWaitingTime);
		context.writeCompactLong(weightedWaitingTime);
		context.writeBigInteger(acceleration);
		deadline.intoWithoutConfigurationData(context);
		context.writeBytes(hashOfPreviousBlock);
	}

	@Override
	protected void populate(StringBuilder builder, Optional<LocalDateTime> startDateTimeUTC) {
		startDateTimeUTC.ifPresent(sd -> builder.append("* creation date and time UTC: " + sd.plus(getTotalWaitingTime(), ChronoUnit.MILLIS) + "\n"));
		super.populate(builder, startDateTimeUTC);
		builder.append("\n* hash of previous block: " + Hex.toHexString(hashOfPreviousBlock) + " (" + getHashingForBlocks() + ")");
		builder.append("\n");
		builder.append("* deadline:\n");
		builder.append("  * prolog:\n");
		var prolog = deadline.getProlog();
		builder.append("    * chain identifier: " + prolog.getChainId() + "\n");
		builder.append("    * public key of the node that signed the block: " + prolog.getPublicKeyForSigningBlocksBase58() + " (" + prolog.getSignatureForBlocks() + ", base58)\n");
		builder.append("    * public key of the miner that signed the deadline: " + prolog.getPublicKeyForSigningDeadlinesBase58() + " (" + prolog.getSignatureForDeadlines() + ", base58)\n");
		builder.append("    * extra: " + Hex.toHexString(prolog.getExtra()) + "\n");
		builder.append("  * challenge:\n");
		var challenge = deadline.getChallenge();
		builder.append("    * scoopNumber: " + challenge.getScoopNumber() + "\n");
		builder.append("    * generation signature: " + Hex.toHexString(challenge.getGenerationSignature()) + " (" + challenge.getHashingForGenerations() + ")");
		builder.append("\n");
		builder.append("  * nonce: " + deadline.getProgressive() + "\n");
		builder.append("  * value: " + Hex.toHexString(deadline.getValue()) + " (" + challenge.getHashingForDeadlines() + ")\n");
		builder.append("  * miner's signature: " + Hex.toHexString(deadline.getSignature()) + " (" + prolog.getSignatureForDeadlines() + ")");
	}

	@Override
	protected byte[] getNextGenerationSignature() {
		var challenge = deadline.getChallenge();
		byte[] previousGenerationSignature = challenge.getGenerationSignature();
		byte[] previousProlog = deadline.getProlog().toByteArray();
		return challenge.getHashingForGenerations().getHasher(Function.identity()).hash(concat(previousGenerationSignature, previousProlog));
	}

	/**
	 * Checks all constraints expected from a non-genesis block.
	 * 
	 * @throws NullPointerException if some value is unexpectedly {@code null}
	 * @throws IllegalArgumentException if some value is illegal
	 */
	private void verify() {
		Objects.requireNonNull(acceleration, "acceleration cannot be null");
		Objects.requireNonNull(deadline, "deadline cannot be null");
		Objects.requireNonNull(hashOfPreviousBlock, "hashOfPreviousBlock cannot be null");
		Objects.requireNonNull(power, "power cannot be null");
	
		if (height < 1)
			throw new IllegalArgumentException("A non-genesis block must have positive height");
	
		if (power.signum() < 0)
			throw new IllegalArgumentException("The power cannot be negative");
	
		if (acceleration.signum() <= 0)
			throw new IllegalArgumentException("The acceleration must be strictly positive");
	
		if (weightedWaitingTime < 0)
			throw new IllegalArgumentException("The weighted waiting time cannot be negative");
	
		if (totalWaitingTime < weightedWaitingTime)
			throw new IllegalArgumentException("The total waiting time cannot be smaller than the weighted waiting time");

		if (hashOfPreviousBlock.length != getHashingForBlocks().length())
			throw new IllegalArgumentException("Length mismatch in the hash of the previous block: expected " + getHashingForBlocks().length() + " but found " + hashOfPreviousBlock.length);
	}

	private static byte[] concat(byte[] array1, byte[] array2) {
		var merge = new byte[array1.length + array2.length];
		System.arraycopy(array1, 0, merge, 0, array1.length);
		System.arraycopy(array2, 0, merge, array1.length, array2.length);
		return merge;
	}
}
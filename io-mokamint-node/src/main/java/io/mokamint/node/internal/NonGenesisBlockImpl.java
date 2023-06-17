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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;

/**
 * The implementation of a non-genesis block of the Mokamint blockchain.
 */
public class NonGenesisBlockImpl extends AbstractBlock implements NonGenesisBlock {

	/**
	 * The block height, non-negative, counting from 0, which is the genesis block.
	 */
	private final long height;

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
	 * varying mining power in the network. It is the inverse of Bitcoin's difficulty.
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
	 * Creates a new non-genesis block.
	 */
	public NonGenesisBlockImpl(long height, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration, Deadline deadline, byte[] hashOfPreviousBlock) {
		Objects.requireNonNull(acceleration, "acceleration cannot be null");
		Objects.requireNonNull(deadline, "deadline cannot be null");
		Objects.requireNonNull(hashOfPreviousBlock, "hashOfPreviousBlock cannot be null");

		this.height = height;
		this.totalWaitingTime = totalWaitingTime;
		this.weightedWaitingTime = weightedWaitingTime;
		this.acceleration = acceleration;
		this.deadline = deadline;
		this.hashOfPreviousBlock = hashOfPreviousBlock;
	}

	/**
	 * Unmarshals a non-genesis block from the given context.
	 * The height of the block has been already read.
	 * 
	 * @param height the height of the block
	 * @param context the context
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws IOException if the block could not be unmarshalled
	 */
	NonGenesisBlockImpl(long height, UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		this.height = height;
		this.totalWaitingTime = context.readLong();
		this.weightedWaitingTime = context.readLong();
		this.acceleration = context.readBigInteger();
		this.deadline = Deadlines.from(context);
		int hashOfPreviousBlockLength = context.readCompactInt();
		this.hashOfPreviousBlock = context.readBytes(hashOfPreviousBlockLength, "previous block hash length mismatch");
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
	public Deadline getDeadline() {
		return deadline;
	}

	@Override
	public byte[] getHashOfPreviousBlock() {
		return hashOfPreviousBlock;
	}

	@Override
	protected byte[] getNextGenerationSignature(HashingAlgorithm<byte[]> hashing) {
		byte[] previousGenerationSignature = deadline.getData();
		byte[] previousProlog = deadline.getProlog();
		return hashing.hash(concat(previousGenerationSignature, previousProlog));
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof NonGenesisBlock) {
			var otherAsNGB = (NonGenesisBlock) other;
			return height == otherAsNGB.getHeight() &&
				totalWaitingTime == otherAsNGB.getTotalWaitingTime() &&
				weightedWaitingTime == otherAsNGB.getWeightedWaitingTime() &&
				acceleration.equals(otherAsNGB.getAcceleration()) &&
				deadline.equals(otherAsNGB.getDeadline()) &&
				Arrays.equals(hashOfPreviousBlock, otherAsNGB.getHashOfPreviousBlock());
		}

		return false;
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		// we write the height of the block first, so that, by reading the first long,
		// it is possible to distinguish between a genesis block (height == 0)
		// and a non-genesis block (height > 0)
		context.writeLong(height);
		context.writeLong(totalWaitingTime);
		context.writeLong(weightedWaitingTime);
		context.writeBigInteger(acceleration);
		deadline.into(context);
		context.writeCompactInt(hashOfPreviousBlock.length);
		context.write(hashOfPreviousBlock);
	}

	@Override
	public String toString() {
		var builder = new StringBuilder("Block:\n");
		populate(builder);
		
		return builder.toString();
	}

	private void populate(StringBuilder builder) {
		builder.append("* height: " + getHeight() + "\n");
		builder.append("* total waiting time: " + getTotalWaitingTime() + "ms\n");
		builder.append("* weighted waiting time: " + getWeightedWaitingTime() + "ms\n");
		builder.append("* acceleration: " + getAcceleration() + "\n");
		builder.append("* hash of previous block: " + Hex.toHexString(hashOfPreviousBlock) + "\n");
		builder.append("* deadline: " + deadline);
	}

	@Override
	public String toString(ConsensusConfig config, LocalDateTime startDateTimeUTC) {
		var builder = new StringBuilder("Block:\n");
		builder.append("* creation date and time UTC: " + startDateTimeUTC.plus(totalWaitingTime, ChronoUnit.MILLIS) + "\n");
		builder.append("* hash: " + Hex.toHexString(config.getHashingForBlocks().hash(toByteArray())) + "\n");
		populate(builder);
		builder.append("\n");
		builder.append("* next generation signature: " + Hex.toHexString(getNextGenerationSignature(config.getHashingForGenerations())));
		return builder.toString();
	}
}
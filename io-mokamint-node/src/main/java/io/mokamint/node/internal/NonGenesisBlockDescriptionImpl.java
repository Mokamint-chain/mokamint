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
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

import io.hotmoka.crypto.Hex;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;

/**
 * The implementation of the description of a non-genesis block of the Mokamint blockchain.
 */
public class NonGenesisBlockDescriptionImpl extends AbstractMarshallable implements NonGenesisBlockDescription {

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
	public NonGenesisBlockDescriptionImpl(long height, BigInteger power, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration, Deadline deadline, byte[] hashOfPreviousBlock) {
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
	 * 
	 * param height the height of the block
	 * @param context the unmarshalling context
	 * @throws IOException if unmarshalling failed
	 * @throws NoSuchAlgorithmException if the block uses some unknown signature or hashing algorithm
	 */
	NonGenesisBlockDescriptionImpl(long height, UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		this.height = height;

		try {
			this.power = context.readBigInteger();
			this.totalWaitingTime = context.readLong();
			this.weightedWaitingTime = context.readLong();
			this.acceleration = context.readBigInteger();
			this.deadline = Deadlines.from(context);
			this.hashOfPreviousBlock = context.readBytes(context.readCompactInt(), "Previous block hash length mismatch");

			verify();
		}
		catch (RuntimeException e) {
			throw new IOException(e);
		}
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
	public Deadline getDeadline() {
		return deadline;
	}

	@Override
	public byte[] getHashOfPreviousBlock() {
		return hashOfPreviousBlock;
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
			Arrays.equals(hashOfPreviousBlock, ngbd.getHashOfPreviousBlock());
	}

	@Override
	public int hashCode() {
		return ((int) height) ^ power.hashCode() ^ ((int) totalWaitingTime) ^ ((int) weightedWaitingTime) ^ acceleration.hashCode() ^ deadline.hashCode();
	}

	@Override
	public <E extends Exception> void matchesOrThrow(NonGenesisBlockDescription description, Function<String, E> exceptionSupplier) throws E {
		if (height != description.getHeight())
			throw exceptionSupplier.apply("Height mismatch (expected " + description.getHeight() + " but found " + height + ")");

		if (!acceleration.equals(description.getAcceleration()))
			throw exceptionSupplier.apply("Acceleration mismatch (expected " + description.getAcceleration() + " but found " + acceleration + ")");

		if (!power.equals(description.getPower()))
			throw exceptionSupplier.apply("Power mismatch (expected " + description.getPower() + " but found " + power + ")");

		if (totalWaitingTime != description.getTotalWaitingTime())
			throw exceptionSupplier.apply("Total waiting time mismatch (expected " + description.getTotalWaitingTime() + " but found " + totalWaitingTime + ")");

		if (weightedWaitingTime != description.getWeightedWaitingTime())
			throw exceptionSupplier.apply("Weighted waiting time mismatch (expected " + description.getWeightedWaitingTime() + " but found " + weightedWaitingTime + ")");

		if (!Arrays.equals(hashOfPreviousBlock, description.getHashOfPreviousBlock()))
			throw exceptionSupplier.apply("Hash of previous block mismatch");
	}

	@Override
	public String toString() {
		var builder = new StringBuilder("Block:\n");
		populate(builder);
		
		return builder.toString();
	}

	private void populate(StringBuilder builder) {
		builder.append("* height: " + getHeight() + "\n");
		builder.append("* power: " + getPower() + "\n");
		builder.append("* total waiting time: " + getTotalWaitingTime() + " ms\n");
		builder.append("* weighted waiting time: " + getWeightedWaitingTime() + " ms\n");
		builder.append("* acceleration: " + getAcceleration() + "\n");
		builder.append("* hash of previous block: " + Hex.toHexString(hashOfPreviousBlock) + "\n");
		builder.append("* deadline:\n");
		builder.append("  * prolog:\n");
		var prolog = deadline.getProlog();
		builder.append("    * chain identifier: " + prolog.getChainId() + "\n");
		builder.append("    * node's public key: " + prolog.getPublicKeyForSigningBlocksBase58() + "\n");
		builder.append("    * plot's public key: " + prolog.getPublicKeyForSigningDeadlinesBase58() + "\n");
		builder.append("    * extra: " + Hex.toHexString(prolog.getExtra()) + "\n");
		builder.append("  * scoopNumber: " + deadline.getScoopNumber() + "\n");
		builder.append("  * generation signature: " + Hex.toHexString(deadline.getData()) + "\n");
		builder.append("  * nonce: " + deadline.getProgressive() + "\n");
		builder.append("  * value: " + Hex.toHexString(deadline.getValue()));
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		context.writeBigInteger(power);
		context.writeLong(totalWaitingTime);
		context.writeLong(weightedWaitingTime);
		context.writeBigInteger(acceleration);
		deadline.into(context);
		context.writeCompactInt(hashOfPreviousBlock.length);
		context.write(hashOfPreviousBlock);
	}
}
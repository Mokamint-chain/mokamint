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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;

/**
 * The implementation of a non-genesis block of the Mokamint blockchain.
 */
@Immutable
public class NonGenesisBlockImpl extends AbstractBlock implements NonGenesisBlock {

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
	 * The signature of this node.
	 */
	private final byte[] signature;

	/**
	 * Creates a new non-genesis block. It adds a signature to the resulting block,
	 * by using the signature algorithm in the prolog of the deadline and the given private key.
	 * 
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public NonGenesisBlockImpl(long height, BigInteger power, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration,
			Deadline deadline, byte[] hashOfPreviousBlock, PrivateKey key) throws InvalidKeyException, SignatureException {

		this.height = height;
		this.power = power;
		this.totalWaitingTime = totalWaitingTime;
		this.weightedWaitingTime = weightedWaitingTime;
		this.acceleration = acceleration;
		this.deadline = deadline;
		this.hashOfPreviousBlock = hashOfPreviousBlock.clone();
		this.signature = deadline.getProlog().getSignatureForBlocks().getSigner(key, NonGenesisBlockImpl::toByteArrayWithoutSignature).sign(this);

		verify();
	}

	/**
	 * Creates a new non-genesis block.
	 */
	public NonGenesisBlockImpl(long height, BigInteger power, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration, Deadline deadline, byte[] hashOfPreviousBlock, byte[] signature) {
		this.height = height;
		this.power = power;
		this.totalWaitingTime = totalWaitingTime;
		this.weightedWaitingTime = weightedWaitingTime;
		this.acceleration = acceleration;
		this.deadline = deadline;
		this.hashOfPreviousBlock = hashOfPreviousBlock.clone();
		this.signature = signature.clone();

		verify();
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
		try {
			this.height = height;
			this.power = context.readBigInteger();
			this.totalWaitingTime = context.readLong();
			this.weightedWaitingTime = context.readLong();
			this.acceleration = context.readBigInteger();
			this.deadline = Deadlines.from(context);
			this.hashOfPreviousBlock = context.readBytes(context.readCompactInt(), "Previous block hash length mismatch");
			this.signature = context.readBytes(context.readCompactInt(), "Signature length mismatch");

			verify();
		}
		catch (RuntimeException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Checks all constraints expected from a non-genesis block, including the
	 * validity of its signature.
	 * 
	 * @throws NullPointerException if some value is unexpectedly {@code null}
	 * @throws IllegalArgumentException if some value is illegal
	 */
	private void verify() {
		Objects.requireNonNull(acceleration, "acceleration cannot be null");
		Objects.requireNonNull(deadline, "deadline cannot be null");
		Objects.requireNonNull(hashOfPreviousBlock, "hashOfPreviousBlock cannot be null");
		Objects.requireNonNull(power, "power cannot be null");
		Objects.requireNonNull(signature, "signature cannot be null");

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

		var prolog = deadline.getProlog();

		try {
			if (!prolog.getSignatureForBlocks().getVerifier(prolog.getPublicKeyForSigningBlocks(), NonGenesisBlockImpl::toByteArrayWithoutSignature).verify(this, signature))
				throw new IllegalArgumentException("The block's signature is invalid");
		}
		catch (SignatureException e) {
			throw new IllegalArgumentException("The block's signature cannot be verified", e);
		}
		catch (InvalidKeyException e) {
			throw new IllegalArgumentException("The public key in the prolog of the deadline of the block is invalid", e);
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
	public Deadline getDeadline() {
		return deadline;
	}

	@Override
	public byte[] getHashOfPreviousBlock() {
		return hashOfPreviousBlock.clone();
	}

	@Override
	public byte[] getSignature() {
		return signature.clone();
	}

	@Override
	protected byte[] getNextGenerationSignature(HashingAlgorithm hashingForGenerations) {
		byte[] previousGenerationSignature = deadline.getData();
		byte[] previousProlog = deadline.getProlog().toByteArray();
		return hashingForGenerations.getHasher(Function.identity()).hash(concat(previousGenerationSignature, previousProlog));
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof NonGenesisBlock ngb &&
			height == ngb.getHeight() &&
			power.equals(ngb.getPower()) &&
			totalWaitingTime == ngb.getTotalWaitingTime() &&
			weightedWaitingTime == ngb.getWeightedWaitingTime() &&
			acceleration.equals(ngb.getAcceleration()) &&
			deadline.equals(ngb.getDeadline()) &&
			Arrays.equals(hashOfPreviousBlock, ngb.getHashOfPreviousBlock()) &&
			Arrays.equals(signature, ngb.getSignature());
	}

	@Override
	public int hashCode() {
		return ((int) height) ^ power.hashCode() ^ ((int) totalWaitingTime) ^ ((int) weightedWaitingTime) ^ acceleration.hashCode() ^ deadline.hashCode();
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		intoWithoutSignature(context);
		context.writeCompactInt(signature.length);
		context.write(signature);
	}

	/**
	 * Marshals this block into the given context, without its signature.
	 * 
	 * @param context the context
	 * @throws IOException if marshalling fails
	 */
	private void intoWithoutSignature(MarshallingContext context) throws IOException {
		// we write the height of the block first, so that, by reading the first long,
		// it is possible to distinguish between a genesis block (height == 0)
		// and a non-genesis block (height > 0)
		context.writeLong(height);
		context.writeBigInteger(power);
		context.writeLong(totalWaitingTime);
		context.writeLong(weightedWaitingTime);
		context.writeBigInteger(acceleration);
		deadline.into(context);
		context.writeCompactInt(hashOfPreviousBlock.length);
		context.write(hashOfPreviousBlock);
	}

	/**
	 * Yields a marshalling of this object into a byte array, without considering
	 * its signature.
	 * 
	 * @return the marshalled bytes
	 */
	private byte[] toByteArrayWithoutSignature() {
		try (var baos = new ByteArrayOutputStream(); var context = createMarshallingContext(baos)) {
			intoWithoutSignature(context);
			context.flush();
			return baos.toByteArray();
		}
		catch (IOException e) {
			// impossible with a ByteArrayOutputStream
			throw new RuntimeException("Unexpected exception", e);
		}
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
		builder.append("* signature: " + Hex.toHexString(signature) + "\n");
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
		builder.append("  * value: " + Hex.toHexString(deadline.getValue()) + "\n");
	}

	@Override
	public String toString(ConsensusConfig<?,?> config, LocalDateTime startDateTimeUTC) {
		var builder = new StringBuilder("Block:\n");
		builder.append("* creation date and time UTC: " + startDateTimeUTC.plus(totalWaitingTime, ChronoUnit.MILLIS) + "\n");
		builder.append("* hash: " + getHexHash(config.getHashingForBlocks()) + "\n");
		populate(builder);
		builder.append("* next generation signature: " + Hex.toHexString(getNextGenerationSignature(config.getHashingForGenerations())));
		return builder.toString();
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
}
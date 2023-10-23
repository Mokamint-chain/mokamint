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
import java.security.PublicKey;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;

/**
 * Shared code of all classes implementing blocks.
 */
public abstract class AbstractBlock extends AbstractMarshallable implements Block {

	/**
	 * The description of this block.
	 */
	private final AbstractBlockDescription description;

	/**
	 * A lock for the {@link #lastHash} and {@link #lastHashingName} fields.
	 */
	private final Object lock = new Object();

	/**
	 * The hashing algorithm used for the last call to {@link #getHash(HashingAlgorithm)}, if any.
	 */
	@GuardedBy("lock")
	private HashingAlgorithm lastHashing;

	/**
	 * The result of the last call to {@link #getHash(HashingAlgorithm)}.
	 */
	@GuardedBy("lock")
	private byte[] lastHash;

	private final static BigInteger SCOOPS_PER_NONCE = BigInteger.valueOf(Deadline.MAX_SCOOP_NUMBER + 1);

	private final static BigInteger _20 = BigInteger.valueOf(20L);

	private final static BigInteger _100 = BigInteger.valueOf(100L);

	/**
	 * Creates an abstract block with the given description.
	 * 
	 * @param description the description of the block
	 */
	protected AbstractBlock(AbstractBlockDescription description) {
		this.description = description;
	}

	/**
	 * Computes the signature of this block. It is compute from its marshalling, without the signature itself.
	 * 
	 * @throws SignatureException if the computation of the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	protected final byte[] computeSignature(PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		return getSignatureForBlocks().getSigner(privateKey, AbstractBlock::toByteArrayWithoutSignature).sign(this);
	}

	/**
	 * Checks all constraints expected from this block.
	 * 
	 * @throws NullPointerException if some value is unexpectedly {@code null}
	 * @throws IllegalArgumentException if some value is illegal
	 */
	protected final void verify() {
		try {
			if (!getSignatureForBlocks().getVerifier(getPublicKeyForSigningThisBlock(), AbstractBlock::toByteArrayWithoutSignature).verify(this, getSignature()))
				throw new IllegalArgumentException("The block's signature is invalid");
		}
		catch (SignatureException e) {
			throw new IllegalArgumentException("The block's signature cannot be verified", e);
		}
		catch (InvalidKeyException e) {
			throw new IllegalArgumentException("The public key in the prolog of the deadline of the block is invalid", e);
		}
	}

	/**
	 * Yields the description of this block.
	 * 
	 * @return the description
	 */
	protected AbstractBlockDescription getDescription() {
		return description;
	}

	/**
	 * Unmarshals a block from the given context.
	 * 
	 * @param context the context
	 * @return the block
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws IOException if the block cannot be unmarshalled
	 */
	public static Block from(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		// by reading the height, we can determine if it's a genesis block or not
		var height = context.readLong();
		if (height == 0L)
			return new GenesisBlockImpl(context);
		else
			return new NonGenesisBlockImpl(height, context);
	}

	@Override
	public BigInteger getPower() {
		return description.getPower();
	}

	@Override
	public long getTotalWaitingTime() {
		return description.getTotalWaitingTime();
	}

	@Override
	public long getWeightedWaitingTime() {
		return description.getWeightedWaitingTime();
	}

	@Override
	public BigInteger getAcceleration() {
		return description.getAcceleration();
	}

	@Override
	public long getHeight() {
		return description.getHeight();
	}

	@Override
	public SignatureAlgorithm getSignatureForBlocks() {
		return description.getSignatureForBlocks();
	}

	@Override
	public PublicKey getPublicKeyForSigningThisBlock() {
		return description.getPublicKeyForSigningThisBlock();
	}

	@Override
	public String getPublicKeyForSigningThisBlockBase58() {
		return description.getPublicKeyForSigningThisBlockBase58();
	}

	@Override
	public final byte[] getHash(HashingAlgorithm hashing) {
		// it uses a cache for optimization, since the computation might be expensive
	
		synchronized (lock) {
			if (Objects.equals(lastHashing, hashing))
				return lastHash.clone();
		}
	
		byte[] result = hashing.getHasher(Block::toByteArray).hash(this);
	
		synchronized (lock) {
			lastHashing = hashing;
			lastHash = result.clone();
		}
	
		return result;
	}

	@Override
	public final String getHexHash(HashingAlgorithm hashing) {
		return Hex.toHexString(getHash(hashing));
	}

	@Override
	public final DeadlineDescription getNextDeadlineDescription(HashingAlgorithm hashingForGenerations, HashingAlgorithm hashingForDeadlines) {
		var nextGenerationSignature = description.getNextGenerationSignature(hashingForGenerations);
		return DeadlineDescriptions.of(getNextScoopNumber(nextGenerationSignature, hashingForGenerations), nextGenerationSignature, hashingForDeadlines);
	}

	@Override
	public final NonGenesisBlockDescription getNextBlockDescription(Deadline deadline, long targetBlockCreationTime, HashingAlgorithm hashingForBlocks, HashingAlgorithm hashingForDeadlines) {
		var heightForNewBlock = getHeight() + 1;
		var powerForNewBlock = computePower(deadline, hashingForDeadlines);
		var waitingTimeForNewBlock = deadline.getMillisecondsToWaitFor(getAcceleration());
		var weightedWaitingTimeForNewBlock = computeWeightedWaitingTime(waitingTimeForNewBlock);
		var totalWaitingTimeForNewBlock = computeTotalWaitingTime(waitingTimeForNewBlock);
		var accelerationForNewBlock = computeAcceleration(weightedWaitingTimeForNewBlock, targetBlockCreationTime);
		var hashOfPreviousBlock = getHash(hashingForBlocks);

		return new NonGenesisBlockDescriptionImpl(heightForNewBlock, powerForNewBlock, totalWaitingTimeForNewBlock,
			weightedWaitingTimeForNewBlock, accelerationForNewBlock, deadline, hashOfPreviousBlock);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof AbstractBlock ab && description.equals(ab.description);
	}

	@Override
	public int hashCode() {
		return description.hashCode();
	}

	@Override
	public String toString() {
		return description.toString();
	}

	/**
	 * Marshals this block into the given context, without its signature.
	 * 
	 * @param context the context
	 * @throws IOException if marshalling fails
	 */
	protected void intoWithoutSignature(MarshallingContext context) throws IOException {
		// we write the height of the block anyway, so that, by reading the first long,
		// it is possible to distinguish between a genesis block (height == 0)
		// and a non-genesis block (height > 0)
		context.writeLong(getHeight());
		getDescription().into(context);
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		intoWithoutSignature(context);
		var signature = getSignature();
		context.writeCompactInt(signature.length);
		context.write(signature);
	}

	/**
	 * Yields a marshalling of this object into a byte array, without considering its signature.
	 * 
	 * @return the marshalled bytes
	 */
	protected final byte[] toByteArrayWithoutSignature() {
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

	private BigInteger computePower(Deadline deadline, HashingAlgorithm hashingForDeadlines) {
		byte[] valueAsBytes = deadline.getValue();
		var value = new BigInteger(1, valueAsBytes);
		return getPower().add(BigInteger.TWO.shiftLeft(hashingForDeadlines.length() * 8).divide(value.add(BigInteger.ONE)));
	}

	private long computeTotalWaitingTime(long waitingTime) {
		return getTotalWaitingTime() + waitingTime;
	}

	private long computeWeightedWaitingTime(long waitingTime) {
		var previousWeightedWaitingTime_95 = getWeightedWaitingTime() * 95L;
		var waitingTime_5 = waitingTime * 5L;
		return (previousWeightedWaitingTime_95 + waitingTime_5) / 100L;
	}

	/**
	 * Computes the acceleration for the new block, in order to get closer to the target creation time.
	 * 
	 * @param weightedWaitingTimeForNewBlock the weighted waiting time for the new block
	 * @param targetBlockCreationTime 
	 * @return the acceleration for the new block
	 */
	private BigInteger computeAcceleration(long weightedWaitingTimeForNewBlock, long targetBlockCreationTime) {
		var oldAcceleration = getAcceleration();
		var delta = oldAcceleration
			.multiply(BigInteger.valueOf(weightedWaitingTimeForNewBlock))
			.divide(BigInteger.valueOf(targetBlockCreationTime))
			.subtract(oldAcceleration);
	
		var acceleration = oldAcceleration.add(delta.multiply(_20).divide(_100));
		
		// TODO: remove at then end
		if (acceleration.signum() < 0) {
			System.out.println("oldAcceleration: " + oldAcceleration);
			System.out.println("weightedWaitingTimeForNewBlock: " + weightedWaitingTimeForNewBlock);
			System.out.println("targetBlockCreationTime: " + targetBlockCreationTime);
			System.out.println("delta: " + delta);
			throw new IllegalStateException("negative acceleration: " + acceleration + " old: " + oldAcceleration + " w: " + weightedWaitingTimeForNewBlock + " t: " + targetBlockCreationTime + " delta: " + delta);
		}

		if (acceleration.signum() == 0)
			acceleration = BigInteger.ONE; // acceleration must be strictly positive
	
		return acceleration;
	}

	private int getNextScoopNumber(byte[] nextGenerationSignature, HashingAlgorithm hashingForGenerations) {
		var generationHash = hashingForGenerations.getHasher(Function.identity()).hash(concat(nextGenerationSignature, longToBytesBE(getHeight() + 1)));
		return new BigInteger(1, generationHash).remainder(SCOOPS_PER_NONCE).intValue();
	}

	protected static byte[] concat(byte[] array1, byte[] array2) {
		var merge = new byte[array1.length + array2.length];
		System.arraycopy(array1, 0, merge, 0, array1.length);
		System.arraycopy(array2, 0, merge, array1.length, array2.length);
		return merge;
	}

	private static byte[] longToBytesBE(long l) {
		var target = new byte[8];
		for (int i = 0; i <= 7; i++)
			target[7 - i] = (byte) ((l>>(8*i)) & 0xFF);

		return target;
	}

	/**
	 * Fills the given builder with the information inside this block.
	 * 
	 * @param builder the builder
	 * @param hashingForGenerations the hashing algorithm used for deadline generations, if available
	 * @param hashingForBlocks the hashing algorithm used for the blocks, if available
	 * @param startDateTimeUTC the creation time of the genesis block of the chain of the block, if available
	 */
	protected void populate(StringBuilder builder, Optional<HashingAlgorithm> hashingForGenerations, Optional<HashingAlgorithm> hashingForBlocks, Optional<LocalDateTime> startDateTimeUTC) {
		description.populate(builder, hashingForGenerations, hashingForBlocks, startDateTimeUTC);
		builder.append("* signature: " + Hex.toHexString(getSignature()) + " (" + getSignatureForBlocks() + ")\n");
	}
}
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;

/**
 * Shared code of all classes implementing blocks.
 */
public abstract sealed class AbstractBlock<D extends BlockDescription> extends AbstractMarshallable implements Block permits GenesisBlockImpl, NonGenesisBlockImpl {

	/**
	 * The description of this block.
	 */
	private final D description;

	/**
	 * The identifier of the state of the application at the end of this block.
	 */
	private final byte[] stateId;

	/**
	 * The signature of this block.
	 */
	private final byte[] signature;

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
	 * @param stateId the identifier of the state of the application at the end of this block
	 * @param signature the signature of the block
	 */
	protected AbstractBlock(D description, byte[] stateId, byte[] signature) {
		this.description = description;
		this.stateId = stateId.clone();
		this.signature = signature.clone();
	}

	/**
	 * Creates an abstract block with the given description and signs it.
	 * 
	 * @param description the description of the block
	 * @param stateId the identifier of the state of the application at the end of this block
	 * @param privateKey the private key for signing the block
	 * @throws SignatureException if signing failed
	 * @throws InvalidKeyException if {@code privateKey} is illegal
	 */
	protected AbstractBlock(D description, byte[] stateId, PrivateKey privateKey, byte[] bytesToSign) throws InvalidKeyException, SignatureException {
		this.description = description;
		this.stateId = stateId.clone();
		this.signature = description.getSignatureForBlock().getSigner(privateKey, Function.identity()).sign(bytesToSign);
	}

	/**
	 * Unmarshals an abstract block from the given context.
	 * The description of the block has been already unmarshalled.
	 * 
	 * @param description the already unmarshalled description
	 * @param context the context
	 * @return the block
	 * @throws IOException if the block cannot be unmarshalled
	 */
	protected AbstractBlock(D description, UnmarshallingContext context) throws IOException {
		this.description = description;

		try {
			this.stateId = context.readLengthAndBytes("State id length mismatch");
			this.signature = context.readLengthAndBytes("Signature length mismatch");
		}
		catch (RuntimeException e) {
			throw new IOException(e);
		}
	}

	@Override
	public final D getDescription() {
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
		var description = AbstractBlockDescription.from(context);
		if (description instanceof GenesisBlockDescription gbd)
			return new GenesisBlockImpl(gbd, context);
		else
			return new NonGenesisBlockImpl((NonGenesisBlockDescription) description, context); // cast verified by sealedness
	}

	@Override
	public final byte[] getSignature() {
		return signature.clone();
	}

	@Override
	public final byte[] getStateId() {
		return stateId.clone();
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
		var heightForNewBlock = description.getHeight() + 1;
		var powerForNewBlock = computePower(deadline, hashingForDeadlines);
		var waitingTimeForNewBlock = deadline.getMillisecondsToWaitFor(description.getAcceleration());
		var weightedWaitingTimeForNewBlock = computeWeightedWaitingTime(waitingTimeForNewBlock);
		var totalWaitingTimeForNewBlock = computeTotalWaitingTime(waitingTimeForNewBlock);
		var accelerationForNewBlock = computeAcceleration(weightedWaitingTimeForNewBlock, targetBlockCreationTime);
		var hashOfPreviousBlock = getHash(hashingForBlocks);

		return new NonGenesisBlockDescriptionImpl(heightForNewBlock, powerForNewBlock, totalWaitingTimeForNewBlock,
			weightedWaitingTimeForNewBlock, accelerationForNewBlock, deadline, hashOfPreviousBlock);
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Block block
			// this guarantees that genesis is only equal to genesis
			// and non-genesis is only equals to non-genesis
			&& description.equals(block.getDescription()))
			if (other instanceof AbstractBlock<?> oab)
				return Arrays.equals(signature, oab.signature) && Arrays.equals(stateId, oab.stateId); // optimization
			else
				return Arrays.equals(signature, block.getSignature()) && Arrays.equals(stateId, block.getStateId());
		else
			return false;
	}

	@Override
	public int hashCode() {
		return description.hashCode() ^ Arrays.hashCode(stateId);
	}

	@Override
	public final String toString() {
		return toString(Optional.empty(), Optional.empty());
	}

	@Override
	public String toString(Optional<ConsensusConfig<?,?>> config, Optional<LocalDateTime> startDateTimeUTC) {
		var builder = new StringBuilder();
		config.map(ConsensusConfig::getHashingForBlocks).ifPresent(hashingForBlocks -> builder.append("* hash: " + getHexHash(hashingForBlocks) + " (" + hashingForBlocks + ")\n"));
		builder.append(description.toString(config, startDateTimeUTC));
		builder.append("\n");
		builder.append("* node's signature: " + Hex.toHexString(signature) + " (" + description.getSignatureForBlock() + ")\n");
		builder.append("* final state id: " + Hex.toHexString(stateId));

		return builder.toString();
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		description.into(context);
		context.writeLengthAndBytes(stateId);
		context.writeLengthAndBytes(signature);
	}

	/**
	 * Checks all constraints expected from this block. This also checks the validity of
	 * the signature and that transactions are not repeated inside the block.
	 * 
	 * @throws NullPointerException if some value is unexpectedly {@code null}
	 * @throws IllegalArgumentException if some value is illegal (also if the signature is invalid or
	 *                                  if some transaction is repeated inside the block)
	 */
	protected void verify(byte[] bytesToSign) {
		Objects.requireNonNull(description, "description cannot be null");
		Objects.requireNonNull(signature, "signature cannot be null");
	
		try {
			if (!description.getSignatureForBlock().getVerifier(description.getPublicKeyForSigningBlock(), Function.identity()).verify(bytesToSign, signature))
				throw new IllegalArgumentException("The block's signature is invalid");
		}
		catch (SignatureException e) {
			throw new IllegalArgumentException("The block's signature cannot be verified", e);
		}
		catch (InvalidKeyException e) {
			throw new IllegalArgumentException("The public key in the prolog of the deadline of the block is invalid", e);
		}
	}

	private BigInteger computePower(Deadline deadline, HashingAlgorithm hashingForDeadlines) {
		byte[] valueAsBytes = deadline.getValue();
		var value = new BigInteger(1, valueAsBytes);
		return description.getPower().add(BigInteger.TWO.shiftLeft(hashingForDeadlines.length() * 8).divide(value.add(BigInteger.ONE)));
	}

	private long computeTotalWaitingTime(long waitingTime) {
		return description.getTotalWaitingTime() + waitingTime;
	}

	private long computeWeightedWaitingTime(long waitingTime) {
		// probably irrelevant, but by using BigInteger we reduce the risk of overflow
		var previousWeightedWaitingTime_95 = BigInteger.valueOf(description.getWeightedWaitingTime()).multiply(BigInteger.valueOf(95L));
		var waitingTime_5 = BigInteger.valueOf(waitingTime).multiply(BigInteger.valueOf(5L));
		return previousWeightedWaitingTime_95.add(waitingTime_5).divide(BigInteger.valueOf(100L)).longValue();
	}

	/**
	 * Computes the acceleration for the new block, in order to get closer to the target creation time.
	 * 
	 * @param weightedWaitingTimeForNewBlock the weighted waiting time for the new block
	 * @param targetBlockCreationTime 
	 * @return the acceleration for the new block
	 */
	private BigInteger computeAcceleration(long weightedWaitingTimeForNewBlock, long targetBlockCreationTime) {
		var oldAcceleration = description.getAcceleration();
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
		var generationHash = hashingForGenerations.getHasher(Function.identity()).hash(concat(nextGenerationSignature, longToBytesBE(description.getHeight() + 1)));
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
}
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
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.nonce.api.Deadline;

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

	private final static double oblivion = 0.05;

	private final static BigInteger _100000 = BigInteger.valueOf(100000L);

	private final static BigInteger OBLIVION = BigInteger.valueOf((long) (oblivion * _100000.longValue()));

	private final static BigInteger COMPLEMENT_OF_OBLIVION = BigInteger.valueOf(_100000.longValue() - OBLIVION.longValue());

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
	 * @param bytesToSign the bytes of the block to sign
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
	 * Unmarshals a block from the given context. It assumes that the block was marshalled
	 * by using {@link Block#into(MarshallingContext)}.
	 * 
	 * @param context the context
	 * @param config the consensus configuration of the node storing the block description
	 * @return the block
	 * @throws IOException if the block cannot be unmarshalled
	 */
	public static Block from(UnmarshallingContext context, ConsensusConfig<?,?> config) throws IOException {
		var description = AbstractBlockDescription.from(context, config);
		if (description instanceof GenesisBlockDescription gbd)
			return new GenesisBlockImpl(gbd, context);
		else
			return new NonGenesisBlockImpl((NonGenesisBlockDescription) description, context); // cast verified by sealedness
	}

	/**
	 * Unmarshals a block from the given context. It assumes that the block was marshalled
	 * by using {@link Block#into(MarshallingContext)}.
	 * 
	 * @param context the context
	 * @param config the consensus configuration of the node storing the block description
	 * @return the block
	 * @throws IOException if the block cannot be unmarshalled
	 * @throws NoSuchAlgorithmException if the block refers to an unknown cryptographic algorithm
	 */
	public static Block from(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
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
	public final NonGenesisBlockDescription getNextBlockDescription(Deadline deadline, ConsensusConfig<?,?> config) {
		var heightForNewBlock = description.getHeight() + 1;
		var powerForNewBlock = computePower(deadline);
		var waitingTimeForNewBlock = deadline.getMillisecondsToWait(description.getAcceleration());
		var weightedWaitingTimeForNewBlock = computeWeightedWaitingTime(waitingTimeForNewBlock);
		var totalWaitingTimeForNewBlock = computeTotalWaitingTime(waitingTimeForNewBlock);
		var accelerationForNewBlock = computeAcceleration(weightedWaitingTimeForNewBlock, config.getTargetBlockCreationTime());
		var hashOfPreviousBlock = getHash(config.getHashingForBlocks());

		return BlockDescriptions.of(heightForNewBlock, powerForNewBlock, totalWaitingTimeForNewBlock,
			weightedWaitingTimeForNewBlock, accelerationForNewBlock, deadline, hashOfPreviousBlock, config.getHashingForBlocks());
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Block block
			// this guarantees that genesis is only equal to genesis, and that non-genesis is only equals to non-genesis
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
		builder.append(description.toString(startDateTimeUTC));
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

	@Override
	public void intoWithoutConfigurationData(MarshallingContext context) throws IOException {
		description.intoWithoutConfigurationData(context);
		context.writeLengthAndBytes(stateId);
		context.writeLengthAndBytes(signature);
	}

	@Override
	public final byte[] toByteArrayWithoutConfigurationData() {
		try (var baos = new ByteArrayOutputStream(); var context = createMarshallingContext(baos)) {
			intoWithoutConfigurationData(context);
			context.flush();
			return baos.toByteArray();
		}
		catch (IOException e) {
			// impossible with a ByteArrayOutputStream
			throw new RuntimeException("Unexpected exception", e);
		}
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

	private BigInteger computePower(Deadline deadline) {
		return description.getPower().add(deadline.getPower());
	}

	private long computeTotalWaitingTime(long waitingTime) {
		return description.getTotalWaitingTime() + waitingTime;
	}

	private long computeWeightedWaitingTime(long waitingTime) {
		// probably irrelevant, but by using BigInteger we reduce the risk of overflow
		var previousWeightedWaitingTimeWeighted = BigInteger.valueOf(description.getWeightedWaitingTime()).multiply(COMPLEMENT_OF_OBLIVION);
		var waitingTimeWeighted = BigInteger.valueOf(waitingTime).multiply(OBLIVION);
		return previousWeightedWaitingTimeWeighted.add(waitingTimeWeighted).divide(_100000).longValue();
	}

	/**
	 * Computes the acceleration for the new block, in order to get closer to the target creation time.
	 * 
	 * @param config the consensus configuration of the node storing this block
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

		var acceleration = oldAcceleration.add(delta.multiply(OBLIVION).divide(_100000));

		// acceleration must be strictly positive
		return acceleration.signum() == 0 ? BigInteger.ONE : acceleration;
	}
}
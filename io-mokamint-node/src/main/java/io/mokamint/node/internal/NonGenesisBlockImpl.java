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
import java.security.PublicKey;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.nonce.api.Deadline;

/**
 * The implementation of a non-genesis block of the Mokamint blockchain.
 */
@Immutable
public class NonGenesisBlockImpl extends AbstractBlock implements NonGenesisBlock {

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
			Deadline deadline, byte[] hashOfPreviousBlock, PrivateKey privateKey) throws InvalidKeyException, SignatureException {

		super(new NonGenesisBlockDescriptionImpl(height, power, totalWaitingTime, weightedWaitingTime, acceleration, deadline, hashOfPreviousBlock));
		verifyWithoutSignature();
		this.signature = computeSignature(privateKey);
	}

	/**
	 * Creates a new non-genesis block.
	 */
	public NonGenesisBlockImpl(long height, BigInteger power, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration, Deadline deadline, byte[] hashOfPreviousBlock, byte[] signature) {
		super(new NonGenesisBlockDescriptionImpl(height, power, totalWaitingTime, weightedWaitingTime, acceleration, deadline, hashOfPreviousBlock));
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
		super(new NonGenesisBlockDescriptionImpl(height, context));

		try {
			this.signature = context.readBytes(context.readCompactInt(), "Signature length mismatch");
			verify();
		}
		catch (RuntimeException e) {
			throw new IOException(e);
		}
	}

	@Override
	protected NonGenesisBlockDescriptionImpl getDescription() {
		return (NonGenesisBlockDescriptionImpl) super.getDescription();
	}

	@Override
	protected void verifyWithoutSignature() {
	}

	@Override
	public Deadline getDeadline() {
		return getDescription().getDeadline();
	}

	@Override
	public byte[] getHashOfPreviousBlock() {
		return getDescription().getHashOfPreviousBlock();
	}

	@Override
	public byte[] getSignature() {
		return signature.clone();
	}

	@Override
	public SignatureAlgorithm getSignatureForBlocks() {
		return getDeadline().getProlog().getSignatureForBlocks();
	}

	@Override
	public PublicKey getPublicKeyForSigningThisBlock() {
		return getDeadline().getProlog().getPublicKeyForSigningBlocks();
	}

	@Override
	public String getPublicKeyForSigningThisBlockBase58() {
		return getDeadline().getProlog().getPublicKeyForSigningBlocksBase58();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof NonGenesisBlock ngb && super.equals(other) && Arrays.equals(signature, ngb.getSignature());
	}

	@Override
	public String toString(ConsensusConfig<?,?> config, LocalDateTime startDateTimeUTC) {
		var hashing = config.getHashingForBlocks();
		var builder = new StringBuilder("Non-genesis block with hash " + getHexHash(hashing) + " (" + hashing + "):\n");
		populate(builder, Optional.of(config.getHashingForGenerations()), Optional.of(hashing), Optional.of(startDateTimeUTC));
		return builder.toString();
	}

	@Override
	public <E extends Exception> void matchesOrThrow(NonGenesisBlockDescription description, Function<String, E> exceptionSupplier) throws E {
		getDescription().matchesOrThrow(description, exceptionSupplier);
	}
}
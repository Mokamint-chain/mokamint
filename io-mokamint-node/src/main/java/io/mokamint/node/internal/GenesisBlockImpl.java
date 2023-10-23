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
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.GenesisBlockDescription;

/**
 * The implementation of a genesis block of a Mokamint blockchain.
 */
@Immutable
public class GenesisBlockImpl extends AbstractBlock implements GenesisBlock {

	/**
	 * The signature algorithm used to sign this block.
	 */
	private final SignatureAlgorithm signatureForBlocks;

	/**
	 * The public key of the node that signed this block.
	 */
	private final PublicKey publicKey;

	/**
	 * Base58 encoding of {@link #publicKey}.
	 */
	private final String publicKeyBase58;

	/**
	 * The signature of this node.
	 */
	private final byte[] signature;

	/**
	 * The generation signature for the block on top of the genesis block. This is arbitrary.
	 */
	private final static byte[] BLOCK_1_GENERATION_SIGNATURE = new byte[] { 13, 1, 19, 73 };

	/**
	 * Creates a genesis block and signs it with the given private key and signature algorithm.
	 * 
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public GenesisBlockImpl(LocalDateTime startDateTimeUTC, BigInteger acceleration, SignatureAlgorithm signatureForBlocks, KeyPair keys) throws InvalidKeyException, SignatureException {
		super(new GenesisBlockDescriptionImpl(startDateTimeUTC, acceleration));

		this.signatureForBlocks = signatureForBlocks;
		this.publicKey = keys.getPublic();
		this.publicKeyBase58 = Base58.encode(signatureForBlocks.encodingOf(publicKey));
		this.signature = computeSignature(keys.getPrivate());
		verifyWithoutSignature();
	}

	/**
	 * Creates a new genesis block.
	 *
	 * @throws InvalidKeySpecException if the public key is invalid
	 */
	public GenesisBlockImpl(LocalDateTime startDateTimeUTC, BigInteger acceleration, SignatureAlgorithm signatureForBlocks, String publicKeyBase58, byte[] signature) throws InvalidKeySpecException {
		super(new GenesisBlockDescriptionImpl(startDateTimeUTC, acceleration));

		this.signatureForBlocks = signatureForBlocks;
		this.publicKey = signatureForBlocks.publicKeyFromEncoding(Base58.decode(publicKeyBase58));
		this.publicKeyBase58 = publicKeyBase58;
		this.signature = signature.clone();
		verify();
	}

	/**
	 * Unmarshals a genesis block from the given context.
	 * The height of the block has been already read.
	 * 
	 * @param context the context
	 * @return the block
	 * @throws IOException if the block cannot be unmarshalled
	 * @throws NoSuchAlgorithmException if some signature algorithm is not available
	 */
	GenesisBlockImpl(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		super(new GenesisBlockDescriptionImpl(context));

		try {
			this.signatureForBlocks = SignatureAlgorithms.of(context.readStringShared());
			byte[] publicKeyEncoding = context.readBytes(context.readCompactInt(), "Mismatch in the length of the public key");
			this.publicKey = signatureForBlocks.publicKeyFromEncoding(publicKeyEncoding);
			this.publicKeyBase58 = Base58.encode(publicKeyEncoding);
			this.signature = context.readBytes(context.readCompactInt(), "Signature length mismatch");
			verify();
		}
		catch (RuntimeException | InvalidKeySpecException e) {
			throw new IOException(e);
		}
	}

	@Override
	protected GenesisBlockDescription getDescription() {
		return (GenesisBlockDescription) super.getDescription();
	}

	@Override
	protected void verifyWithoutSignature() {
		Objects.requireNonNull(signatureForBlocks, "signatureForBlocks cannot be null");
		Objects.requireNonNull(publicKey, "publicKey cannot be null");
		Objects.requireNonNull(publicKeyBase58, "publicKeyBase58 cannot be null");
	}

	@Override
	public LocalDateTime getStartDateTimeUTC() {
		return getDescription().getStartDateTimeUTC();
	}

	@Override
	public byte[] getSignature() {
		return signature.clone();
	}

	@Override
	public SignatureAlgorithm getSignatureForBlocks() {
		return signatureForBlocks;
	}

	@Override
	public PublicKey getPublicKeyForSigningThisBlock() {
		return publicKey;
	}

	@Override
	public String getPublicKeyForSigningThisBlockBase58() {
		return publicKeyBase58;
	}

	@Override
	protected byte[] getNextGenerationSignature(HashingAlgorithm hashing) {
		return BLOCK_1_GENERATION_SIGNATURE;
	}

	@Override
	protected void intoWithoutSignature(MarshallingContext context) throws IOException {
		super.intoWithoutSignature(context);
		context.writeStringShared(signatureForBlocks.getName());

		try {
			var publicKeyBytes = signatureForBlocks.encodingOf(publicKey);
			context.writeCompactInt(publicKeyBytes.length);
			context.write(publicKeyBytes);
		}
		catch (InvalidKeyException e) {
			throw new IOException("Cannot marshal the genesis block into bytes", e);
		}
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GenesisBlock gb && super.equals(other)
			&& Arrays.equals(signature, gb.getSignature())
			&& publicKey.equals(gb.getPublicKeyForSigningThisBlock())
			&& publicKeyBase58.equals(gb.getPublicKeyForSigningThisBlockBase58())
			&& signatureForBlocks.equals(gb.getSignatureForBlocks());
	}

	@Override
	public String toString() {
		var builder = new StringBuilder("Genesis Block:\n");
		builder.append("* creation date and time UTC: " + getStartDateTimeUTC() + "\n");
		populate(builder);
		
		return builder.toString();
	}

	@Override
	public String toString(ConsensusConfig<?,?> config, LocalDateTime startDateTimeUTC) {
		var builder = new StringBuilder("Genesis Block:\n");
		builder.append("* creation date and time UTC: " + startDateTimeUTC + "\n");
		builder.append("* hash: " + getHexHash(config.getHashingForBlocks()) + "\n");
		populate(builder);

		return builder.toString();
	}

	private void populate(StringBuilder builder) {
		builder.append("* height: " + getHeight() + "\n");
		builder.append("* power: " + getPower() + "\n");
		builder.append("* total waiting time: " + getTotalWaitingTime() + " ms\n");
		builder.append("* weighted waiting time: " + getWeightedWaitingTime() + " ms\n");
		builder.append("* acceleration: " + getAcceleration() + "\n");
	}
}
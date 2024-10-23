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
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.GenesisBlockDescription;

/**
 * The implementation of the description of a genesis block of the Mokamint blockchain.
 */
@Immutable
public non-sealed class GenesisBlockDescriptionImpl extends AbstractBlockDescription implements GenesisBlockDescription {

	/**
	 * The moment when the block has been mined. This is the moment when the blockchain started.
	 */
	private final LocalDateTime startDateTimeUTC;

	/**
	 * A value used to divide the deadline to derive the time needed to wait for it.
	 * The higher, the shorter the time. This value changes dynamically to cope with
	 * varying mining power in the network. It is similar to Bitcoin's difficulty.
	 */
	private final BigInteger acceleration;

	/**
	 * The signature algorithm used to sign this block.
	 */
	private final SignatureAlgorithm signatureForBlock;

	/**
	 * The public key of the node that signed this block.
	 */
	private final PublicKey publicKey;

	/**
	 * Base58 encoding of {@link #publicKey}.
	 */
	private final String publicKeyBase58;

	/**
	 * Creates a genesis block description with the given keys and signature algorithm.
	 * 
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public GenesisBlockDescriptionImpl(LocalDateTime startDateTimeUTC, BigInteger acceleration, SignatureAlgorithm signatureForBlock, PublicKey publicKey) throws InvalidKeyException {
		this.startDateTimeUTC = startDateTimeUTC;
		this.acceleration = acceleration;
		this.signatureForBlock = signatureForBlock;
		this.publicKey = publicKey;
		byte[] publicKeyEncoding = signatureForBlock.encodingOf(publicKey);
		this.publicKeyBase58 = Base58.encode(publicKeyEncoding);

		verify();
	}

	/**
	 * Unmarshals a genesis block description. It assumes that the description was marshalled
	 * by using {@link BlockDescription#into(MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @throws IOException if unmarshalling failed
	 * @throws NoSuchAlgorithmException if some signature algorithm is not available
	 */
	GenesisBlockDescriptionImpl(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		try {
			this.startDateTimeUTC = LocalDateTime.parse(context.readStringUnshared(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			this.acceleration = context.readBigInteger();
			this.signatureForBlock = SignatureAlgorithms.of(context.readStringShared());
			byte[] publicKeyEncoding = readPublicKeyEncoding(context);
			this.publicKey = signatureForBlock.publicKeyFromEncoding(publicKeyEncoding);
			this.publicKeyBase58 = Base58.encode(publicKeyEncoding);
	
			verify();
		}
		catch (RuntimeException | InvalidKeySpecException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Unmarshals a genesis block description. It assumes that the description was marshalled
	 * by using {@link BlockDescription#intoWithoutConfigurationData(MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @param config the configuration of the node storing the description
	 * @throws IOException if unmarshalling failed
	 */
	GenesisBlockDescriptionImpl(UnmarshallingContext context, ConsensusConfig<?,?> config) throws IOException {
		try {
			this.startDateTimeUTC = LocalDateTime.parse(context.readStringUnshared(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			this.acceleration = context.readBigInteger();
			this.signatureForBlock = config.getSignatureForBlocks();
			byte[] publicKeyEncoding = readPublicKeyEncoding(context);
			this.publicKey = signatureForBlock.publicKeyFromEncoding(publicKeyEncoding);
			this.publicKeyBase58 = Base58.encode(publicKeyEncoding);
	
			verify();
		}
		catch (RuntimeException | InvalidKeySpecException e) {
			throw new IOException(e);
		}
	}

	private byte[] readPublicKeyEncoding(UnmarshallingContext context) throws IOException {
		var maybeLength = signatureForBlock.publicKeyLength();
		if (maybeLength.isPresent())
			return context.readBytes(maybeLength.getAsInt(), "Mismatch in the length of the public key");
		else
			return context.readLengthAndBytes("Mismatch in the length of the public key");
	}

	/**
	 * Checks all constraints expected from a non-genesis block.
	 * 
	 * @throws NullPointerException if some value is unexpectedly {@code null}
	 * @throws IllegalArgumentException if some value is illegal
	 */
	private void verify() {
		Objects.requireNonNull(startDateTimeUTC, "startDateTimeUTC cannot be null");
		Objects.requireNonNull(acceleration, "acceleration cannot be null");
		Objects.requireNonNull(signatureForBlock, "signatureForBlocks cannot be null");
		Objects.requireNonNull(publicKey, "publicKey cannot be null");

		if (acceleration.signum() <= 0)
			throw new IllegalArgumentException("The acceleration must be strictly positive");
	}

	@Override
	public BigInteger getPower() {
		return BigInteger.ZERO; // just started
	}

	@Override
	public long getTotalWaitingTime() {
		return 0L; // just started
	}

	@Override
	public long getWeightedWaitingTime() {
		return 0L; // just started
	}

	@Override
	public BigInteger getAcceleration() {
		return acceleration;
	}

	@Override
	public long getHeight() {
		return 0L;
	}

	@Override
	public SignatureAlgorithm getSignatureForBlock() {
		return signatureForBlock;
	}

	@Override
	public PublicKey getPublicKeyForSigningBlock() {
		return publicKey;
	}

	@Override
	public String getPublicKeyForSigningBlockBase58() {
		return publicKeyBase58;
	}

	@Override
	public LocalDateTime getStartDateTimeUTC() {
		return startDateTimeUTC;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GenesisBlockDescription gbd &&
			startDateTimeUTC.equals(gbd.getStartDateTimeUTC()) &&
			acceleration.equals(gbd.getAcceleration()) &&
			publicKey.equals(gbd.getPublicKeyForSigningBlock()) &&
			signatureForBlock.equals(gbd.getSignatureForBlock());
	}

	@Override
	public int hashCode() {
		return startDateTimeUTC.hashCode() ^ acceleration.hashCode();
	}

	@Override
	protected void populate(StringBuilder builder, Optional<ConsensusConfig<?,?>> config, Optional<LocalDateTime> startDateTimeUTC) {
		builder.append("* creation date and time UTC: " + this.startDateTimeUTC + "\n");
		super.populate(builder, config, startDateTimeUTC);
		builder.append("\n* public key of the peer that signed the block: " + publicKeyBase58 + " (" + signatureForBlock + ", base58)");
	}

	@Override
	protected byte[] getNextGenerationSignature(HashingAlgorithm hashingForGenerations) {
		var generationSignature = new byte[hashingForGenerations.length()];
		generationSignature[0] = (byte) 0x80;
		return generationSignature;
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		try {
			// we write the height of the block anyway, so that, by reading the first long,
			// it is possible to distinguish between a genesis block (height == 0)
			// and a non-genesis block (height > 0)
			context.writeLong(0L);
			context.writeStringUnshared(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(startDateTimeUTC));
			context.writeBigInteger(acceleration);
			context.writeStringShared(signatureForBlock.getName());
			writePublicKeyEncoding(context);
		}
		catch (DateTimeException | InvalidKeyException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void intoWithoutConfigurationData(MarshallingContext context) throws IOException {
		try {
			// we write the height of the block anyway, so that, by reading the first long,
			// it is possible to distinguish between a genesis block (height == 0)
			// and a non-genesis block (height > 0)
			context.writeLong(0L);
			context.writeStringUnshared(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(startDateTimeUTC));
			context.writeBigInteger(acceleration);
			writePublicKeyEncoding(context);
		}
		catch (DateTimeException | InvalidKeyException e) {
			throw new IOException(e);
		}
	}

	private void writePublicKeyEncoding(MarshallingContext context) throws IOException, InvalidKeyException {
		var maybeLength = signatureForBlock.publicKeyLength();
		if (maybeLength.isEmpty())
			context.writeLengthAndBytes(signatureForBlock.encodingOf(publicKey));
		else
			context.writeBytes(signatureForBlock.encodingOf(publicKey));
	}
}
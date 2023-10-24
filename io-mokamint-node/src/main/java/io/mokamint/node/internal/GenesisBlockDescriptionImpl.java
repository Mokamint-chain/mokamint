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
import java.util.function.Function;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.GenesisBlockDescription;

/**
 * The implementation of the description of a genesis block of the Mokamint blockchain.
 */
@Immutable
public class GenesisBlockDescriptionImpl extends AbstractBlockDescription implements GenesisBlockDescription {

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
	 * The generation signature for the block on top of the genesis block. This is arbitrary.
	 */
	private final static byte[] BLOCK_1_GENERATION_SIGNATURE = new byte[] { 13, 1, 19, 73 };

	/**
	 * Creates a new genesis block description.
	 *
	 * @throws InvalidKeySpecException if the public key is invalid
	 */
	public GenesisBlockDescriptionImpl(LocalDateTime startDateTimeUTC, BigInteger acceleration, SignatureAlgorithm signatureForBlocks, String publicKeyBase58) throws InvalidKeySpecException {
		this.startDateTimeUTC = startDateTimeUTC;
		this.acceleration = acceleration;
		this.signatureForBlocks = signatureForBlocks;
		this.publicKey = signatureForBlocks.publicKeyFromEncoding(Base58.decode(publicKeyBase58));
		this.publicKeyBase58 = publicKeyBase58;

		verify();
	}

	/**
	 * Creates a genesis block description with the given keys and signature algorithm.
	 * 
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public GenesisBlockDescriptionImpl(LocalDateTime startDateTimeUTC, BigInteger acceleration, SignatureAlgorithm signatureForBlocks, PublicKey publicKey) throws InvalidKeyException {
		this.startDateTimeUTC = startDateTimeUTC;
		this.acceleration = acceleration;
		this.signatureForBlocks = signatureForBlocks;
		this.publicKey = publicKey;
		this.publicKeyBase58 = Base58.encode(signatureForBlocks.encodingOf(publicKey));

		verify();
	}

	/**
	 * Unmarshals a genesis block.
	 * 
	 * @param context the unmarshalling context
	 * @throws IOException if unmarshalling failed
	 * @throws NoSuchAlgorithmException if some signature algorithm is not available
	 */
	GenesisBlockDescriptionImpl(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		try {
			this.startDateTimeUTC = LocalDateTime.parse(context.readStringUnshared(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			this.acceleration = context.readBigInteger();
			this.signatureForBlocks = SignatureAlgorithms.of(context.readStringShared());
			byte[] publicKeyEncoding = context.readBytes(context.readCompactInt(), "Mismatch in the length of the public key");
			this.publicKey = signatureForBlocks.publicKeyFromEncoding(publicKeyEncoding);
			this.publicKeyBase58 = Base58.encode(publicKeyEncoding);
	
			verify();
		}
		catch (RuntimeException | InvalidKeySpecException e) {
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
		Objects.requireNonNull(startDateTimeUTC, "startDateTimeUTC cannot be null");
		Objects.requireNonNull(acceleration, "acceleration cannot be null");
		Objects.requireNonNull(signatureForBlocks, "signatureForBlocks cannot be null");
		Objects.requireNonNull(publicKey, "publicKey cannot be null");
		Objects.requireNonNull(publicKeyBase58, "publicKeyBase58 cannot be null");

		if (acceleration.signum() <= 0)
			throw new IllegalArgumentException("acceleration must be strictly positive");
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
	public LocalDateTime getStartDateTimeUTC() {
		return startDateTimeUTC;
	}

	@Override
	protected byte[] getNextGenerationSignature(HashingAlgorithm hashing) {
		return BLOCK_1_GENERATION_SIGNATURE;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GenesisBlockDescription gbd &&
			startDateTimeUTC.equals(gbd.getStartDateTimeUTC()) &&
			acceleration.equals(gbd.getAcceleration()) &&
			publicKey.equals(gbd.getPublicKeyForSigningThisBlock()) &&
			publicKeyBase58.equals(gbd.getPublicKeyForSigningThisBlockBase58()) &&
			signatureForBlocks.equals(gbd.getSignatureForBlocks());
	}

	@Override
	public int hashCode() {
		return startDateTimeUTC.hashCode() ^ acceleration.hashCode();
	}

	@Override
	protected String nameInToString() {
		return "Genesis block";
	}

	@Override
	protected void populate(StringBuilder builder, Optional<HashingAlgorithm> hashingForGenerations, Optional<HashingAlgorithm> hashingForBlocks, Optional<LocalDateTime> startDateTimeUTC) {
		builder.append("* creation date and time UTC: " + this.startDateTimeUTC + "\n");
		super.populate(builder, hashingForGenerations, hashingForBlocks, startDateTimeUTC);
		builder.append("* public key of the node that signed the block: " + publicKeyBase58 + " (" + signatureForBlocks + ")\n");
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		try {
			context.writeStringUnshared(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(startDateTimeUTC));
			context.writeBigInteger(acceleration);
			context.writeStringShared(signatureForBlocks.getName());
			var publicKeyBytes = signatureForBlocks.encodingOf(publicKey);
			context.writeCompactInt(publicKeyBytes.length);
			context.write(publicKeyBytes);
		}
		catch (DateTimeException | InvalidKeyException e) {
			throw new IOException(e);
		}
	}

	@Override
	protected Block unmarshals(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		return new GenesisBlockImpl(this, context);
	}

	@Override
	public <E extends Exception> void matchesOrThrow(BlockDescription description, Function<String, E> exceptionSupplier) throws E {
		if (description instanceof GenesisBlockDescription) {
			if (!acceleration.equals(description.getAcceleration()))
				throw exceptionSupplier.apply("Acceleration mismatch (expected " + description.getAcceleration() + " but found " + acceleration + ")");

			if (!signatureForBlocks.equals(description.getSignatureForBlocks()))
				throw exceptionSupplier.apply("Block signature algorithm mismatch (expected " + description.getSignatureForBlocks() + " but found " + signatureForBlocks + ")");

			// TODO: in the future, maybe check for the public key as well
		}
		else
			throw exceptionSupplier.apply("Block type mismatch (expected non-genesis but found genesis)");
	}
}
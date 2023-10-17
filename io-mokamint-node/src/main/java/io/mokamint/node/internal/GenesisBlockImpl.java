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
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

/**
 * The implementation of a genesis block of a Mokamint blockchain.
 */
@Immutable
public class GenesisBlockImpl extends AbstractBlock implements GenesisBlock {

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
		this.startDateTimeUTC = startDateTimeUTC;
		this.acceleration = acceleration;
		this.signatureForBlocks = signatureForBlocks;
		this.publicKey = keys.getPublic();
		this.publicKeyBase58 = Base58.encode(signatureForBlocks.encodingOf(publicKey));
		this.signature = signatureForBlocks.getSigner(keys.getPrivate(), GenesisBlockImpl::toByteArrayWithoutSignature).sign(this);

		verify();
	}

	/**
	 * Creates a new genesis block.
	 *
	 * @throws InvalidKeySpecException if the public key is invalid
	 */
	public GenesisBlockImpl(LocalDateTime startDateTimeUTC, BigInteger acceleration, SignatureAlgorithm signatureForBlocks, String publicKeyBase58, byte[] signature) throws InvalidKeySpecException {
		this.startDateTimeUTC = startDateTimeUTC;
		this.acceleration = acceleration;
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
		try {
			this.startDateTimeUTC = LocalDateTime.parse(context.readStringUnshared(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			this.acceleration = context.readBigInteger();
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

	/**
	 * Checks all constraints expected from a genesis block.
	 * 
	 * @throws NullPointerException if some value is unexpectedly {@code null}
	 * @throws IllegalArgumentException if some value is illegal
	 */
	private void verify() {
		Objects.requireNonNull(startDateTimeUTC, "startDateTimeUTC cannot be null");
		Objects.requireNonNull(acceleration, "acceleration cannot be null");
		Objects.requireNonNull(signatureForBlocks, "signatureForBlocks cannot be null");
		Objects.requireNonNull(publicKey, "publicKey cannot be null");
		Objects.requireNonNull(signature, "signature cannot be null");
		
		if (acceleration.signum() <= 0)
			throw new IllegalArgumentException("acceleration must be strictly positive");

		try {
			if (!signatureForBlocks.getVerifier(publicKey, GenesisBlockImpl::toByteArrayWithoutSignature).verify(this, signature))
				throw new IllegalArgumentException("The block's signature is invalid");
		}
		catch (SignatureException e) {
			throw new IllegalArgumentException("The block's signature cannot be verified", e);
		}
		catch (InvalidKeyException e) {
			throw new IllegalArgumentException("The public key is invalid", e);
		}
	}

	@Override
	public LocalDateTime getStartDateTimeUTC() {
		return startDateTimeUTC;
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
	public byte[] getSignature() {
		return signature.clone();
	}

	@Override
	public SignatureAlgorithm getSignatureForBlocks() {
		return signatureForBlocks;
	}

	@Override
	public PublicKey getPublicKey() {
		return publicKey;
	}

	@Override
	public String getPublicKeyBase58() {
		return publicKeyBase58;
	}

	@Override
	public long getHeight() {
		return 0L;
	}

	@Override
	protected byte[] getNextGenerationSignature(HashingAlgorithm hashing) {
		return BLOCK_1_GENERATION_SIGNATURE;
	}

	/**
	 * Marshals this block into the given context, without its signature.
	 * 
	 * @param context the context
	 * @throws IOException if marshalling fails
	 */
	private void intoWithoutSignature(MarshallingContext context) throws IOException {
		// we write the height of the block anyway, so that, by reading the first long,
		// it is possible to distinguish between a genesis block (height == 0)
		// and a non-genesis block (height > 0)
		context.writeLong(0L);
		context.writeStringUnshared(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(startDateTimeUTC));
		context.writeBigInteger(acceleration);
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
	public void into(MarshallingContext context) throws IOException {
		intoWithoutSignature(context);
		context.writeCompactInt(signature.length);
		context.write(signature);
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
	public boolean equals(Object other) {
		return other instanceof GenesisBlock gb && startDateTimeUTC.equals(gb.getStartDateTimeUTC())
			&& acceleration.equals(gb.getAcceleration());
	}

	@Override
	public int hashCode() {
		return startDateTimeUTC.hashCode() ^ acceleration.hashCode();
	}

	@Override
	public String toString() {
		var builder = new StringBuilder("Genesis Block:\n");
		builder.append("* creation date and time UTC: " + startDateTimeUTC + "\n");
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
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

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.function.Function;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.Base58ConversionException;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.internal.gson.BlockDescriptionJson;

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
	 * The hashing algorithm used for the deadlines.
	 */
	private final HashingAlgorithm hashingForDeadlines;

	/**
	 * The hashing algorithm used for the generation signatures.
	 */
	private final HashingAlgorithm hashingForGenerations;
	
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
	public GenesisBlockDescriptionImpl(LocalDateTime startDateTimeUTC, int targetBlockCreationTime,
			HashingAlgorithm hashingForBlocks, HashingAlgorithm hashingForTransactions, HashingAlgorithm hashingForDeadlines, HashingAlgorithm hashingForGenerations,
			SignatureAlgorithm signatureForBlock, PublicKey publicKey) throws InvalidKeyException {

		super(targetBlockCreationTime, hashingForBlocks, hashingForTransactions);

		this.startDateTimeUTC = Objects.requireNonNull(startDateTimeUTC);
		this.hashingForDeadlines = Objects.requireNonNull(hashingForDeadlines);
		this.hashingForGenerations = Objects.requireNonNull(hashingForGenerations);
		this.signatureForBlock = Objects.requireNonNull(signatureForBlock);
		this.publicKey = Objects.requireNonNull(publicKey);
		this.publicKeyBase58 = Base58.encode(signatureForBlock.encodingOf(publicKey));
	}

	/**
	 * Creates a genesis block description from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 * @throws NoSuchAlgorithmException if the JSON refers to an unknown hashing algorithm
	 */
	public GenesisBlockDescriptionImpl(BlockDescriptionJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
		super(json);

		String startDateTimeUTC = json.getStartDateTimeUTC();
		if (startDateTimeUTC == null)
			throw new InconsistentJsonException("startDateTimeUTC cannot be null");

		try {
			this.startDateTimeUTC = LocalDateTime.parse(startDateTimeUTC, ISO_LOCAL_DATE_TIME);
		}
		catch (DateTimeParseException e) {
			throw new InconsistentJsonException(e);
		}

		String hashingForDeadlines = json.getHashingForDeadlines();
		if (hashingForDeadlines == null)
			throw new InconsistentJsonException("hashingForDeadlines cannot be null");

		this.hashingForDeadlines = HashingAlgorithms.of(hashingForDeadlines);

		String hashingForGenerations = json.getHashingForGenerations();
		if (hashingForGenerations == null)
			throw new InconsistentJsonException("hashingForGenerations cannot be null");

		this.hashingForGenerations = HashingAlgorithms.of(hashingForGenerations);

		String signatureForBlocks = json.getSignatureForBlocks();
		if (signatureForBlocks == null)
			throw new InconsistentJsonException("signatureForBlocks cannot be null");

		this.signatureForBlock = SignatureAlgorithms.of(signatureForBlocks);

		String publicKey = json.getPublicKey();
		if (publicKey == null)
			throw new InconsistentJsonException("publicKey cannot be null");

		try {
			this.publicKey = signatureForBlock.publicKeyFromEncoding(Base58.decode(publicKey));
		}
		catch (Base58ConversionException | InvalidKeySpecException e) {
			throw new InconsistentJsonException(e);
		}

		try {
			this.publicKeyBase58 = Base58.encode(signatureForBlock.encodingOf(this.publicKey));
		}
		catch (InvalidKeyException e) {
			throw new InconsistentJsonException(e);
		}
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
		super(context.readCompactInt(), HashingAlgorithms.of(context.readStringShared()), HashingAlgorithms.of(context.readStringShared())); // TODO: check positive first argument

		try {
			this.startDateTimeUTC = LocalDateTime.parse(context.readStringUnshared(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			this.hashingForDeadlines = HashingAlgorithms.of(context.readStringShared());
			this.hashingForGenerations = HashingAlgorithms.of(context.readStringShared());
			this.signatureForBlock = SignatureAlgorithms.of(context.readStringUnshared());
			byte[] publicKeyEncoding = readPublicKeyEncoding(context);
			this.publicKey = signatureForBlock.publicKeyFromEncoding(publicKeyEncoding);
			this.publicKeyBase58 = Base58.encode(publicKeyEncoding);
		}
		catch (DateTimeParseException | InvalidKeySpecException e) {
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
		super(config.getTargetBlockCreationTime(), config.getHashingForBlocks(), config.getHashingForTransactions());

		try {
			this.startDateTimeUTC = LocalDateTime.parse(context.readStringUnshared(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			this.hashingForDeadlines = config.getHashingForDeadlines();
			this.hashingForGenerations = config.getHashingForGenerations();
			this.signatureForBlock = config.getSignatureForBlocks();
			byte[] publicKeyEncoding = readPublicKeyEncoding(context);
			this.publicKey = signatureForBlock.publicKeyFromEncoding(publicKeyEncoding);
			this.publicKeyBase58 = Base58.encode(publicKeyEncoding);
		}
		catch (DateTimeParseException | InvalidKeySpecException e) {
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
		// the initial acceleration should be fast enough to start mining new blocks without delay;
		// for that, we consider the average value of the first deadline computed for the blockchain;
		// since we do not know how much space has been allocated initially, globally, we choose
		// an average value for the worst case: just one nonce is available in the plots, globally;
		// then we divide for the target block creation time. This might lead to a faster start-up of
		// mining than expected, but it will subsequently slow down to the target block creation time
		var averageValue = new byte[hashingForGenerations.length()];
		averageValue[0] = (byte) 0x80;
		var newValueAsBytes = new BigInteger(1, averageValue).divide(BigInteger.valueOf(getTargetBlockCreationTime())).toByteArray();
		// we recreate an array of the same length as at the beginning
		var dividedValueAsBytes = new byte[averageValue.length];
		System.arraycopy(newValueAsBytes, 0, dividedValueAsBytes, dividedValueAsBytes.length - newValueAsBytes.length, newValueAsBytes.length);
		// we take the first 8 bytes of the divided value
		var firstEightBytes = new byte[] {
			dividedValueAsBytes[0], dividedValueAsBytes[1], dividedValueAsBytes[2], dividedValueAsBytes[3],
			dividedValueAsBytes[4], dividedValueAsBytes[5], dividedValueAsBytes[6], dividedValueAsBytes[7]
		};

		var result = new BigInteger(1, firstEightBytes);
		// acceleration must be strictly positive
		return result.signum() == 0 ? BigInteger.ONE : result;
	}

	@Override
	public long getHeight() {
		return 0L;
	}

	@Override
	public HashingAlgorithm getHashingForDeadlines() {
		return hashingForDeadlines;
	}

	@Override
	public HashingAlgorithm getHashingForGenerations() {
		return hashingForGenerations;
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
			super.equals(other) &&
			startDateTimeUTC.equals(gbd.getStartDateTimeUTC()) &&
			publicKeyBase58.equals(gbd.getPublicKeyForSigningBlockBase58()) &&
			signatureForBlock.equals(gbd.getSignatureForBlock()) &&
			hashingForDeadlines.equals(gbd.getHashingForDeadlines()) &&
			hashingForGenerations.equals(gbd.getHashingForGenerations());
	}

	@Override
	public int hashCode() {
		return super.hashCode() ^ startDateTimeUTC.hashCode() ^ publicKeyBase58.hashCode();
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		try {
			super.into(context);
			context.writeStringUnshared(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(startDateTimeUTC));
			context.writeStringShared(hashingForDeadlines.getName());
			context.writeStringShared(hashingForGenerations.getName());
			context.writeStringUnshared(signatureForBlock.getName());
			writePublicKeyEncoding(context);
		}
		catch (DateTimeException | InvalidKeyException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void intoWithoutConfigurationData(MarshallingContext context) throws IOException {
		try {
			super.intoWithoutConfigurationData(context);
			context.writeStringUnshared(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(startDateTimeUTC));
			writePublicKeyEncoding(context);
		}
		catch (DateTimeException | InvalidKeyException e) {
			throw new IOException(e);
		}
	}

	@Override
	protected void populate(StringBuilder builder) {
		super.populate(builder);
		builder.append("\n* public key of the peer that signed the block: " + publicKeyBase58 + " (" + signatureForBlock + ", base58)");
	}

	@Override
	protected byte[] getNextGenerationSignature() {
		return hashingForGenerations.getHasher(Function.identity()).hash(new byte[] { 13, 1, 19, 73 }); // anything would do
	}

	private void writePublicKeyEncoding(MarshallingContext context) throws IOException, InvalidKeyException {
		byte[] publicKeyEncoding = signatureForBlock.encodingOf(publicKey);

		if (signatureForBlock.publicKeyLength().isEmpty())
			context.writeLengthAndBytes(publicKeyEncoding);
		else
			context.writeBytes(publicKeyEncoding);
	}
}
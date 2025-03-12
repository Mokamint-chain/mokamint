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

package io.mokamint.nonce.internal;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Objects;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.nonce.api.Prolog;

/**
 * Implementation of the prolog of a plot file.
 */
@Immutable
public final class PrologImpl extends AbstractMarshallable implements Prolog {

	/**
	 * The chain identifier of the blockchain of the node using the plots
	 * with this prolog.
	 */
	private final String chainId;

	/**
	 * The signature algorithm that nodes must use to sign blocks having
	 * a deadline with this prolog, with the key {@link #getPublicKeyForSigningBlocks()}.
	 */
	private final SignatureAlgorithm signatureForBlocks;

	/**
	 * The public key that the nodes, using this plots with this prolog,
	 * use to sign new mined blocks.
	 */
	private final PublicKey publicKeyForSigningBlocks;

	/**
	 * The Base58 representation of {@link #publicKeyForSigningBlocks}.
	 */
	private final String publicKeyForSigningBlocksBase58;

	/**
	 * The signature algorithm that miners must use to sign deadlines having
	 * this prolog, with the key {@link #getPublicKeyForSigningDeadlines()}.
	 */
	private final SignatureAlgorithm signatureForDeadlines;

	/**
	 * The public key that miners must use to sign deadlines having this prolog.
	 */
	private final PublicKey publicKeyForSigningDeadlines;

	/**
	 * The Base58 representation of {@link #publicKeyForSigningDeadlines}.
	 */
	private final String publicKeyForSigningDeadlinesBase58;

	/**
	 * Application-specific extra information.
	 */
	private final byte[] extra;

	/**
	 * Creates the prolog of a plot file.
	 * 
	 * @param chainId the chain identifier of the blockchain of the node using the plots with this prolog
	 * @param signatureForBlocks the signature algorithm that nodes must use to sign the
	 *                            blocks having the deadline with the prolog, with {@code publicKeyForSigningBlocks}
	 * @param publicKeyForSigningBlocks the public key that the nodes must use to sign the
	 *                                  blocks having a deadline with the prolog
	 * @param signatureForDeadlines the signature algorithm that miners must use to sign
	 *                              the deadlines with this prolog, with {@code publicKeyForSigningDeadlines}
	 * @param publicKeyForSigningDeadlines the public key that miners must use to sign the deadlines with the prolog
	 * @param extra application-specific extra information
	 * @throws InvalidKeyException if some of the keys is not valid
	 */
	public PrologImpl(String chainId, SignatureAlgorithm signatureForBlocks, PublicKey publicKeyForSigningBlocks,
			SignatureAlgorithm signatureForDeadlines, PublicKey publicKeyForSigningDeadlines, byte[] extra)
					throws InvalidKeyException {

		Objects.requireNonNull(chainId, "chainId cannot be null");
		Objects.requireNonNull(signatureForBlocks, "signatureForBlocks cannot be null");
		Objects.requireNonNull(publicKeyForSigningBlocks, "publicKeyForSigningBlocks cannot be null");
		Objects.requireNonNull(signatureForDeadlines, "signatureForDeadlines cannot be null");
		Objects.requireNonNull(publicKeyForSigningDeadlines, "publicKeyForSigningDeadlines cannot be null");
		Objects.requireNonNull(extra, "extra cannot be null");

		this.chainId = chainId;
		this.signatureForBlocks = signatureForBlocks;
		this.publicKeyForSigningBlocks = publicKeyForSigningBlocks;
		this.signatureForDeadlines = signatureForDeadlines;
		this.publicKeyForSigningDeadlines = publicKeyForSigningDeadlines;
		this.extra = extra.clone();

		verify();

		this.publicKeyForSigningBlocksBase58 = Base58.toBase58String(signatureForBlocks.encodingOf(publicKeyForSigningBlocks));
		this.publicKeyForSigningDeadlinesBase58 = Base58.toBase58String(signatureForDeadlines.encodingOf(publicKeyForSigningDeadlines));
	}

	/**
	 * Unmarshals a prolog from the given context.  It assumes that the prolog was previously marshalled through
	 * {@link Prolog#into(MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @param chainId the chain identifier of the node storing the prolog
	 * @throws NoSuchAlgorithmException if some signature algorithm is not available
	 * @throws IOException if the prolog could not be unmarshalled
	 */
	public PrologImpl(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		try {
			this.chainId = context.readStringUnshared();
			this.signatureForBlocks = SignatureAlgorithms.of(context.readStringShared());
			byte[] publicKeyForSigningBlocksEncoding = unmarshalPublicKeyForSigningBlocks(context);
			this.publicKeyForSigningBlocks = signatureForBlocks.publicKeyFromEncoding(publicKeyForSigningBlocksEncoding);
			this.signatureForDeadlines = SignatureAlgorithms.of(context.readStringShared());
			byte[] plotPublicKeyEncoding = unmarshalPublicKeyForSigningDeadlines(context);
			this.publicKeyForSigningDeadlines = signatureForDeadlines.publicKeyFromEncoding(plotPublicKeyEncoding);
			this.extra = context.readLengthAndBytes("Mismatch in prolog's extra length");

			verify();

			this.publicKeyForSigningBlocksBase58 = Base58.toBase58String(publicKeyForSigningBlocksEncoding);
			this.publicKeyForSigningDeadlinesBase58 = Base58.toBase58String(plotPublicKeyEncoding);
		}
		catch (RuntimeException | InvalidKeySpecException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Unmarshals a prolog from the given context.  It assumes that the prolog was previously marshalled through
	 * {@link Prolog#intoWithoutConfigurationData(MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @param chainId the chain identifier of the node storing the prolog
	 * @param signatureForBlocks the signature algorithm for the blocks
	 * @param signatureForDeadlines the signature algorithm for the deadlines
	 * @throws IOException if the prolog could not be unmarshalled
	 */
	public PrologImpl(UnmarshallingContext context, String chainId, SignatureAlgorithm signatureForBlocks, SignatureAlgorithm signatureForDeadlines) throws IOException {
		try {
			this.chainId = chainId;
			this.signatureForBlocks = signatureForBlocks;
			byte[] publicKeyForSigningBlocksEncoding = unmarshalPublicKeyForSigningBlocks(context);
			this.publicKeyForSigningBlocks = signatureForBlocks.publicKeyFromEncoding(publicKeyForSigningBlocksEncoding);
			this.signatureForDeadlines = signatureForDeadlines;
			byte[] plotPublicKeyEncoding = unmarshalPublicKeyForSigningDeadlines(context);
			this.publicKeyForSigningDeadlines = signatureForDeadlines.publicKeyFromEncoding(plotPublicKeyEncoding);
			this.extra = context.readLengthAndBytes("Mismatch in prolog's extra length");

			verify();

			this.publicKeyForSigningBlocksBase58 = Base58.toBase58String(publicKeyForSigningBlocksEncoding);
			this.publicKeyForSigningDeadlinesBase58 = Base58.toBase58String(plotPublicKeyEncoding);
		}
		catch (RuntimeException | InvalidKeySpecException e) {
			throw new IOException(e);
		}
	}

	private byte[] unmarshalPublicKeyForSigningBlocks(UnmarshallingContext context) throws IOException, InvalidKeySpecException {
		var maybeLength = signatureForBlocks.publicKeyLength();
		if (maybeLength.isEmpty())
			return context.readLengthAndBytes("Mismatch in the length of the public key for signing blocks");
		else
			return context.readBytes(maybeLength.getAsInt(), "Mismatch in the length of the public key for signing blocks");
	}

	private byte[] unmarshalPublicKeyForSigningDeadlines(UnmarshallingContext context) throws IOException, InvalidKeySpecException {
		var maybeLength = signatureForDeadlines.publicKeyLength();
		if (maybeLength.isEmpty())
			return context.readLengthAndBytes("Mismatch in the plot's public key length");
		else
			return context.readBytes(maybeLength.getAsInt(), "Mismatch in the plot's public key length");
	}

	private void verify() {
		if (toByteArray().length > MAX_PROLOG_SIZE)
			throw new IllegalArgumentException("A prolog cannot be longer than " + MAX_PROLOG_SIZE + " bytes");
	}

	@Override
	public String getChainId() {
		return chainId;
	}

	@Override
	public SignatureAlgorithm getSignatureForBlocks() {
		return signatureForBlocks;
	}

	@Override
	public PublicKey getPublicKeyForSigningBlocks() {
		return publicKeyForSigningBlocks;
	}

	@Override
	public String getPublicKeyForSigningBlocksBase58() {
		return publicKeyForSigningBlocksBase58;
	}

	@Override
	public SignatureAlgorithm getSignatureForDeadlines() {
		return signatureForDeadlines;
	}

	@Override
	public PublicKey getPublicKeyForSigningDeadlines() {
		return publicKeyForSigningDeadlines;
	}

	@Override
	public String getPublicKeyForSigningDeadlinesBase58() {
		return publicKeyForSigningDeadlinesBase58;
	}

	@Override
	public byte[] getExtra() {
		return extra.clone();
	}

	@Override
	public String toString() {
		// we avoid printing values whose length is potentially unbound, to avoid log injection problems
		String chainIdTrimmed = chainId;
		if (chainIdTrimmed.length() > 64)
			chainIdTrimmed = chainIdTrimmed.substring(0, 64) + "...";

		String trimmedExtra;
		if (extra.length > 256) {
			var trimmedExtraBytes = new byte[256];
			System.arraycopy(extra, 0, trimmedExtraBytes, 0, trimmedExtraBytes.length);
			trimmedExtra = Hex.toHexString(trimmedExtraBytes);
		}
		else
			trimmedExtra = Hex.toHexString(extra);

		return "chainId: " + chainIdTrimmed + ", nodeSignatureName: " + signatureForBlocks + ", nodePublicKey: " + publicKeyForSigningBlocksBase58 +
			", plotSignatureName: " + signatureForDeadlines + ", plotPublicKey: " + publicKeyForSigningDeadlinesBase58 + ", extra: " + trimmedExtra;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Prolog otherAsProlog)
			return publicKeyForSigningDeadlines.equals(otherAsProlog.getPublicKeyForSigningDeadlines())
				&& publicKeyForSigningBlocks.equals(otherAsProlog.getPublicKeyForSigningBlocks())
				&& chainId.equals(otherAsProlog.getChainId())
				&& Arrays.equals(extra, otherAsProlog.getExtra());
		else
			return false;
	}

	@Override
	public int hashCode() {
		return chainId.hashCode() ^ publicKeyForSigningBlocks.hashCode() ^ publicKeyForSigningDeadlines.hashCode();
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		context.writeStringUnshared(chainId);
		context.writeStringShared(signatureForBlocks.getName());
		marshalPublicKeyForSigningBlocks(context);
		context.writeStringShared(signatureForDeadlines.getName());
		marshalPublicKeyForSigningDeadlines(context);
		context.writeLengthAndBytes(extra);
	}

	@Override
	public void intoWithoutConfigurationData(MarshallingContext context) throws IOException {
		marshalPublicKeyForSigningBlocks(context);
		marshalPublicKeyForSigningDeadlines(context);
		context.writeLengthAndBytes(extra);
	}

	private void marshalPublicKeyForSigningBlocks(MarshallingContext context) throws IOException {
		try {
			var maybeLength = signatureForBlocks.publicKeyLength();
			if (maybeLength.isEmpty())
				context.writeLengthAndBytes(signatureForBlocks.encodingOf(publicKeyForSigningBlocks));
			else
				context.writeBytes(signatureForBlocks.encodingOf(publicKeyForSigningBlocks));
		}
		catch (InvalidKeyException e) {
			throw new IOException("Cannot marshal into bytes the public key for signing blocks", e);
		}
	}

	private void marshalPublicKeyForSigningDeadlines(MarshallingContext context) throws IOException {
		try {
			var maybeLength = signatureForDeadlines.publicKeyLength();
			if (maybeLength.isEmpty())
				context.writeLengthAndBytes(signatureForDeadlines.encodingOf(publicKeyForSigningDeadlines));
			else
				context.writeBytes(signatureForDeadlines.encodingOf(publicKeyForSigningDeadlines));
		}
		catch (InvalidKeyException e) {
			throw new IOException("Cannot marshal into bytes the public key for signing deadlines", e);
		}
	}
}
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

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.nonce.internal.json.PrologJson;

/**
 * Implementation of the prolog of a deadline.
 */
@Immutable
public final class PrologImpl extends AbstractMarshallable implements Prolog {

	/**
	 * The chain identifier of the blockchain of the node using the deadline.
	 */
	private final String chainId;

	/**
	 * The signature algorithm that nodes must use to sign blocks having
	 * a deadline with this prolog, with the key {@link #getPublicKeyForSigningBlocks()}.
	 */
	private final SignatureAlgorithm signatureForBlocks;

	/**
	 * The public key that nodes must use to sign blocks having
	 * a deadline with this prolog, with the key {@link #getPublicKeyForSigningBlocks()}.
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
	 * Creates the prolog of a deadline.
	 * 
	 * @param chainId the chain identifier of the blockchain of the node using the deadline with this prolog
	 * @param signatureForBlocks the signature algorithm that nodes must use to sign the
	 *                           blocks having the deadline with the prolog, with {@code publicKeyForSigningBlocks}
	 * @param publicKeyForSigningBlocks the public key that the nodes must use to sign the
	 *                                  blocks having a deadline with the prolog
	 * @param signatureForDeadlines the signature algorithm that miners must use to sign
	 *                              the deadlines with this prolog, with {@code publicKeyForSigningDeadlines}
	 * @param publicKeyForSigningDeadlines the public key that miners must use to sign the deadlines with the prolog
	 * @param extra application-specific extra information
	 */
	public PrologImpl(String chainId, SignatureAlgorithm signatureForBlocks, PublicKey publicKeyForSigningBlocks,
			SignatureAlgorithm signatureForDeadlines, PublicKey publicKeyForSigningDeadlines, byte[] extra) {

		this(chainId, signatureForBlocks, publicKeyForSigningBlocks, signatureForDeadlines, publicKeyForSigningDeadlines, extra, IllegalArgumentException::new);
	}

	/**
	 * Unmarshals a prolog from the given context.  It assumes that the prolog was previously marshalled through
	 * {@link Prolog#into(MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @param chainId the chain identifier of the node storing the prolog
	 * @throws NoSuchAlgorithmException if some cryptographic algorithm is not available
	 * @throws IOException if the prolog could not be unmarshalled
	 */
	public PrologImpl(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		this(
			context,
			context.readStringUnshared(),
			SignatureAlgorithms.of(context.readStringShared())
		);
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
		this(
			chainId,
			signatureForBlocks,
			unmarshalPublicKey(context, signatureForBlocks),
			signatureForDeadlines,
			unmarshalPublicKey(context, signatureForDeadlines),
			context.readLengthAndBytes("Mismatch in prolog's extra length"),
			IOException::new
		);
	}

	/**
	 * Creates a prolog from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 * @throws NoSuchAlgorithmException if {@code json} refers to some non-available cryptographic algorithm
	 */
	public PrologImpl(PrologJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
		this(
			json.getChainId(),
			SignatureAlgorithms.of(Objects.requireNonNull(json.getSignatureForBlocks(), "signatureForBlocks cannot be null", InconsistentJsonException::new)),
			Base58.fromBase58String(Objects.requireNonNull(json.getPublicKeyForSigningBlocks(), "publicKeyForSigningBlocks cannot be null", InconsistentJsonException::new), InconsistentJsonException::new),
			SignatureAlgorithms.of(Objects.requireNonNull(json.getSignatureForDeadlines(), "signatureForDeadlines cannot be null", InconsistentJsonException::new)),
			Base58.fromBase58String(Objects.requireNonNull(json.getPublicKeyForSigningDeadlines(), "publicKeyForSigningDeadlines cannot be null", InconsistentJsonException::new), InconsistentJsonException::new),
			Hex.fromHexString(Objects.requireNonNull(json.getExtra(), "extra cannot be null", InconsistentJsonException::new), InconsistentJsonException::new),
			InconsistentJsonException::new
		);
	}

	/**
	 * Unmarshals a prolog from the given context.  It assumes that the prolog was previously marshalled through
	 * {@link Prolog#into(MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @param chainId the chain identifier of the node storing the prolog
	 * @throws NoSuchAlgorithmException if some cryptographic algorithm is not available
	 * @throws IOException if the prolog could not be unmarshalled
	 */
	private PrologImpl(UnmarshallingContext context, String chainId, SignatureAlgorithm signatureForBlocks) throws NoSuchAlgorithmException, IOException {
		this(
			context, chainId, signatureForBlocks,
			unmarshalPublicKey(context, signatureForBlocks),
			SignatureAlgorithms.of(context.readStringShared())
		);
	}

	/**
	 * Unmarshals a prolog from the given context.  It assumes that the prolog was previously marshalled through
	 * {@link Prolog#into(MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @param chainId the chain identifier of the node storing the prolog
	 * @throws NoSuchAlgorithmException if some cryptographic algorithm is not available
	 * @throws IOException if the prolog could not be unmarshalled
	 */
	private PrologImpl(UnmarshallingContext context, String chainId, SignatureAlgorithm signatureForBlocks, byte[] encodedPublicKeyForBlocks, SignatureAlgorithm signatureForDeadlines) throws NoSuchAlgorithmException, IOException {
		this(
			chainId, signatureForBlocks, encodedPublicKeyForBlocks, signatureForDeadlines,
			unmarshalPublicKey(context, signatureForDeadlines),
			context.readLengthAndBytes("Mismatch in prolog's extra length"),
			IOException::new
		);
	}

	/**
	 * Creates the prolog of a deadline.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param chainId the chain identifier of the blockchain of the node using the deadline with this prolog
	 * @param signatureForBlocks the signature algorithm that nodes must use to sign the
	 *                           blocks having the deadline with the prolog, with {@code publicKeyForSigningBlocks}
	 * @param publicKeyForSigningBlocks the public key that the nodes must use to sign the
	 *                                  blocks having a deadline with the prolog
	 * @param signatureForDeadlines the signature algorithm that miners must use to sign
	 *                              the deadlines with this prolog, with {@code publicKeyForSigningDeadlines}
	 * @param publicKeyForSigningDeadlines the public key that miners must use to sign the deadlines with the prolog
	 * @param extra application-specific extra information
	 * @param onIllegalArgs the supplier of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> PrologImpl(String chainId, SignatureAlgorithm signatureForBlocks, PublicKey publicKeyForSigningBlocks,
			SignatureAlgorithm signatureForDeadlines, PublicKey publicKeyForSigningDeadlines, byte[] extra, ExceptionSupplierFromMessage<E> onIllegalArgs) throws E {
	
		this(
				chainId,
				signatureForBlocks,
				encodingOf(publicKeyForSigningBlocks, signatureForBlocks, onIllegalArgs),
				signatureForDeadlines,
				encodingOf(publicKeyForSigningDeadlines, signatureForDeadlines, onIllegalArgs),
				extra,
				onIllegalArgs
		);
	}

	/**
	 * Creates the prolog of a deadline.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param chainId the chain identifier of the blockchain of the node using the deadline with this prolog
	 * @param signatureForBlocks the signature algorithm that nodes must use to sign the
	 *                           blocks having the deadline with the prolog, with {@code publicKeyForSigningBlocks}
	 * @param publicKeyForSigningBlocks the encoding of the public key that the nodes must use to sign the
	 *                                  blocks having a deadline with the prolog
	 * @param signatureForDeadlines the signature algorithm that miners must use to sign
	 *                              the deadlines with this prolog, with {@code publicKeyForSigningDeadlines}
	 * @param publicKeyForSigningDeadlines the encoding of the public key that miners must use to sign the deadlines with the prolog
	 * @param extra application-specific extra information
	 * @param onIllegalArgs the supplier of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> PrologImpl(String chainId, SignatureAlgorithm signatureForBlocks, byte[] publicKeyForSigningBlocks,
			SignatureAlgorithm signatureForDeadlines, byte[] publicKeyForSigningDeadlines, byte[] extra, ExceptionSupplierFromMessage<E> onIllegalArgs) throws E {
	
		Objects.requireNonNull(chainId, "chainId cannot be null", onIllegalArgs);
		Objects.requireNonNull(signatureForBlocks, "signatureForBlocks cannot be null", onIllegalArgs);
		Objects.requireNonNull(publicKeyForSigningBlocks, "publicKeyForSigningBlocks cannot be null", onIllegalArgs);
		Objects.requireNonNull(signatureForDeadlines, "signatureForDeadlines cannot be null", onIllegalArgs);
		Objects.requireNonNull(publicKeyForSigningDeadlines, "publicKeyForSigningDeadlines cannot be null", onIllegalArgs);
		Objects.requireNonNull(extra, "extra cannot be null", onIllegalArgs);
	
		this.chainId = chainId;
		this.signatureForBlocks = signatureForBlocks;
		this.signatureForDeadlines = signatureForDeadlines;
	
		try {
			this.publicKeyForSigningBlocks = signatureForBlocks.publicKeyFromEncoding(publicKeyForSigningBlocks);
			this.publicKeyForSigningDeadlines = signatureForDeadlines.publicKeyFromEncoding(publicKeyForSigningDeadlines);
		}
		catch (InvalidKeySpecException e) {
			throw onIllegalArgs.apply("Invalid key: " + e.getMessage());
		}
	
		this.extra = extra.clone();
		this.publicKeyForSigningBlocksBase58 = Base58.toBase58String(publicKeyForSigningBlocks);
		this.publicKeyForSigningDeadlinesBase58 = Base58.toBase58String(publicKeyForSigningDeadlines);
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

		return "chainId: " + chainIdTrimmed + ", signatureForBlocks: " + signatureForBlocks + ", publicKeyForSigningBlocksBase58: " + publicKeyForSigningBlocksBase58 +
			", signatureForDeadlines: " + signatureForDeadlines + ", publicKeyForSigningDeadlinesBase58: " + publicKeyForSigningDeadlinesBase58 + ", extra: " + trimmedExtra;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Prolog otherAsProlog)
			return publicKeyForSigningDeadlines.equals(otherAsProlog.getPublicKeyForSigningDeadlines())
				&& publicKeyForSigningBlocks.equals(otherAsProlog.getPublicKeyForSigningBlocks())
				// TODO: check signature algorithms as well ?
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
		marshalPublicKey(context, signatureForBlocks, publicKeyForSigningBlocks);
		context.writeStringShared(signatureForDeadlines.getName());
		marshalPublicKey(context, signatureForDeadlines, publicKeyForSigningDeadlines);
		context.writeLengthAndBytes(extra);
	}

	@Override
	public void intoWithoutConfigurationData(MarshallingContext context) throws IOException {
		marshalPublicKey(context, signatureForBlocks, publicKeyForSigningBlocks);
		marshalPublicKey(context, signatureForDeadlines, publicKeyForSigningDeadlines);
		context.writeLengthAndBytes(extra);
	}

	private static <E extends Exception> byte[] encodingOf(PublicKey publicKey, SignatureAlgorithm signature, ExceptionSupplierFromMessage<E> onIllegalArgs) throws E {
		try {
			return signature.encodingOf(publicKey);
		}
		catch (InvalidKeyException e) {
			throw onIllegalArgs.apply(e.getMessage());
		}
	}

	private static byte[] unmarshalPublicKey(UnmarshallingContext context, SignatureAlgorithm signature) throws IOException {
		var maybeLength = signature.publicKeyLength();
		if (maybeLength.isEmpty())
			return context.readLengthAndBytes("Mismatch in public key length");
		else
			return context.readBytes(maybeLength.getAsInt(), "Mismatch in public key length");
	}

	private void marshalPublicKey(MarshallingContext context, SignatureAlgorithm signature, PublicKey publicKey) throws IOException {
		try {
			var maybeLength = signature.publicKeyLength();
			if (maybeLength.isEmpty())
				context.writeLengthAndBytes(signature.encodingOf(publicKey));
			else
				context.writeBytes(signature.encodingOf(publicKey));
		}
		catch (InvalidKeyException e) {
			throw new IOException("Cannot marshal public key into bytes", e);
		}
	}
}
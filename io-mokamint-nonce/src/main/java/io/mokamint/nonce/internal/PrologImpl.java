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
import java.util.function.Function;

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
public class PrologImpl extends AbstractMarshallable implements Prolog {

	/**
	 * The chain identifier of the blockchain of the node using the plots
	 * with this prolog.
	 */
	private final String chainId;

	/**
	 * The signature algorithm for the key {@link #getPublicKeyForSigningBlocks()}.
	 */
	private final SignatureAlgorithm<byte[]> signatureForBlocks;

	/**
	 * The public key that the nodes, using this plots with this prolog,
	 * use to sign new mined blocks.
	 */
	private final PublicKey publicKeyForSigningBlocks;

	/**
	 * The signature algorithm for the key {@link #getPlotPublicKey()}.
	 */
	private final SignatureAlgorithm<byte[]> plotSignature;

	/**
	 * The public key that identifies the plots with this prolog.
	 */
	private final PublicKey plotPublicKey;

	/**
	 * Application-specific extra information.
	 */
	private final byte[] extra;

	/**
	 * The Base58 representation of {@link #publicKeyForSigningBlocks}.
	 */
	private final String publicKeyForSigningBlocksBase58;

	/**
	 * The Base58 representation of {@link #plotPublicKey}.
	 */
	private final String plotPublicKeyBase58;

	/**
	 * Creates the prolog of a plot file.
	 * 
	 * @param chainId the chain identifier of the blockchain of the node using the plots with this prolog
	 *  @param signatureForBlocksSupplier the supplier of the signature algorithm used to sign the
	 *                                   blocks having this deadline, with {@code publicKeyForSigningBlocksBase58}
	 * @param publicKeyForSigningBlocks the public key that the nodes, using this plots with this prolog,
	 *                                  use to sign new mined blocks
	 * @param plotSignatureSupplier the supplier of the signature algorithm used by {@code plotPublicKey}
	 * @param plotPublicKey the public key that identifies the plots with this prolog
	 * @param extra application-specific extra information
	 * @throws NoSuchAlgorithmException if some signature algorithm is not available
	 * @throws InvalidKeyException if some of the keys is not valid
	 */
	public PrologImpl(String chainId, SignatureAlgorithm.Supplier<byte[]> signatureForBlocksSupplier, PublicKey publicKeyForSigningBlocks,
			SignatureAlgorithm.Supplier<byte[]> plotSignatureSupplier, PublicKey plotPublicKey, byte[] extra) throws NoSuchAlgorithmException, InvalidKeyException {

		Objects.requireNonNull(chainId, "chainId cannot be null");
		Objects.requireNonNull(signatureForBlocksSupplier, "nodeSignatureType cannot be null");
		Objects.requireNonNull(publicKeyForSigningBlocks, "publicKeyForSigningBlocks cannot be null");
		Objects.requireNonNull(plotSignatureSupplier, "plotSignatureType cannot be null");
		Objects.requireNonNull(plotPublicKey, "plotPublicKey cannot be null");
		Objects.requireNonNull(extra, "extra cannot be null");

		this.chainId = chainId;
		this.signatureForBlocks = signatureForBlocksSupplier.get(Function.identity());
		this.publicKeyForSigningBlocks = publicKeyForSigningBlocks;
		this.plotSignature = plotSignatureSupplier.get(Function.identity());
		this.plotPublicKey = plotPublicKey;
		this.extra = extra.clone();

		verify();

		this.publicKeyForSigningBlocksBase58 = Base58.encode(signatureForBlocks.encodingOf(publicKeyForSigningBlocks));
		this.plotPublicKeyBase58 = Base58.encode(plotSignature.encodingOf(plotPublicKey));
	}

	/**
	 * Creates the prolog of a plot file.
	 * 
	 * @param chainId the chain identifier of the blockchain of the node using the plots with this prolog
	 * @param signatureForBlocksSupplier the supplier of the signature algorithm used to sign the
	 *                                   blocks having this deadline, with {@code publicKeyForSigningBlocksBase58}
	 * @param publicKeyForSigningBlocksBase58 the public key that the nodes, using this plots with this prolog,
	 *                            use to sign new mined blocks; in Base58 format
	 * @param plotSignatureSupplier the supplier of the signature algorithm used by {@code plotPublicKeyBase58}
	 * @param plotPublicKeyBase58 the public key that identifies the plots with this prolog, in Base58 format
	 * @param extra application-specific extra information
	 * @throws NoSuchAlgorithmException if some signature algorithm is not available
	 * @throws InvalidKeySpecException if some of the keys is not valid
	 */
	public PrologImpl(String chainId, SignatureAlgorithm.Supplier<byte[]> signatureForBlocksSupplier, String publicKeyForSigningBlocksBase58,
			SignatureAlgorithm.Supplier<byte[]> plotSignatureSupplier, String plotPublicKeyBase58, byte[] extra) throws NoSuchAlgorithmException, InvalidKeySpecException {

		Objects.requireNonNull(chainId, "chainId cannot be null");
		Objects.requireNonNull(signatureForBlocksSupplier, "nodeSignatureType cannot be null");
		Objects.requireNonNull(publicKeyForSigningBlocksBase58, "publicKeyForSigningBlocksBase58 cannot be null");
		Objects.requireNonNull(plotSignatureSupplier, "plotSignatureType cannot be null");
		Objects.requireNonNull(plotPublicKeyBase58, "plotPublicKeyBase58 cannot be null");
		Objects.requireNonNull(extra, "extra cannot be null");

		this.chainId = chainId;
		this.extra = extra.clone();
		this.signatureForBlocks = signatureForBlocksSupplier.get(Function.identity());
		this.publicKeyForSigningBlocks = signatureForBlocks.publicKeyFromEncoding(Base58.decode(publicKeyForSigningBlocksBase58));
		this.plotSignature = plotSignatureSupplier.get(Function.identity());
		this.plotPublicKey = plotSignature.publicKeyFromEncoding(Base58.decode(plotPublicKeyBase58));

		verify();

		this.publicKeyForSigningBlocksBase58 = publicKeyForSigningBlocksBase58;
		this.plotPublicKeyBase58 = plotPublicKeyBase58;
	}

	/**
	 * Unmarshals a prolog from the given context.
	 * 
	 * @param context the unmarshalling context
	 * @throws NoSuchAlgorithmException if some signature algorithm is not available
	 * @throws IOException if the prolog could not be unmarshalled
	 */
	public PrologImpl(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		try {
			this.chainId = context.readStringUnshared();
			this.signatureForBlocks = SignatureAlgorithms.of(context.readStringShared(), Function.identity());
			byte[] publicKeyForSigningBlocksEncoding = context.readBytes(context.readCompactInt(), "Mismatch in the length of the public key for signing blocks");
			this.publicKeyForSigningBlocks = signatureForBlocks.publicKeyFromEncoding(publicKeyForSigningBlocksEncoding);
			this.plotSignature = SignatureAlgorithms.of(context.readStringShared(), Function.identity());
			byte[] plotPublicKeyEncoding = context.readBytes(context.readCompactInt(), "Mismatch in the plot's public key length");
			this.plotPublicKey = plotSignature.publicKeyFromEncoding(plotPublicKeyEncoding);
			this.extra = context.readBytes(context.readCompactInt(), "Mismatch in prolog's extra length");

			verify();

			this.publicKeyForSigningBlocksBase58 = Base58.encode(publicKeyForSigningBlocksEncoding);
			this.plotPublicKeyBase58 = Base58.encode(plotPublicKeyEncoding);
		}
		catch (RuntimeException | InvalidKeySpecException e) {
			throw new IOException(e);
		}
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
	public SignatureAlgorithm<byte[]> getSignatureForBlocks() {
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
	public SignatureAlgorithm<byte[]> getPlotSignature() {
		return plotSignature;
	}

	@Override
	public PublicKey getPlotPublicKey() {
		return plotPublicKey;
	}

	@Override
	public String getPlotPublicKeyBase58() {
		return plotPublicKeyBase58;
	}

	@Override
	public byte[] getExtra() {
		return extra.clone();
	}

	@Override
	public String toString() {
		return "chainId: " + chainId + ", nodeSignatureName: " + signatureForBlocks.getName() + ", nodePublicKey: " + publicKeyForSigningBlocksBase58 +
			", plotSignatureName: " + plotSignature.getName() + ", plotPublicKey: " + plotPublicKeyBase58 + ", extra: " + Hex.toHexString(extra);
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Prolog p) {
			return plotPublicKey.equals(p.getPlotPublicKey()) && publicKeyForSigningBlocks.equals(p.getPublicKeyForSigningBlocks())
				&& chainId.equals(p.getChainId()) && Arrays.equals(extra, p.getExtra());
		}
		else
			return false;
	}

	@Override
	public int hashCode() {
		return chainId.hashCode() ^ publicKeyForSigningBlocks.hashCode() ^ plotPublicKey.hashCode() ^ Arrays.hashCode(extra);
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		try {
			context.writeStringUnshared(chainId);
			context.writeStringShared(signatureForBlocks.getName());
			var nodePublicKeyBytes = signatureForBlocks.encodingOf(publicKeyForSigningBlocks);
			context.writeCompactInt(nodePublicKeyBytes.length);
			context.write(nodePublicKeyBytes);
			var plotPublicKeyBytes = plotSignature.encodingOf(plotPublicKey);
			context.writeStringShared(plotSignature.getName());
			context.writeCompactInt(plotPublicKeyBytes.length);
			context.write(plotPublicKeyBytes);
			context.writeCompactInt(extra.length);
			context.write(extra);
		}
		catch (InvalidKeyException e) {
			throw new IOException("Cannot marshal the prolog into bytes", e);
		}
	}
}
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
	 * The public key that the nodes, using this plots with this prolog,
	 * use to sign new mined blocks.
	 */
	private final PublicKey nodePublicKey;

	/**
	 * The public key that identifies the plots with this prolog.
	 */
	private final PublicKey plotPublicKey;

	/**
	 * Application-specific extra information.
	 */
	private final byte[] extra;

	/**
	 * The Base58 representation of {@link #nodePublicKey}.
	 */
	private final String nodePublicKeyBase58;

	/**
	 * The Base58 representation of {@link #plotPublicKey}.
	 */
	private final String plotPublicKeyBase58;

	/**
	 * Creates the prolog of a plot file.
	 * 
	 * @param chainId the chain identifier of the blockchain of the node using the plots with this prolog
	 * @param nodePublicKey the public key that the nodes, using this plots with this prolog,
	 *                      use to sign new mined blocks
	 * @param plotPublicKey the public key that identifies the plots with this prolog
	 * @param extra application-specific extra information
	 * @throws NoSuchAlgorithmException if the ed25519 signature algorithm is not available
	 * @throws InvalidKeyException if some of the keys is not an ed25519 valid public key
	 */
	public PrologImpl(String chainId, PublicKey nodePublicKey, PublicKey plotPublicKey, byte[] extra) throws NoSuchAlgorithmException, InvalidKeyException {
		Objects.requireNonNull(chainId, "chainId cannot be null");
		Objects.requireNonNull(nodePublicKey, "nodePublicKey cannot be null");
		Objects.requireNonNull(plotPublicKey, "plotPublicKey cannot be null");
		Objects.requireNonNull(extra, "extra cannot be null");

		this.chainId = chainId;
		this.nodePublicKey = nodePublicKey;
		this.plotPublicKey = plotPublicKey;
		this.extra = extra.clone();

		verify();

		var signature = SignatureAlgorithms.ed25519(Function.identity());
		this.nodePublicKeyBase58 = Base58.encode(signature.encodingOf(nodePublicKey));
		this.plotPublicKeyBase58 = Base58.encode(signature.encodingOf(plotPublicKey));
	}

	/**
	 * Unmarshals a prolog from the given context.
	 * 
	 * @param context the unmarshalling context
	 * @throws NoSuchAlgorithmException if the ed25519 signature algorithm is not available
	 * @throws IOException if the prolog could not be unmarshalled
	 */
	public PrologImpl(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		try {
			this.chainId = context.readStringUnshared();
			var signature = SignatureAlgorithms.ed25519(Function.identity());
			byte[] nodePublicKeyEncoding = context.readBytes(context.readCompactInt(), "Mismatch in the node's public key length");
			this.nodePublicKey = signature.publicKeyFromEncoding(nodePublicKeyEncoding);
			byte[] plotPublicKeyEncoding = context.readBytes(context.readCompactInt(), "Mismatch in the plot's public key length");
			this.plotPublicKey = signature.publicKeyFromEncoding(plotPublicKeyEncoding);
			this.extra = context.readBytes(context.readCompactInt(), "Mismatch in prolog's extra length");

			verify();

			this.nodePublicKeyBase58 = Base58.encode(nodePublicKeyEncoding);
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
	public PublicKey getNodePublicKey() {
		return nodePublicKey;
	}

	@Override
	public String getNodePublicKeyBase58() {
		return nodePublicKeyBase58;
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
	public void into(MarshallingContext context) throws IOException {
		try {
			context.writeStringUnshared(chainId);
			var signature = SignatureAlgorithms.ed25519(Function.identity());
			var nodePublicKeyBytes = signature.encodingOf(nodePublicKey);
			context.writeCompactInt(nodePublicKeyBytes.length);
			context.write(nodePublicKeyBytes);
			var plotPublicKeyBytes = signature.encodingOf(plotPublicKey);
			context.writeCompactInt(plotPublicKeyBytes.length);
			context.write(plotPublicKeyBytes);
			context.writeCompactInt(extra.length);
			context.write(extra);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IOException("Cannot marshal the prolog into bytes", e);
		}
	}

	@Override
	public String toString() {
		return "chainId: " + chainId + ", nodePublicKey: " + nodePublicKeyBase58 + ", plotPublicKey: " + plotPublicKeyBase58 + ", extra: " + Hex.toHexString(extra);
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Prolog p) {
			return plotPublicKey.equals(p.getPlotPublicKey()) && nodePublicKey.equals(p.getNodePublicKey())
				&& chainId.equals(p.getChainId()) && Arrays.equals(extra, p.getExtra());
		}
		else
			return false;
	}

	@Override
	public int hashCode() {
		return chainId.hashCode() ^ nodePublicKey.hashCode() ^ plotPublicKey.hashCode() ^ Arrays.hashCode(extra);
	}
}
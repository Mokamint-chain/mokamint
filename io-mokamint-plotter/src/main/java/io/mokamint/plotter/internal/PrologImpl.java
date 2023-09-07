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

package io.mokamint.plotter.internal;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Objects;
import java.util.function.Function;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.plotter.api.Prolog;

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
	 * Creates the prolog of a plot file.
	 * 
	 * @param chainId the chain identifier of the blockchain of the node using the plots with this prolog
	 * @param nodePublicKey the public key that the nodes, using this plots with this prolog,
	 *                      use to sign new mined blocks
	 * @param plotPublicKey the public key that identifies the plots with this prolog
	 * @param extra application-specific extra information
	 */
	public PrologImpl(String chainId, PublicKey nodePublicKey, PublicKey plotPublicKey, byte[] extra) {
		Objects.requireNonNull(chainId, "chainId cannot be null");
		Objects.requireNonNull(nodePublicKey, "nodePublicKey cannot be null");
		Objects.requireNonNull(plotPublicKey, "plotPublicKey cannot be null");
		Objects.requireNonNull(extra, "extra cannot be null");

		this.chainId = chainId;
		this.nodePublicKey = nodePublicKey;
		this.plotPublicKey = plotPublicKey;
		this.extra = extra.clone();

		verify();
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
			this.nodePublicKey = signature.publicKeyFromEncoding(context.readBytes(context.readCompactInt(), "Mismatch in the node's public key length"));
			this.plotPublicKey = signature.publicKeyFromEncoding(context.readBytes(context.readCompactInt(), "Mismatch in the plot's public key length"));
			this.extra = context.readBytes(context.readCompactInt(), "Mismatch in prolog's extra length");

			verify();
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
	public PublicKey getNodeKey() {
		return nodePublicKey;
	}

	@Override
	public PublicKey getPlotKey() {
		return plotPublicKey;
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
			throw new IOException("Cannot marshal a prolog into bytes", e);
		}
	}
}
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

package io.mokamint.nonce.internal.json;

import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.Hex;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.nonce.internal.PrologImpl;

/**
 * The JSON representation of a {@link Prolog}.
 */
public abstract class PrologJson implements JsonRepresentation<Prolog> {
	private final String chainId;
	private final String signatureForBlocks;
	private final String publicKeyForSigningBlocks;
	private final String signatureForDeadlines;
	private final String publicKeyForSigningDeadlines;
	private final String extra;

	protected PrologJson(Prolog prolog) {
		this.chainId = prolog.getChainId();
		this.signatureForBlocks = prolog.getSignatureForBlocks().getName();
		this.publicKeyForSigningBlocks = prolog.getPublicKeyForSigningBlocksBase58();
		this.signatureForDeadlines = prolog.getSignatureForDeadlines().getName();
		this.publicKeyForSigningDeadlines = prolog.getPublicKeyForSigningDeadlinesBase58();
		this.extra = Hex.toHexString(prolog.getExtra());
	}

	/**
	 * Yields the chain identifier of the blockchain where the deadline is legal.
	 * 
	 * @return the chain identifier
	 */
	public String getChainId() {
		return chainId;
	}

	/**
	 * Yields the name of the signature algorithm that nodes must use to sign blocks
	 * having a deadline with this prolog, with the key {@link #getPublicKeyForSigningBlocks()}.
	 * 
	 * @return the name of the signature algorithm
	 */
	public String getSignatureForBlocks() {
		return signatureForBlocks;
	}

	/**
	 * Yields the Base58-encoded public key that must be used to sign the blocks having
	 * a deadline with this prolog.
	 * 
	 * @return the Base58-encoded public key; this is guaranteed to be a valid key for
	 *         {@link #getSignatureForBlocks()}.
	 */
	public String getPublicKeyForSigningBlocks() {
		return publicKeyForSigningBlocks;
	}

	/**
	 * Yields the name of the signature algorithm used by the keys that identify the miners.
	 * The identifier of the miner of a deadline having this prolog
	 * is the key {@link #getPublicKeyForSigningDeadlines()}. Application
	 * might decide to actually sign deadlines with this algorithm, using the
	 * extra field of the deadlines. In general, there is no need to actually sign the deadlines.
	 * 
	 * @return the name of the signature algorithm
	 */
	public String getSignatureForDeadlines() {
		return signatureForDeadlines;
	}

	/**
	 * Yields the Base58-encoded public key that identifies the miner of a deadline having this prolog.
	 * 
	 * @return the Base58-encoded public key; this is guaranteed to be a valid key for
	 *         {@link #getSignatureForDeadlines()}
	 */
	public String getPublicKeyForSigningDeadlines() {
		return publicKeyForSigningDeadlines;
	}

	/**
	 * Yields application-specific extra data in the prolog.
	 * 
	 * @return the extra data
	 */
	public String getExtra() {
		return extra;
	}

	@Override
	public Prolog unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		return new PrologImpl(this);
	}
}
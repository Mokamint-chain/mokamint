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
	private final String nodeSignatureName; // TODO: improve these names at next release
	private final String nodePublicKey;
	private final String plotSignatureName;
	private final String plotPublicKey;
	private final String extra;

	protected PrologJson(Prolog prolog) {
		this.chainId = prolog.getChainId();
		this.nodeSignatureName = prolog.getSignatureForBlocks().getName();
		this.nodePublicKey = prolog.getPublicKeyForSigningBlocksBase58();
		this.plotSignatureName = prolog.getSignatureForDeadlines().getName();
		this.plotPublicKey = prolog.getPublicKeyForSigningDeadlinesBase58();
		this.extra = Hex.toHexString(prolog.getExtra());
	}

	public String getChainId() {
		return chainId;
	}

	public String getSignatureForBlocks() {
		return nodeSignatureName;
	}

	public String getPublicKeyForSigningBlocks() {
		return nodePublicKey;
	}

	public String getSignatureForDeadlines() {
		return plotSignatureName;
	}

	public String getPublicKeyForSigningDeadlines() {
		return plotPublicKey;
	}

	public String getExtra() {
		return extra;
	}

	@Override
	public Prolog unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		return new PrologImpl(this);
	}
}
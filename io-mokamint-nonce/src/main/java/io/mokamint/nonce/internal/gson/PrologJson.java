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

package io.mokamint.nonce.internal.gson;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Prolog;

/**
 * The JSON representation of a {@link Prolog}.
 */
public abstract class PrologJson implements JsonRepresentation<Prolog> {
	private String chainId;
	private String nodeSignatureName;
	private String nodePublicKey;
	private String plotSignatureName;
	private String plotPublicKey;
	private String extra;

	/**
	 * Used by Gson.
	 */
	protected PrologJson() {}

	protected PrologJson(Prolog prolog) {
		this.chainId = prolog.getChainId();
		this.nodeSignatureName = prolog.getSignatureForBlocks().getName();
		this.nodePublicKey = prolog.getPublicKeyForSigningBlocksBase58();
		this.plotSignatureName = prolog.getSignatureForDeadlines().getName();
		this.plotPublicKey = prolog.getPublicKeyForSigningDeadlinesBase58();
		this.extra = Hex.toHexString(prolog.getExtra());
	}

	@Override
	public Prolog unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		var nodeSignature = SignatureAlgorithms.of(nodeSignatureName);
		var plotSignature = SignatureAlgorithms.of(plotSignatureName);

		try {
			return Prologs.of(chainId, nodeSignature, nodeSignature.publicKeyFromEncoding(Base58.fromBase58String(nodePublicKey, InconsistentJsonException::new)),
					plotSignature, plotSignature.publicKeyFromEncoding(Base58.fromBase58String(plotPublicKey, InconsistentJsonException::new)), Hex.fromHexString(extra, InconsistentJsonException::new));
		}
		catch (InvalidKeyException | InvalidKeySpecException e) {
			throw new InconsistentJsonException(e);
		}
	}
}
/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.miner.messages.internal;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.messages.api.GetBalanceMessage;
import io.mokamint.miner.messages.api.GetMiningSpecificationMessage;
import io.mokamint.miner.messages.internal.json.GetBalanceMessageJson;

/**
 * Implementation of the network message corresponding to {@link Miner#getBalance(java.security.PublicKey)}.
 */
public class GetBalanceMessageImpl extends AbstractRpcMessage implements GetBalanceMessage {

	/**
	 * The signature algorithm of {@link #publicKey}.
	 */
	private final SignatureAlgorithm signature;

	/**
	 * The public key whose balance is required.
	 */
	private final PublicKey publicKey;

	/**
	 * The Base58-encoded version of {@link #publicKey}.
	 */
	private final String publicKeyBase58;

	/**
	 * Creates the message.
	 * 
	 * @param signature the signature algorithm of {@code key}
	 * @param publicKey the public key whose balance is required
	 * @param id the identifier of the message
	 */
	public GetBalanceMessageImpl(SignatureAlgorithm signature, PublicKey publicKey, String id) {
		this(signature, publicKey, id, IllegalArgumentException::new);
	}

	/**
	 * Creates the message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 * @throws NoSuchAlgorithmException if some cryptographic algorithm is not available
	 */
	public GetBalanceMessageImpl(GetBalanceMessageJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
		this(
			SignatureAlgorithms.of(Objects.requireNonNull(json.getSignature(), "signature cannot be null", InconsistentJsonException::new)),
			Base58.fromBase58String(Objects.requireNonNull(json.getPublicKey(), "publicKey cannot be null", InconsistentJsonException::new), InconsistentJsonException::new),
			json.getId(),
			InconsistentJsonException::new
		);
	}

	/**
	 * Creates the message.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param signature the signature algorithm of {@code key}
	 * @param publicKey the public key whose balance is required
	 * @param id the identifier of the message
	 * @param onIllegalArgs the creator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> GetBalanceMessageImpl(SignatureAlgorithm signature, PublicKey publicKey, String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(Objects.requireNonNull(id, "id cannot be null", onIllegalArgs));

		this.signature = Objects.requireNonNull(signature, "signature cannot be null", onIllegalArgs);
		this.publicKey = Objects.requireNonNull(publicKey, "publicKey cannot be null",  onIllegalArgs);
		this.publicKeyBase58 = Base58.toBase58String(encodingOf(publicKey, signature, onIllegalArgs));
	}

	/**
	 * Creates the message.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param signature the signature algorithm of {@code key}
	 * @param publicKey the encoding of the public key whose balance is required
	 * @param id the identifier of the message
	 * @param onIllegalArgs the creator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> GetBalanceMessageImpl(SignatureAlgorithm signature, byte[] publicKey, String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(Objects.requireNonNull(id, "id cannot be null", onIllegalArgs));

		this.signature = Objects.requireNonNull(signature, "signature cannot be null", onIllegalArgs);

		try {
			this.publicKey = signature.publicKeyFromEncoding(Objects.requireNonNull(publicKey, "publicKey cannot be null",  onIllegalArgs));
		}
		catch (InvalidKeySpecException e) {
			throw onIllegalArgs.apply(e.getMessage());
		}

		this.publicKeyBase58 = Base58.toBase58String(publicKey);
	}

	private static <E extends Exception> byte[] encodingOf(PublicKey publicKey, SignatureAlgorithm signature, ExceptionSupplierFromMessage<E> onIllegalArgs) throws E {
		try {
			return signature.encodingOf(publicKey);
		}
		catch (InvalidKeyException e) {
			throw onIllegalArgs.apply(e.getMessage());
		}
	}

	@Override
	public SignatureAlgorithm getSignature() {
		return signature;
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
	public boolean equals(Object other) {
		return other instanceof GetBalanceMessage gbm && super.equals(other) && signature.equals(gbm.getSignature()) && publicKey.equals(gbm.getPublicKey());
	}

	@Override
	protected String getExpectedType() {
		return GetMiningSpecificationMessage.class.getName();
	}
}
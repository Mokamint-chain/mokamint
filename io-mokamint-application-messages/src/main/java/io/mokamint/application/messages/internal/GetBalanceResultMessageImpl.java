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

package io.mokamint.application.messages.internal;

import java.math.BigInteger;
import java.util.Optional;

import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.GetBalanceResultMessage;
import io.mokamint.application.messages.internal.json.GetBalanceResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the
 * {@link Application#getBalance(io.hotmoka.crypto.api.SignatureAlgorithm, java.security.PublicKey)} method.
 */
public class GetBalanceResultMessageImpl extends AbstractRpcMessage implements GetBalanceResultMessage {

	/**
	 * The result of the call.
	 */
	private final Optional<BigInteger> result;

	/**
	 * Creates the message.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 */
	public GetBalanceResultMessageImpl(Optional<BigInteger> result, String id) {
		this(result, id, IllegalArgumentException::new);
	}

	/**
	 * Creates the message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public GetBalanceResultMessageImpl(GetBalanceResultMessageJson json) throws InconsistentJsonException {
		this(json.getResult(), json.getId(), InconsistentJsonException::new);
	}

	/**
	 * Creates the message.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param result the result of the transaction
	 * @param id the identifier of the message
	 * @param onIllegalArgs the creator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> GetBalanceResultMessageImpl(Optional<BigInteger> result, String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(Objects.requireNonNull(id, "id cannot be null", onIllegalArgs));
	
		this.result = result;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetBalanceResultMessage gbrm && super.equals(other) && result.equals(gbrm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetBalanceResultMessage.class.getName();
	}

	@Override
	public Optional<BigInteger> get() {
		return result;
	}
}
/*
Copyright 2024 Fausto Spoto

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

import java.util.Arrays;

import io.hotmoka.crypto.Hex;
import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.GetInitialStateIdResultMessage;
import io.mokamint.application.messages.internal.json.GetInitialStateIdResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link Application#getInitialStateId()} method.
 */
public class GetInitialStateIdResultMessageImpl extends AbstractRpcMessage implements GetInitialStateIdResultMessage {

	/**
	 * The result of the call.
	 */
	private final byte[] result;

	/**
	 * Creates the message.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 */
	public GetInitialStateIdResultMessageImpl(byte[] result, String id) {
		this(Objects.requireNonNull(result, "result cannot be null", IllegalArgumentException::new).clone(), id, IllegalArgumentException::new);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public GetInitialStateIdResultMessageImpl(GetInitialStateIdResultMessageJson json) throws InconsistentJsonException {
		this(
			Hex.fromHexString(Objects.requireNonNull(json.getResult(), "result cannot be null", InconsistentJsonException::new), InconsistentJsonException::new),
			json.getId(),
			InconsistentJsonException::new
		);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param <E> the exception to throw if some argument is illegal
	 * @param result the result of the call
	 * @param id the identifier of the message
	 * @param onIllegalArgs the provider of the exception to throw if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> GetInitialStateIdResultMessageImpl(byte[] result, String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(id, onIllegalArgs);

		this.result = Objects.requireNonNull(result, "result cannot be null", onIllegalArgs);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetInitialStateIdResultMessage gisirm && super.equals(other) && Arrays.equals(result, gisirm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetInitialStateIdResultMessage.class.getName();
	}

	@Override
	public byte[] get() {
		return result.clone();
	}
}
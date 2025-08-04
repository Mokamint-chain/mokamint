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
import io.mokamint.application.messages.api.CheckPrologExtraMessage;
import io.mokamint.application.messages.internal.json.CheckPrologExtraMessageJson;

/**
 * Implementation of the network message corresponding to {@link Application#checkPrologExtra(byte[])}.
 */
public class CheckPrologExtraMessageImpl extends AbstractRpcMessage implements CheckPrologExtraMessage {
	private final byte[] extra;

	/**
	 * Creates the message.
	 * 
	 * @param extra the extra, application-specific bytes of the prolog
	 * @param id the identifier of the message
	 */
	public CheckPrologExtraMessageImpl(byte[] extra, String id) {
		this(Objects.requireNonNull(extra, "extra cannot be null", IllegalArgumentException::new).clone(), id, IllegalArgumentException::new);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public CheckPrologExtraMessageImpl(CheckPrologExtraMessageJson json) throws InconsistentJsonException {
		this(
			Hex.fromHexString(Objects.requireNonNull(json.getExtra(), "extra cannot be null", InconsistentJsonException::new), InconsistentJsonException::new),
			json.getId(),
			InconsistentJsonException::new
		);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param <E> the exception to throw if some argument is illegal
	 * @param extra the extra, application-specific bytes of the prolog
	 * @param id the identifier of the message
	 * @param onIllegalArgs the provider of the exception to throw if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> CheckPrologExtraMessageImpl(byte[] extra, String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(id, onIllegalArgs);

		this.extra = Objects.requireNonNull(extra, "extra cannot be null", onIllegalArgs).clone();
	}

	@Override
	public byte[] getExtra() {
		return extra.clone();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof CheckPrologExtraMessage cpem && super.equals(other) && Arrays.equals(extra, cpem.getExtra());
	}

	@Override
	protected String getExpectedType() {
		return CheckPrologExtraMessage.class.getName();
	}
}
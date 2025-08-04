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

import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.CheckPrologExtraResultMessage;
import io.mokamint.application.messages.internal.json.CheckPrologExtraResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link Application#checkPrologExtra(byte[])} method.
 */
public class CheckPrologExtraResultMessageImpl extends AbstractRpcMessage implements CheckPrologExtraResultMessage {

	/**
	 * The result of the call.
	 */
	private final boolean result;

	/**
	 * Creates the message.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 */
	public CheckPrologExtraResultMessageImpl(boolean result, String id) {
		this(result, id, IllegalArgumentException::new);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public CheckPrologExtraResultMessageImpl(CheckPrologExtraResultMessageJson json) throws InconsistentJsonException {
		this(json.getResult(), json.getId(), InconsistentJsonException::new);
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
	private <E extends Exception> CheckPrologExtraResultMessageImpl(boolean result, String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(id, onIllegalArgs);

		this.result = result;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof CheckPrologExtraResultMessage cperm && super.equals(other) && result == cperm.get().booleanValue();
	}

	@Override
	protected String getExpectedType() {
		return CheckPrologExtraResultMessage.class.getName();
	}

	@Override
	public Boolean get() {
		return result;
	}
}
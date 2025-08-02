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

import java.util.Objects;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.GetRepresentationResultMessage;
import io.mokamint.application.messages.internal.json.GetRepresentationResultMessageJson;
import io.mokamint.node.api.Transaction;

/**
 * Implementation of the network message corresponding to the result of the {@link Application#getRepresentation(Transaction)} method.
 */
public class GetRepresentationResultMessageImpl extends AbstractRpcMessage implements GetRepresentationResultMessage {

	/**
	 * The result of the call.
	 */
	private final String result;

	/**
	 * Creates the message.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 */
	public GetRepresentationResultMessageImpl(String result, String id) {
		super(id);

		this.result = result;
	}

	/**
	 * Creates a message from its JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public GetRepresentationResultMessageImpl(GetRepresentationResultMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		String result = json.getResult();
		if (result == null)
			throw new InconsistentJsonException("result cannot be null");

		this.result = result;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetRepresentationResultMessage grrm && super.equals(other) && Objects.equals(result, grrm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetRepresentationResultMessage.class.getName();
	}

	@Override
	public String get() {
		return result;
	}
}
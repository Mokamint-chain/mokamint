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

package io.mokamint.node.messages.internal;

import java.util.Objects;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.messages.api.AddTransactionResultMessage;
import io.mokamint.node.messages.internal.gson.AddTransactionResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link PublicNode#add(io.mokamint.node.api.Transaction)} method.
 */
public class AddTransactionResultMessageImpl extends AbstractRpcMessage implements AddTransactionResultMessage {

	/**
	 * The result of the call.
	 */
	private final MempoolEntry result;

	/**
	 * Creates the message.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 */
	public AddTransactionResultMessageImpl(MempoolEntry result, String id) {
		super(id);

		this.result = Objects.requireNonNull(result, "result cannot be null");
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public AddTransactionResultMessageImpl(AddTransactionResultMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		var result = json.getResult();
		if (result == null)
			throw new InconsistentJsonException("result cannot be null");

		this.result = result.unmap();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof AddTransactionResultMessage atrm && super.equals(other) && result.equals(atrm.get());
	}

	@Override
	protected String getExpectedType() {
		return AddTransactionResultMessage.class.getName();
	}

	@Override
	public MempoolEntry get() {
		return result;
	}
}
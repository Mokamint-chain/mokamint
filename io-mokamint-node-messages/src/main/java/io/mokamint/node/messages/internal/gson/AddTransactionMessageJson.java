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

package io.mokamint.node.messages.internal.gson;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.Transactions;
import io.mokamint.node.messages.AddTransactionMessages;
import io.mokamint.node.messages.api.AddTransactionMessage;

/**
 * The JSON representation of a {@link AddTransactionMessage}.
 */
public abstract class AddTransactionMessageJson extends AbstractRpcMessageJsonRepresentation<AddTransactionMessage> {
	private final Transactions.Json transaction;

	protected AddTransactionMessageJson(AddTransactionMessage message) {
		super(message);

		this.transaction = new Transactions.Json(message.getTransaction());
	}

	@Override
	public AddTransactionMessage unmap() throws InconsistentJsonException {
		return AddTransactionMessages.of(transaction.unmap(), getId());
	}

	@Override
	protected String getExpectedType() {
		return AddTransactionMessage.class.getName();
	}
}
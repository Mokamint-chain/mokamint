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

package io.mokamint.application.messages.internal.gson;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.messages.CheckTransactionMessages;
import io.mokamint.application.messages.api.CheckTransactionMessage;
import io.mokamint.node.Transactions;

/**
 * The JSON representation of an {@link CheckTransactionMessage}.
 */
public abstract class CheckTransactionMessageJson extends AbstractRpcMessageJsonRepresentation<CheckTransactionMessage> {
	private final Transactions.Json transaction;

	protected CheckTransactionMessageJson(CheckTransactionMessage message) {
		super(message);

		this.transaction = new Transactions.Json(message.getTransaction());
	}

	@Override
	public CheckTransactionMessage unmap() throws InconsistentJsonException {
		return CheckTransactionMessages.of(transaction.unmap(), getId());
	}

	@Override
	protected String getExpectedType() {
		return CheckTransactionMessage.class.getName();
	}
}
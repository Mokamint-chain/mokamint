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

package io.mokamint.node.messages.internal.gson;

import java.util.Optional;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.Transactions;
import io.mokamint.node.messages.GetTransactionResultMessages;
import io.mokamint.node.messages.api.GetTransactionResultMessage;

/**
 * The JSON representation of a {@link GetTransactionResultMessage}.
 */
public abstract class GetTransactionResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetTransactionResultMessage> {
	private final Transactions.Json transaction;

	protected GetTransactionResultMessageJson(GetTransactionResultMessage message) {
		super(message);

		this.transaction = message.get().map(Transactions.Json::new).orElse(null);
	}

	@Override
	public GetTransactionResultMessage unmap() throws InconsistentJsonException {
		return GetTransactionResultMessages.of(Optional.ofNullable(transaction == null ? null : transaction.unmap()), getId());
	}

	@Override
	protected String getExpectedType() {
		return GetTransactionResultMessage.class.getName();
	}
}
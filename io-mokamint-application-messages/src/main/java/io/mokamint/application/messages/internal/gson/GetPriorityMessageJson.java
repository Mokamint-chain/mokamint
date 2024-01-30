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

import io.hotmoka.crypto.Base64ConversionException;
import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.application.messages.GetPriorityMessages;
import io.mokamint.application.messages.api.GetPriorityMessage;
import io.mokamint.node.Transactions;

/**
 * The JSON representation of an {@link GetPriorityMessage}.
 */
public abstract class GetPriorityMessageJson extends AbstractRpcMessageJsonRepresentation<GetPriorityMessage> {
	private final Transactions.Json transaction;

	protected GetPriorityMessageJson(GetPriorityMessage message) {
		super(message);

		this.transaction = new Transactions.Json(message.getTransaction());
	}

	@Override
	public GetPriorityMessage unmap() throws Base64ConversionException {
		return GetPriorityMessages.of(transaction.unmap(), getId());
	}

	@Override
	protected String getExpectedType() {
		return GetPriorityMessage.class.getName();
	}
}
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
import io.mokamint.application.messages.DeliverTransactionMessages;
import io.mokamint.application.messages.api.DeliverTransactionMessage;
import io.mokamint.node.Transactions;

/**
 * The JSON representation of an {@link DeliverTransactionMessage}.
 */
public abstract class DeliverTransactionMessageJson extends AbstractRpcMessageJsonRepresentation<DeliverTransactionMessage> {
	private final Transactions.Json transaction;
	private final int groupId;

	protected DeliverTransactionMessageJson(DeliverTransactionMessage message) {
		super(message);

		this.transaction = new Transactions.Json(message.getTransaction());
		this.groupId = message.getGroupId();
	}

	@Override
	public DeliverTransactionMessage unmap() throws Base64ConversionException {
		return DeliverTransactionMessages.of(transaction.unmap(), groupId, getId());
	}

	@Override
	protected String getExpectedType() {
		return DeliverTransactionMessage.class.getName();
	}
}
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

package io.mokamint.node.messages.internal;

import java.util.Objects;
import java.util.Optional;

import io.hotmoka.crypto.Base64;
import io.hotmoka.crypto.Base64ConversionException;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.messages.api.GetTransactionResultMessage;
import io.mokamint.node.messages.internal.gson.GetTransactionResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link PublicNode#getTransaction(byte[])} method.
 */
public class GetTransactionResultMessageImpl extends AbstractRpcMessage implements GetTransactionResultMessage {
	private final Optional<Transaction> transaction;

	/**
	 * Creates the message.
	 * 
	 * @param transaction the transaction in the message, if any
	 * @param id the identifier of the message
	 */
	public GetTransactionResultMessageImpl(Optional<Transaction> transaction, String id) {
		super(id);

		this.transaction = Objects.requireNonNull(transaction, "transaction cannot be null");
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public GetTransactionResultMessageImpl(GetTransactionResultMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		var maybeTransactionBase64 = json.getTransaction();
		if (maybeTransactionBase64.isEmpty())
			this.transaction = Optional.empty();
		else {
			try {
				this.transaction = Optional.of(Transactions.of(Base64.fromBase64String(maybeTransactionBase64.get())));
			}
			catch (Base64ConversionException e) {
				throw new InconsistentJsonException(e);
			}
		}
	}

	@Override
	public Optional<Transaction> get() {
		return transaction;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetTransactionResultMessage gtrm && super.equals(other) && Objects.equals(transaction, gtrm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetTransactionResultMessage.class.getName();
	}
}
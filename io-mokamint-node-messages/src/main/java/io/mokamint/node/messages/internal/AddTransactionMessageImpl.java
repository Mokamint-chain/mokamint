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

import io.hotmoka.crypto.Base64;
import io.hotmoka.crypto.Base64ConversionException;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.messages.api.AddTransactionMessage;
import io.mokamint.node.messages.internal.gson.AddTransactionMessageJson;

/**
 * Implementation of the network message corresponding to
 * the {@link PublicNode#post(io.mokamint.node.api.Transaction)} method of a node.
 */
public class AddTransactionMessageImpl extends AbstractRpcMessage implements AddTransactionMessage {
	private final Transaction transaction;

	/**
	 * Creates the message.
	 * 
	 * @param transaction the {@code transaction} parameter of the method
	 * @param id the identifier of the message
	 */
	public AddTransactionMessageImpl(Transaction transaction, String id) {
		super(id);

		this.transaction = Objects.requireNonNull(transaction, "transaction cannot be null");
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public AddTransactionMessageImpl(AddTransactionMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		var transactionBase64 = json.getTransaction();
		if (transactionBase64 == null)
			throw new InconsistentJsonException("transaction cannot be null");

		try {
			this.transaction = Transactions.of(Base64.fromBase64String(transactionBase64));
		}
		catch (Base64ConversionException e) {
			throw new InconsistentJsonException(e);
		}
	}

	@Override
	public Transaction getTransaction() {
		return transaction;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof AddTransactionMessage ptm && super.equals(other) && transaction.equals(ptm.getTransaction());
	}

	@Override
	protected String getExpectedType() {
		return AddTransactionMessage.class.getName();
	}
}
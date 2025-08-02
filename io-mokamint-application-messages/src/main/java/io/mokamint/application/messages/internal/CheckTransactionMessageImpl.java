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

import io.hotmoka.crypto.Base64;
import io.hotmoka.crypto.Base64ConversionException;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.CheckTransactionMessage;
import io.mokamint.application.messages.internal.json.CheckTransactionMessageJson;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.Transaction;

/**
 * Implementation of the network message corresponding to {@link Application#checkTransaction(Transaction)}.
 */
public class CheckTransactionMessageImpl extends AbstractRpcMessage implements CheckTransactionMessage {
	private final Transaction transaction;

	/**
	 * Creates the message.
	 * 
	 * @param transaction the transaction in the message
	 * @param id the identifier of the message
	 */
	public CheckTransactionMessageImpl(Transaction transaction, String id) {
		super(id);

		this.transaction = Objects.requireNonNull(transaction, "transaction cannot be null");
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public CheckTransactionMessageImpl(CheckTransactionMessageJson json) throws InconsistentJsonException {
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
		return other instanceof CheckTransactionMessage ctm && super.equals(other) && transaction.equals(ctm.getTransaction());
	}

	@Override
	protected String getExpectedType() {
		return CheckTransactionMessage.class.getName();
	}
}
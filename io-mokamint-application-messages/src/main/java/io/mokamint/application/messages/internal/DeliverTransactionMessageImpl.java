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
import io.mokamint.application.messages.api.DeliverTransactionMessage;
import io.mokamint.application.messages.internal.json.DeliverTransactionMessageJson;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.Transaction;

/**
 * Implementation of the network message corresponding to {@link Application#deliverTransaction(Transaction, int)}.
 */
public class DeliverTransactionMessageImpl extends AbstractRpcMessage implements DeliverTransactionMessage {
	private final Transaction transaction;
	private final int groupId;

	/**
	 * Creates the message.
	 * 
	 * @param groupId the identifier of the group of transactions in the message
	 * @param transaction the transaction in the message
	 * @param id the identifier of the message
	 */
	public DeliverTransactionMessageImpl(int groupId, Transaction transaction, String id) {
		super(id);

		this.transaction = Objects.requireNonNull(transaction, "transaction cannot be null");
		this.groupId = groupId;
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public DeliverTransactionMessageImpl(DeliverTransactionMessageJson json) throws InconsistentJsonException {
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

		this.groupId = json.getGroupId();
	}

	@Override
	public Transaction getTransaction() {
		return transaction;
	}

	@Override
	public int getGroupId() {
		return groupId;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof DeliverTransactionMessage dtm && super.equals(other)
			&& transaction.equals(dtm.getTransaction())
			&& groupId == dtm.getGroupId();
	}

	@Override
	protected String getExpectedType() {
		return DeliverTransactionMessage.class.getName();
	}
}
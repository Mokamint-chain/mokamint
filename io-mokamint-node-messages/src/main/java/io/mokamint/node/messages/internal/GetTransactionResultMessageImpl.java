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

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.messages.api.GetTransactionResultMessage;

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
		transaction.map(Objects::requireNonNull);
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
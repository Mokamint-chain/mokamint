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

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.GetPriorityMessage;
import io.mokamint.node.api.Transaction;

/**
 * Implementation of the network message corresponding to {@link Application#getPriority(Transaction)}.
 */
public class GetPriorityMessageImpl extends AbstractRpcMessage implements GetPriorityMessage {
	private final Transaction transaction;

	/**
	 * Creates the message.
	 * 
	 * @param transaction the transaction in the message
	 * @param id the identifier of the message
	 */
	public GetPriorityMessageImpl(Transaction transaction, String id) {
		super(id);

		this.transaction = Objects.requireNonNull(transaction, "transaction cannot be null");
	}

	@Override
	public Transaction getTransaction() {
		return transaction;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetPriorityMessage cpm && super.equals(other) && transaction.equals(cpm.getTransaction());
	}

	@Override
	protected String getExpectedType() {
		return GetPriorityMessage.class.getName();
	}
}
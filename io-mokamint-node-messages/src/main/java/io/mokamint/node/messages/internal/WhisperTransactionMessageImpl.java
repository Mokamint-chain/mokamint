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

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.messages.api.WhisperTransactionMessage;

/**
 * Implementation of the network message sent to whisper a transaction between whisperers.
 */
public class WhisperTransactionMessageImpl extends AbstractRpcMessage implements WhisperTransactionMessage {

	/**
	 * The whispered transaction.
	 */
	private final Transaction transaction;

	/**
	 * Creates the message.
	 * 
	 * @param transaction the whispered transaction
	 * @param id the identifier of the message
	 */
	public WhisperTransactionMessageImpl(Transaction transaction, String id) {
		super(id);

		this.transaction = Objects.requireNonNull(transaction, "transaction cannot be null");
	}

	@Override
	public Transaction getTransaction() {
		return transaction;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof WhisperTransactionMessage wtm && super.equals(other) && transaction.equals(wtm.getTransaction());
	}

	@Override
	protected String getExpectedType() {
		return WhisperTransactionMessage.class.getName();
	}
}
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

package io.mokamint.application.messages.internal.json;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.messages.api.DeliverTransactionMessage;
import io.mokamint.application.messages.internal.DeliverTransactionMessageImpl;

/**
 * The JSON representation of an {@link DeliverTransactionMessage}.
 */
public abstract class DeliverTransactionMessageJson extends AbstractRpcMessageJsonRepresentation<DeliverTransactionMessage> {
	private final String transaction;
	private final int groupId;

	protected DeliverTransactionMessageJson(DeliverTransactionMessage message) {
		super(message);

		this.transaction = message.getTransaction().toBase64String();
		this.groupId = message.getGroupId();
	}

	public String getTransaction() {
		return transaction;
	}

	public int getGroupId() {
		return groupId;
	}

	@Override
	public DeliverTransactionMessage unmap() throws InconsistentJsonException {
		return new DeliverTransactionMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return DeliverTransactionMessage.class.getName();
	}
}
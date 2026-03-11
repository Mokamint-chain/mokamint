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
import io.mokamint.application.messages.api.ExecuteTransactionMessage;
import io.mokamint.application.messages.internal.ExecuteExecuteMessageImpl;

/**
 * The JSON representation of an {@link ExecuteTransactionMessage}.
 */
public abstract class ExecuteTransactionMessageJson extends AbstractRpcMessageJsonRepresentation<ExecuteTransactionMessage> {
	private final String transaction;
	private final int groupId;

	protected ExecuteTransactionMessageJson(ExecuteTransactionMessage message) {
		super(message);

		this.transaction = message.getRequest().toBase64String();
		this.groupId = message.getGroupId();
	}

	public String getTransaction() {
		return transaction;
	}

	public int getGroupId() {
		return groupId;
	}

	@Override
	public ExecuteTransactionMessage unmap() throws InconsistentJsonException {
		return new ExecuteExecuteMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return ExecuteTransactionMessage.class.getName();
	}
}
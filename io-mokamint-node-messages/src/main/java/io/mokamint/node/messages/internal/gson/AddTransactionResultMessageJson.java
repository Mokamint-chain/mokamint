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

package io.mokamint.node.messages.internal.gson;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.MempoolEntries;
import io.mokamint.node.messages.AddTransactionResultMessages;
import io.mokamint.node.messages.api.AddTransactionResultMessage;

/**
 * The JSON representation of a {@link AddTransactionResultMessage}.
 */
public abstract class AddTransactionResultMessageJson extends AbstractRpcMessageJsonRepresentation<AddTransactionResultMessage> {
	private final MempoolEntries.Json result;

	protected AddTransactionResultMessageJson(AddTransactionResultMessage message) {
		super(message);

		this.result = new MempoolEntries.Json(message.get());
	}

	@Override
	public AddTransactionResultMessage unmap() throws InconsistentJsonException {
		return AddTransactionResultMessages.of(result.unmap(), getId());
	}

	@Override
	protected String getExpectedType() {
		return AddTransactionResultMessage.class.getName();
	}
}
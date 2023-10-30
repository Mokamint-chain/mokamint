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
import io.mokamint.node.messages.PostTransactionResultMessages;
import io.mokamint.node.messages.api.PostTransactionResultMessage;

/**
 * The JSON representation of a {@link PostTransactionResultMessage}.
 */
public abstract class PostTransactionResultMessageJson extends AbstractRpcMessageJsonRepresentation<PostTransactionResultMessage> {
	private final boolean result;

	protected PostTransactionResultMessageJson(PostTransactionResultMessage message) {
		super(message);

		this.result = message.get();
	}

	@Override
	public PostTransactionResultMessage unmap() {
		return PostTransactionResultMessages.of(result, getId());
	}

	@Override
	protected String getExpectedType() {
		return PostTransactionResultMessage.class.getName();
	}
}
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

package io.mokamint.node.messages.internal.json;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.messages.api.WhisperRequestMessage;
import io.mokamint.node.messages.internal.WhisperTransactionMessageImpl;

/**
 * The JSON representation of an {@link WhisperRequestMessage}.
 */
public abstract class WhisperRequestMessageJson extends AbstractRpcMessageJsonRepresentation<WhisperRequestMessage> {
	private final String request;

	protected WhisperRequestMessageJson(WhisperRequestMessage message) {
		super(message);

		this.request = message.getWhispered().toBase64String();
	}

	public String getRequest() {
		return request;
	}

	@Override
	public WhisperRequestMessage unmap() throws InconsistentJsonException {
		return new WhisperTransactionMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return WhisperRequestMessage.class.getName();
	}
}
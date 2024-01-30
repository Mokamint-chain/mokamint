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

package io.mokamint.application.messages.internal.gson;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.application.messages.GetPriorityResultMessages;
import io.mokamint.application.messages.api.GetPriorityResultMessage;

/**
 * The JSON representation of a {@link GetPriorityResultMessage}.
 */
public abstract class GetPriorityResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetPriorityResultMessage> {
	private final long result;

	protected GetPriorityResultMessageJson(GetPriorityResultMessage message) {
		super(message);

		this.result = message.get();
	}

	@Override
	public GetPriorityResultMessage unmap() {
		return GetPriorityResultMessages.of(result, getId());
	}

	@Override
	protected String getExpectedType() {
		return GetPriorityResultMessage.class.getName();
	}
}
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

package io.mokamint.node.messages.internal.gson;

import java.util.Optional;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.node.messages.GetTransactionRepresentationResultMessages;
import io.mokamint.node.messages.api.GetTransactionRepresentationResultMessage;

/**
 * The JSON representation of a {@link GetTransactionRepresentationResultMessage}.
 */
public abstract class GetTransactionRepresentationResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetTransactionRepresentationResultMessage> {
	private final String representation;

	protected GetTransactionRepresentationResultMessageJson(GetTransactionRepresentationResultMessage message) {
		super(message);

		this.representation = message.get().orElse(null);
	}

	@Override
	public GetTransactionRepresentationResultMessage unmap() {
		return GetTransactionRepresentationResultMessages.of(Optional.ofNullable(representation), getId());
	}

	@Override
	protected String getExpectedType() {
		return GetTransactionRepresentationResultMessage.class.getName();
	}
}
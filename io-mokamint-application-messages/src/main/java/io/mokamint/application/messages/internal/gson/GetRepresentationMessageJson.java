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

import io.hotmoka.crypto.Base64ConversionException;
import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.application.messages.GetRepresentationMessages;
import io.mokamint.application.messages.api.GetRepresentationMessage;
import io.mokamint.node.Transactions;

/**
 * The JSON representation of an {@link GetRepresentationMessage}.
 */
public abstract class GetRepresentationMessageJson extends AbstractRpcMessageJsonRepresentation<GetRepresentationMessage> {
	private final Transactions.Json transaction;

	protected GetRepresentationMessageJson(GetRepresentationMessage message) {
		super(message);

		this.transaction = new Transactions.Json(message.getTransaction());
	}

	@Override
	public GetRepresentationMessage unmap() throws Base64ConversionException {
		return GetRepresentationMessages.of(transaction.unmap(), getId());
	}

	@Override
	protected String getExpectedType() {
		return GetRepresentationMessage.class.getName();
	}
}
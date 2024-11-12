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

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.messages.GetTransactionAddressMessages;
import io.mokamint.node.messages.api.GetTransactionAddressMessage;

/**
 * The JSON representation of a {@link GetTransactionAddressMessage}.
 */
public abstract class GetTransactionAddressMessageJson extends AbstractRpcMessageJsonRepresentation<GetTransactionAddressMessage> {
	private final String hash;

	protected GetTransactionAddressMessageJson(GetTransactionAddressMessage message) {
		super(message);

		this.hash = Hex.toHexString(message.getHash());
	}

	@Override
	public GetTransactionAddressMessage unmap() throws InconsistentJsonException {
		try {
			return GetTransactionAddressMessages.of(Hex.fromHexString(hash), getId());
		}
		catch (HexConversionException e) {
			throw new InconsistentJsonException(e);
		}
	}

	@Override
	protected String getExpectedType() {
		return GetTransactionAddressMessage.class.getName();
	}
}
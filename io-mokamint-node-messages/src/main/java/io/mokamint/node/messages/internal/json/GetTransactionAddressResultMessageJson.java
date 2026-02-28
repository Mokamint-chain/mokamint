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

package io.mokamint.node.messages.internal.json;

import java.util.Optional;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.RequestAddresses;
import io.mokamint.node.messages.api.GetRequestAddressResultMessage;
import io.mokamint.node.messages.internal.GetTransactionAddressResultMessageImpl;

/**
 * The JSON representation of a {@link GetRequestAddressResultMessage}.
 */
public abstract class GetTransactionAddressResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetRequestAddressResultMessage> {
	private final RequestAddresses.Json address;

	protected GetTransactionAddressResultMessageJson(GetRequestAddressResultMessage message) {
		super(message);

		this.address = message.get().map(RequestAddresses.Json::new).orElse(null);
	}

	public Optional<RequestAddresses.Json> getAddress() {
		return Optional.ofNullable(address);
	}

	@Override
	public GetRequestAddressResultMessage unmap() throws InconsistentJsonException {
		return new GetTransactionAddressResultMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return GetRequestAddressResultMessage.class.getName();
	}
}
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

package io.mokamint.node.messages.internal;

import java.util.Objects;
import java.util.Optional;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.messages.api.GetTransactionAddressResultMessage;
import io.mokamint.node.messages.internal.gson.GetTransactionAddressResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link PublicNode#getTransactionAddress(byte[])} method.
 */
public class GetTransactionAddressResultMessageImpl extends AbstractRpcMessage implements GetTransactionAddressResultMessage {
	private final Optional<TransactionAddress> address;

	/**
	 * Creates the message.
	 * 
	 * @param address the address of the transaction in the message, if any
	 * @param id the identifier of the message
	 */
	public GetTransactionAddressResultMessageImpl(Optional<TransactionAddress> address, String id) {
		super(id);

		this.address = Objects.requireNonNull(address, "address cannot be null");
		address.map(Objects::requireNonNull);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public GetTransactionAddressResultMessageImpl(GetTransactionAddressResultMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		var address = json.getAddress();
		this.address = address.isEmpty() ? Optional.empty() : Optional.of(address.get().unmap());
	}

	@Override
	public Optional<TransactionAddress> get() {
		return address;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetTransactionAddressResultMessage gtarm && super.equals(other) && address.equals(gtarm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetTransactionAddressResultMessage.class.getName();
	}
}
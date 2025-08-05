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
import io.mokamint.node.messages.api.GetTransactionRepresentationResultMessage;
import io.mokamint.node.messages.internal.json.GetTransactionRepresentationResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link PublicNode#getTransactionRepresentation(byte[])} method.
 */
public class GetTransactionRepresentationResultMessageImpl extends AbstractRpcMessage implements GetTransactionRepresentationResultMessage {
	private final Optional<String> representation;

	/**
	 * Creates the message.
	 * 
	 * @param representation the representation of the transaction in the message, if any
	 * @param id the identifier of the message
	 */
	public GetTransactionRepresentationResultMessageImpl(Optional<String> representation, String id) {
		super(id);

		this.representation = Objects.requireNonNull(representation, "representation cannot be null");
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public GetTransactionRepresentationResultMessageImpl(GetTransactionRepresentationResultMessageJson json) {
		super(json.getId());

		this.representation = json.getRepresentation();
	}

	@Override
	public Optional<String> get() {
		return representation;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetTransactionRepresentationResultMessage gtrrm && super.equals(other) && Objects.equals(representation, gtrrm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetTransactionRepresentationResultMessage.class.getName();
	}
}
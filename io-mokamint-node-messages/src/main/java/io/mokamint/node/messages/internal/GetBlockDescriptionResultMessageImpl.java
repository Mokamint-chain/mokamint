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

package io.mokamint.node.messages.internal;

import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.api.GetBlockDescriptionResultMessage;
import io.mokamint.node.messages.internal.json.GetBlockDescriptionResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link PublicNode#getBlockDescription(byte[])} method.
 */
public class GetBlockDescriptionResultMessageImpl extends AbstractRpcMessage implements GetBlockDescriptionResultMessage {

	private final Optional<BlockDescription> description;

	/**
	 * Creates the message.
	 * 
	 * @param description the description of the block in the message, if any
	 * @param id the identifier of the message
	 */
	public GetBlockDescriptionResultMessageImpl(Optional<BlockDescription> description, String id) {
		super(id);

		this.description = Objects.requireNonNull(description, "description cannot be null");
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws NoSuchAlgorithmException if {@code json} refers to a non-available cryptographic algorithm
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public GetBlockDescriptionResultMessageImpl(GetBlockDescriptionResultMessageJson json) throws NoSuchAlgorithmException, InconsistentJsonException {
		super(json.getId());

		var description = json.getDescription();
		this.description = description.isEmpty() ? Optional.empty() : Optional.ofNullable(description.get().unmap());
	}

	@Override
	public Optional<BlockDescription> get() {
		return description;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetBlockDescriptionResultMessage gbdrm && super.equals(other) && Objects.equals(get(), gbdrm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetBlockDescriptionResultMessage.class.getName();
	}
}
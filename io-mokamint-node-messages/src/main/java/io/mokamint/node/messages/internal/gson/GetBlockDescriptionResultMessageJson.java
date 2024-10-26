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

import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.messages.GetBlockDescriptionResultMessages;
import io.mokamint.node.messages.api.GetBlockDescriptionResultMessage;

/**
 * The JSON representation of a {@link GetBlockDescriptionResultMessage}.
 */
public abstract class GetBlockDescriptionResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetBlockDescriptionResultMessage> {
	private final BlockDescriptions.Json description;

	protected GetBlockDescriptionResultMessageJson(GetBlockDescriptionResultMessage message) {
		super(message);

		this.description = message.get().map(BlockDescriptions.Json::new).orElse(null);
	}

	@Override
	public GetBlockDescriptionResultMessage unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		return GetBlockDescriptionResultMessages.of(Optional.ofNullable(description == null ? null : description.unmap()), getId());
	}

	@Override
	protected String getExpectedType() {
		return GetBlockDescriptionResultMessage.class.getName();
	}
}
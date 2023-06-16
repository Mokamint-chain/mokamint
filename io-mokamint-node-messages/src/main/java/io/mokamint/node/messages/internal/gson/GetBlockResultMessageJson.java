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
import io.mokamint.node.Blocks;
import io.mokamint.node.messages.GetBlockResultMessage;
import io.mokamint.node.messages.GetBlockResultMessages;

/**
 * The JSON representation of a {@link GetBlockResultMessage}.
 */
public abstract class GetBlockResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetBlockResultMessage> {
	private Blocks.Json block;

	protected GetBlockResultMessageJson(GetBlockResultMessage message) {
		super(message);

		if (message.get().isPresent())
			this.block = new Blocks.Encoder().map(message.get().get());
	}

	@Override
	public GetBlockResultMessage unmap() throws NoSuchAlgorithmException {
		return GetBlockResultMessages.of(Optional.ofNullable(block == null ? null : block.unmap()), getId());
	}


	@Override
	protected String getExpectedType() {
		return GetBlockResultMessage.class.getName();
	}
}
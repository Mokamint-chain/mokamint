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

import java.util.Optional;
import java.util.function.Supplier;

import io.mokamint.node.Blocks;
import io.mokamint.node.messages.GetBlockResultMessage;
import io.mokamint.node.messages.GetBlockResultMessages;

/**
 * The Json representation of a {@code GetBlockResultMessage}.
 */
public abstract class GetBlockResultMessageJson implements Supplier<GetBlockResultMessage> {
	private Blocks.Json block;

	protected GetBlockResultMessageJson(GetBlockResultMessage message) {
		if (message.get().isPresent())
			this.block = new Blocks.Encoder().map(message.get().get());
	}

	@Override
	public GetBlockResultMessage get() {
		if (block == null)
			return GetBlockResultMessages.of(Optional.empty());
		else
			return GetBlockResultMessages.of(Optional.of(block.get()));
	}
}
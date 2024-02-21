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

import java.util.Objects;
import java.util.Optional;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.api.GetBlockResultMessage;

/**
 * Implementation of the network message corresponding to the result of the {@link PublicNode#getBlock(byte[])} method.
 */
public class GetBlockResultMessageImpl extends AbstractRpcMessage implements GetBlockResultMessage {

	private final Optional<Block> block;

	/**
	 * Creates the message.
	 * 
	 * @param block the block in the message, if any
	 * @param id the identifier of the message
	 */
	public GetBlockResultMessageImpl(Optional<Block> block, String id) {
		super(id);

		this.block = Objects.requireNonNull(block, "block cannot be null");
		block.map(Objects::requireNonNull);
	}

	@Override
	public Optional<Block> get() {
		return block;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetBlockResultMessage gbrm && super.equals(other) && Objects.equals(get(), gbrm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetBlockResultMessage.class.getName();
	}
}
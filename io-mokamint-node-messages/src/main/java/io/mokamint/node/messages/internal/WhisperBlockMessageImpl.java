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

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.Block;
import io.mokamint.node.messages.api.WhisperBlockMessage;
import io.mokamint.node.messages.internal.gson.WhisperBlockMessageJson;

/**
 * Implementation of the network message sent to whisper a block between whisperers.
 */
public class WhisperBlockMessageImpl extends AbstractRpcMessage implements WhisperBlockMessage {

	/**
	 * The whispered block.
	 */
	private final Block block;

	/**
	 * Creates the message.
	 * 
	 * @param block the whispered block
	 * @param id the identifier of the message
	 */
	public WhisperBlockMessageImpl(Block block, String id) {
		super(id);

		this.block = Objects.requireNonNull(block, "block cannot be null");
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 * @throws NoSuchAlgorithmException if {@code json} refers to a non-available cryptographic algorithm
	 */
	public WhisperBlockMessageImpl(WhisperBlockMessageJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
		super(json.getId());

		var block = json.getBlock();
		if (block == null)
			throw new InconsistentJsonException("block cannot be null");

		this.block = block.unmap();
	}

	@Override
	public Block getWhispered() {
		return block;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof WhisperBlockMessage wbm && super.equals(other) && block.equals(wbm.getWhispered());
	}

	@Override
	protected String getExpectedType() {
		return WhisperBlockMessage.class.getName();
	}
}
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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import io.hotmoka.crypto.Base58ConversionException;
import io.hotmoka.crypto.Base64ConversionException;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.node.Blocks;
import io.mokamint.node.messages.WhisperBlockMessages;
import io.mokamint.node.messages.api.WhisperBlockMessage;

/**
 * The JSON representation of an {@link WhisperBlockMessage}.
 */
public abstract class WhisperBlockMessageJson extends AbstractRpcMessageJsonRepresentation<WhisperBlockMessage> {
	private final Blocks.Json block;

	protected WhisperBlockMessageJson(WhisperBlockMessage message) {
		super(message);

		this.block = new Blocks.Json(message.getWhispered());
	}

	@Override
	public WhisperBlockMessage unmap() throws NoSuchAlgorithmException, InvalidKeySpecException, HexConversionException, Base64ConversionException, InvalidKeyException, Base58ConversionException {
		return WhisperBlockMessages.of(block.unmap(), getId());
	}

	@Override
	protected String getExpectedType() {
		return WhisperBlockMessage.class.getName();
	}
}
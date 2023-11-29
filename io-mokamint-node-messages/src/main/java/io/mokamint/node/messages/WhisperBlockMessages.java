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

package io.mokamint.node.messages;

import io.mokamint.node.api.Block;
import io.mokamint.node.messages.api.WhisperBlockMessage;
import io.mokamint.node.messages.internal.WhisperBlockMessageImpl;
import io.mokamint.node.messages.internal.gson.WhisperBlockMessageDecoder;
import io.mokamint.node.messages.internal.gson.WhisperBlockMessageEncoder;
import io.mokamint.node.messages.internal.gson.WhisperBlockMessageJson;

/**
 * A provider of {@link WhisperBlockMessage}.
 */
public final class WhisperBlockMessages {

	private WhisperBlockMessages() {}

	/**
	 * Yields a {@link WhisperBlockMessage}.
	 * 
	 * @param block the whispered block
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static WhisperBlockMessage of(Block block, String id) {
		return new WhisperBlockMessageImpl(block, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends WhisperBlockMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends WhisperBlockMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends WhisperBlockMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(WhisperBlockMessage message) {
    		super(message);
    	}
    }
}
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

import java.security.InvalidKeyException;
import java.util.Optional;

import io.mokamint.node.api.Block;
import io.mokamint.node.messages.api.GetBlockResultMessage;
import io.mokamint.node.messages.internal.GetBlockResultMessageImpl;
import io.mokamint.node.messages.internal.gson.GetBlockResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetBlockResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.GetBlockResultMessageJson;

/**
 * A provider of {@link GetBlockResultMessage}.
 */
public final class GetBlockResultMessages {

	private GetBlockResultMessages() {}

	/**
	 * Yields a {@link GetBlockResultMessage}.
	 * 
	 * @param block the block in the result, if any
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetBlockResultMessage of(Optional<Block> block, String id) {
		return new GetBlockResultMessageImpl(block, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetBlockResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetBlockResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetBlockResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 * @throws InvalidKeyException if the block contains an invalid key
    	 */
    	public Json(GetBlockResultMessage message) throws InvalidKeyException {
    		super(message);
    	}
    }
}
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

import io.mokamint.node.messages.api.GetBlockDescriptionMessage;
import io.mokamint.node.messages.internal.GetBlockDescriptionMessageImpl;
import io.mokamint.node.messages.internal.gson.GetBlockDescriptionMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetBlockDescriptionMessageEncoder;
import io.mokamint.node.messages.internal.gson.GetBlockDescriptionMessageJson;

/**
 * A provider of {@link GetBlockDescriptionMessage}.
 */
public final class GetBlockDescriptionMessages {

	private GetBlockDescriptionMessages() {}

	/**
	 * Yields a {@link GetBlockDescriptionMessage}.
	 * 
	 * @param hash the {@code hash} parameter of the message
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetBlockDescriptionMessage of(byte[] hash, String id) {
		return new GetBlockDescriptionMessageImpl(hash, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetBlockDescriptionMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetBlockDescriptionMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetBlockDescriptionMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetBlockDescriptionMessage message) {
    		super(message);
    	}
    }
}
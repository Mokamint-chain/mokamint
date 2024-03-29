/*
Copyright 2024 Fausto Spoto

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

import io.mokamint.node.messages.api.GetTransactionMessage;
import io.mokamint.node.messages.internal.GetTransactionMessageImpl;
import io.mokamint.node.messages.internal.gson.GetTransactionMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetTransactionMessageEncoder;
import io.mokamint.node.messages.internal.gson.GetTransactionMessageJson;

/**
 * A provider of {@link GetTransactionMessage}.
 */
public final class GetTransactionMessages {

	private GetTransactionMessages() {}

	/**
	 * Yields a {@link GetTransactionMessage}.
	 * 
	 * @param hash the {@code hash} parameter of the message
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetTransactionMessage of(byte[] hash, String id) {
		return new GetTransactionMessageImpl(hash, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetTransactionMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetTransactionMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetTransactionMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetTransactionMessage message) {
    		super(message);
    	}
    }
}
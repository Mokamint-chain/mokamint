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

import io.mokamint.node.messages.api.GetTransactionAddressMessage;
import io.mokamint.node.messages.internal.GetTransactionAddressMessageImpl;
import io.mokamint.node.messages.internal.gson.GetTransactionAddressMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetTransactionAddressMessageEncoder;
import io.mokamint.node.messages.internal.gson.GetTransactionAddressMessageJson;

/**
 * A provider of {@link GetTransactionAddressMessage}.
 */
public final class GetTransactionAddressMessages {

	private GetTransactionAddressMessages() {}

	/**
	 * Yields a {@link GetTransactionAddressMessage}.
	 * 
	 * @param hash the {@code hash} parameter of the message
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetTransactionAddressMessage of(byte[] hash, String id) {
		return new GetTransactionAddressMessageImpl(hash, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetTransactionAddressMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetTransactionAddressMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetTransactionAddressMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetTransactionAddressMessage message) {
    		super(message);
    	}
    }
}
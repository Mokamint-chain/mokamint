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

import io.mokamint.node.api.TransactionInfo;
import io.mokamint.node.messages.api.AddTransactionResultMessage;
import io.mokamint.node.messages.internal.AddTransactionResultMessageImpl;
import io.mokamint.node.messages.internal.gson.AddTransactionResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.AddTransactionResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.AddTransactionResultMessageJson;

/**
 * A provider of {@link AddTransactionResultMessage}.
 */
public final class AddTransactionResultMessages {

	private AddTransactionResultMessages() {}

	/**
	 * Yields an {@link AddTransactionResultMessage}.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static AddTransactionResultMessage of(TransactionInfo result, String id) {
		return new AddTransactionResultMessageImpl(result, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends AddTransactionResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends AddTransactionResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends AddTransactionResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(AddTransactionResultMessage message) {
    		super(message);
    	}
    }
}
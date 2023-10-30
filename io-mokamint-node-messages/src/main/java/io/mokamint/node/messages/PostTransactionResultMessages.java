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

import io.mokamint.node.messages.api.PostTransactionResultMessage;
import io.mokamint.node.messages.internal.PostTransactionResultMessageImpl;
import io.mokamint.node.messages.internal.gson.PostTransactionResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.PostTransactionResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.PostTransactionResultMessageJson;

/**
 * A provider of {@link PostTransactionResultMessage}.
 */
public final class PostTransactionResultMessages {

	private PostTransactionResultMessages() {}

	/**
	 * Yields an {@link PostTransactionResultMessage}.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static PostTransactionResultMessage of(boolean result, String id) {
		return new PostTransactionResultMessageImpl(result, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends PostTransactionResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends PostTransactionResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends PostTransactionResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(PostTransactionResultMessage message) {
    		super(message);
    	}
    }
}
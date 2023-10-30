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

import io.mokamint.node.api.Transaction;
import io.mokamint.node.messages.api.PostTransactionMessage;
import io.mokamint.node.messages.internal.PostTransactionMessageImpl;
import io.mokamint.node.messages.internal.gson.PostTransactionMessageDecoder;
import io.mokamint.node.messages.internal.gson.PostTransactionMessageEncoder;
import io.mokamint.node.messages.internal.gson.PostTransactionMessageJson;

/**
 * A provider of {@link PostTransactionMessage}.
 */
public final class PostTransactionMessages {

	private PostTransactionMessages() {}

	/**
	 * Yields a {@link PostTransactionMessage}.
	 * 
	 * @param transaction the {@code transaction} parameter of the message
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static PostTransactionMessage of(Transaction transaction, String id) {
		return new PostTransactionMessageImpl(transaction, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends PostTransactionMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends PostTransactionMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends PostTransactionMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(PostTransactionMessage message) {
    		super(message);
    	}
    }
}
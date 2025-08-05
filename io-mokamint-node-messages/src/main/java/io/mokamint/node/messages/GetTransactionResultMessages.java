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

import java.util.Optional;

import io.hotmoka.websockets.beans.MappedDecoder;
import io.hotmoka.websockets.beans.MappedEncoder;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.messages.api.GetTransactionResultMessage;
import io.mokamint.node.messages.internal.GetTransactionResultMessageImpl;
import io.mokamint.node.messages.internal.json.GetTransactionResultMessageJson;

/**
 * A provider of {@link GetTransactionResultMessage}.
 */
public final class GetTransactionResultMessages {

	private GetTransactionResultMessages() {}

	/**
	 * Yields a {@link GetTransactionResultMessage}.
	 * 
	 * @param transaction the transaction in the result, if any
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetTransactionResultMessage of(Optional<Transaction> transaction, String id) {
		return new GetTransactionResultMessageImpl(transaction, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MappedEncoder<GetTransactionResultMessage, Json> {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {
			super(Json::new);
		}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends MappedDecoder<GetTransactionResultMessage, Json> {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {
			super(Json.class);
		}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetTransactionResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetTransactionResultMessage message) {
    		super(message);
    	}
    }
}
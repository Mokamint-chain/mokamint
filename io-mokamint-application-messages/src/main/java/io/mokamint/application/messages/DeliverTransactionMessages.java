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

package io.mokamint.application.messages;

import io.hotmoka.websockets.beans.MappedDecoder;
import io.hotmoka.websockets.beans.MappedEncoder;
import io.mokamint.application.messages.api.DeliverTransactionMessage;
import io.mokamint.application.messages.internal.DeliverTransactionMessageImpl;
import io.mokamint.application.messages.internal.json.DeliverTransactionMessageJson;
import io.mokamint.node.api.Transaction;

/**
 * A provider of {@link DeliverTransactionMessage}.
 */
public abstract class DeliverTransactionMessages {

	private DeliverTransactionMessages() {}

	/**
	 * Yields a {@link DeliverTransactionMessage}.
	 * 
	 * @param groupId the identifier of the group of transactions in the message
	 * @param transaction the transaction in the message
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static DeliverTransactionMessage of(int groupId, Transaction transaction, String id) {
		return new DeliverTransactionMessageImpl(groupId, transaction, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MappedEncoder<DeliverTransactionMessage, Json> {

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
	public static class Decoder extends MappedDecoder<DeliverTransactionMessage, Json> {

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
    public static class Json extends DeliverTransactionMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(DeliverTransactionMessage message) {
    		super(message);
    	}
    }
}
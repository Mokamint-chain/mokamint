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
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.messages.api.GetTransactionAddressResultMessage;
import io.mokamint.node.messages.internal.GetTransactionAddressResultMessageImpl;
import io.mokamint.node.messages.internal.json.GetTransactionAddressResultMessageJson;

/**
 * A provider of {@link GetTransactionAddressResultMessage}.
 */
public final class GetTransactionAddressResultMessages {

	private GetTransactionAddressResultMessages() {}

	/**
	 * Yields a {@link GetTransactionAddressResultMessage}.
	 * 
	 * @param address the address of the transaction in the result, if any
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetTransactionAddressResultMessage of(Optional<TransactionAddress> address, String id) {
		return new GetTransactionAddressResultMessageImpl(address, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MappedEncoder<GetTransactionAddressResultMessage, Json> {

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
	public static class Decoder extends MappedDecoder<GetTransactionAddressResultMessage, Json> {

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
    public static class Json extends GetTransactionAddressResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetTransactionAddressResultMessage message) {
    		super(message);
    	}
    }
}
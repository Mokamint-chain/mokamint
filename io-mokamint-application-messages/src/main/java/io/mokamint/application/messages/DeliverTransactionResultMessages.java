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

import io.mokamint.application.messages.api.DeliverTransactionResultMessage;
import io.mokamint.application.messages.internal.DeliverTransactionResultMessageImpl;
import io.mokamint.application.messages.internal.json.DeliverTransactionResultMessageDecoder;
import io.mokamint.application.messages.internal.json.DeliverTransactionResultMessageEncoder;
import io.mokamint.application.messages.internal.json.DeliverTransactionResultMessageJson;

/**
 * A provider of {@link DeliverTransactionResultMessage}.
 */
public abstract class DeliverTransactionResultMessages {

	private DeliverTransactionResultMessages() {}

	/**
	 * Yields a {@link DeliverTransactionResultMessage}.
	 * 
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static DeliverTransactionResultMessage of(String id) {
		return new DeliverTransactionResultMessageImpl(id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends DeliverTransactionResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends DeliverTransactionResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends DeliverTransactionResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(DeliverTransactionResultMessage message) {
    		super(message);
    	}
    }
}
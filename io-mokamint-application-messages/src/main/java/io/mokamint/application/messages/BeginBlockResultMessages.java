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

import io.mokamint.application.messages.api.BeginBlockResultMessage;
import io.mokamint.application.messages.internal.BeginBlockResultMessageImpl;
import io.mokamint.application.messages.internal.gson.BeginBlockResultMessageDecoder;
import io.mokamint.application.messages.internal.gson.BeginBlockResultMessageEncoder;
import io.mokamint.application.messages.internal.gson.BeginBlockResultMessageJson;

/**
 * A provider of {@link BeginBlockResultMessage}.
 */
public final class BeginBlockResultMessages {

	private BeginBlockResultMessages() {}

	/**
	 * Yields a {@link BeginBlockResultMessage}.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static BeginBlockResultMessage of(int result, String id) {
		return new BeginBlockResultMessageImpl(result, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends BeginBlockResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends BeginBlockResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends BeginBlockResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(BeginBlockResultMessage message) {
    		super(message);
    	}
    }
}
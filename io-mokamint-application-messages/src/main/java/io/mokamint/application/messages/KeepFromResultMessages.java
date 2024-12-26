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

import io.mokamint.application.messages.api.KeepFromResultMessage;
import io.mokamint.application.messages.internal.KeepFromResultMessageImpl;
import io.mokamint.application.messages.internal.gson.KeepFromResultMessageDecoder;
import io.mokamint.application.messages.internal.gson.KeepFromResultMessageEncoder;
import io.mokamint.application.messages.internal.gson.KeepFromResultMessageJson;

/**
 * A provider of {@link KeepFromResultMessage}.
 */
public abstract class KeepFromResultMessages {

	private KeepFromResultMessages() {}

	/**
	 * Yields a {@link KeepFromResultMessage}.
	 * 
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static KeepFromResultMessage of(String id) {
		return new KeepFromResultMessageImpl(id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends KeepFromResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends KeepFromResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends KeepFromResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(KeepFromResultMessage message) {
    		super(message);
    	}
    }
}
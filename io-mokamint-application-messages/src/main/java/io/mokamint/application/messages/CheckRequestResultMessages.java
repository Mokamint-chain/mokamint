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
import io.mokamint.application.messages.api.CheckRequestResultMessage;
import io.mokamint.application.messages.internal.CheckRequestResultMessageImpl;
import io.mokamint.application.messages.internal.json.CheckRequestResultMessageJson;

/**
 * A provider of {@link CheckRequestResultMessage}.
 */
public abstract class CheckRequestResultMessages {

	private CheckRequestResultMessages() {}

	/**
	 * Yields a {@link CheckRequestResultMessage}.
	 * 
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static CheckRequestResultMessage of(String id) {
		return new CheckRequestResultMessageImpl(id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MappedEncoder<CheckRequestResultMessage, Json> {

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
	public static class Decoder extends MappedDecoder<CheckRequestResultMessage, Json> {

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
    public static class Json extends CheckRequestResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(CheckRequestResultMessage message) {
    		super(message);
    	}
    }
}
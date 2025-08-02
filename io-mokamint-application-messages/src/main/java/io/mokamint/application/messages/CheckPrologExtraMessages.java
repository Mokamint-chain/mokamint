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

import io.mokamint.application.messages.api.CheckPrologExtraMessage;
import io.mokamint.application.messages.internal.CheckPrologExtraMessageImpl;
import io.mokamint.application.messages.internal.json.CheckPrologExtraMessageDecoder;
import io.mokamint.application.messages.internal.json.CheckPrologExtraMessageEncoder;
import io.mokamint.application.messages.internal.json.CheckPrologExtraMessageJson;

/**
 * A provider of {@link CheckPrologExtraMessage}.
 */
public abstract class CheckPrologExtraMessages {

	private CheckPrologExtraMessages() {}

	/**
	 * Yields a {@link CheckPrologExtraMessage}.
	 * 
	 * @param extra the extra bytes to check
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static CheckPrologExtraMessage of(byte[] extra, String id) {
		return new CheckPrologExtraMessageImpl(extra, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends CheckPrologExtraMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends CheckPrologExtraMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends CheckPrologExtraMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(CheckPrologExtraMessage message) {
    		super(message);
    	}
    }
}
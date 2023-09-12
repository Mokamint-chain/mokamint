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

import io.mokamint.node.messages.api.CloseMinerResultMessage;
import io.mokamint.node.messages.internal.CloseMinerResultMessageImpl;
import io.mokamint.node.messages.internal.gson.CloseMinerResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.CloseMinerResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.CloseMinerResultMessageJson;

/**
 * A provider of {@link CloseMinerResultMessage}.
 */
public final class CloseMinerResultMessages {

	private CloseMinerResultMessages() {}

	/**
	 * Yields an {@link CloseMinerResultMessage}.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static CloseMinerResultMessage of(boolean result, String id) {
		return new CloseMinerResultMessageImpl(result, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends CloseMinerResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends CloseMinerResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends CloseMinerResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(CloseMinerResultMessage message) {
    		super(message);
    	}
    }
}
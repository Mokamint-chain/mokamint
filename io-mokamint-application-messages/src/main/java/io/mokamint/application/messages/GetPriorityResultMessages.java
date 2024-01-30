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

import io.mokamint.application.messages.api.GetPriorityResultMessage;
import io.mokamint.application.messages.internal.GetPriorityResultMessageImpl;
import io.mokamint.application.messages.internal.gson.GetPriorityResultMessageDecoder;
import io.mokamint.application.messages.internal.gson.GetPriorityResultMessageEncoder;
import io.mokamint.application.messages.internal.gson.GetPriorityResultMessageJson;

/**
 * A provider of {@link GetPriorityResultMessage}.
 */
public final class GetPriorityResultMessages {

	private GetPriorityResultMessages() {}

	/**
	 * Yields a {@link GetPriorityResultMessage}.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetPriorityResultMessage of(long result, String id) {
		return new GetPriorityResultMessageImpl(result, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetPriorityResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetPriorityResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetPriorityResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetPriorityResultMessage message) {
    		super(message);
    	}
    }
}
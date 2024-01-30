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

import io.mokamint.application.messages.api.GetInitialStateIdMessage;
import io.mokamint.application.messages.internal.GetInitialStateIdMessageImpl;
import io.mokamint.application.messages.internal.gson.GetInitialStateIdMessageDecoder;
import io.mokamint.application.messages.internal.gson.GetInitialStateIdMessageEncoder;
import io.mokamint.application.messages.internal.gson.GetInitialStateIdMessageJson;

/**
 * A provider of {@link GetInitialStateIdMessage}.
 */
public final class GetInitialStateIdMessages {

	private GetInitialStateIdMessages() {}

	/**
	 * Yields a {@link GetInitialStateIdMessage}.
	 * 
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetInitialStateIdMessage of(String id) {
		return new GetInitialStateIdMessageImpl(id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetInitialStateIdMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetInitialStateIdMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetInitialStateIdMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetInitialStateIdMessage message) {
    		super(message);
    	}
    }
}
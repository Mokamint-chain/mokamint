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

import io.mokamint.application.messages.api.GetRepresentationResultMessage;
import io.mokamint.application.messages.internal.GetRepresentationResultMessageImpl;
import io.mokamint.application.messages.internal.json.GetRepresentationResultMessageDecoder;
import io.mokamint.application.messages.internal.json.GetRepresentationResultMessageEncoder;
import io.mokamint.application.messages.internal.json.GetRepresentationResultMessageJson;

/**
 * A provider of {@link GetRepresentationResultMessage}.
 */
public abstract class GetRepresentationResultMessages {

	private GetRepresentationResultMessages() {}

	/**
	 * Yields a {@link GetRepresentationResultMessage}.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetRepresentationResultMessage of(String result, String id) {
		return new GetRepresentationResultMessageImpl(result, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetRepresentationResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetRepresentationResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetRepresentationResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetRepresentationResultMessage message) {
    		super(message);
    	}
    }
}
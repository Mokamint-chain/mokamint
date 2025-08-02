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

import io.mokamint.application.messages.api.EndBlockMessage;
import io.mokamint.application.messages.internal.EndBlockMessageImpl;
import io.mokamint.application.messages.internal.json.EndBlockMessageDecoder;
import io.mokamint.application.messages.internal.json.EndBlockMessageEncoder;
import io.mokamint.application.messages.internal.json.EndBlockMessageJson;
import io.mokamint.nonce.api.Deadline;

/**
 * A provider of {@link EndBlockMessage}.
 */
public abstract class EndBlockMessages {

	private EndBlockMessages() {}

	/**
	 * Yields a {@link EndBlockMessage}.
	 * 
	 * @param groupId the identifier of the group of transactions in the message
	 * @param deadline the deadline in the message
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static EndBlockMessage of(int groupId, Deadline deadline, String id) {
		return new EndBlockMessageImpl(groupId, deadline, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends EndBlockMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends EndBlockMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends EndBlockMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(EndBlockMessage message) {
    		super(message);
    	}
    }
}
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

import java.time.LocalDateTime;

import io.mokamint.application.messages.api.BeginBlockMessage;
import io.mokamint.application.messages.internal.BeginBlockMessageImpl;
import io.mokamint.application.messages.internal.gson.BeginBlockMessageDecoder;
import io.mokamint.application.messages.internal.gson.BeginBlockMessageEncoder;
import io.mokamint.application.messages.internal.gson.BeginBlockMessageJson;

/**
 * A provider of {@link BeginBlockMessage}.
 */
public final class BeginBlockMessages {

	private BeginBlockMessages() {}

	/**
	 * Yields a {@link BeginBlockMessage}.
	 * 
	 * @param height the block height in the message
	 * @param when the block creation time in the message
	 * @param stateId the state identifier in the message
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static BeginBlockMessage of(long height, LocalDateTime when, byte[] stateId, String id) {
		return new BeginBlockMessageImpl(height, when, stateId, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends BeginBlockMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends BeginBlockMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends BeginBlockMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(BeginBlockMessage message) {
    		super(message);
    	}
    }
}
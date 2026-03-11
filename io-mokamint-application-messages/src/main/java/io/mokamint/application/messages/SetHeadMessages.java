/*
Copyright 2026 Fausto Spoto

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
import io.mokamint.application.messages.api.SetHeadMessage;
import io.mokamint.application.messages.internal.SetHeadMessageImpl;
import io.mokamint.application.messages.internal.json.SetHeadMessageJson;

/**
 * A provider of {@link SetHeadMessage}.
 */
public abstract class SetHeadMessages {

	private SetHeadMessages() {}

	/**
	 * Yields a {@link SetHeadMessage}.
	 * 
	 * @param stateId the state identifier in the message
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static SetHeadMessage of(byte[] stateId, String id) {
		return new SetHeadMessageImpl(stateId, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MappedEncoder<SetHeadMessage, Json> {

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
	public static class Decoder extends MappedDecoder<SetHeadMessage, Json> {

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
    public static class Json extends SetHeadMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(SetHeadMessage message) {
    		super(message);
    	}
    }
}
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

import java.util.UUID;

import io.hotmoka.websockets.beans.MappedDecoder;
import io.hotmoka.websockets.beans.MappedEncoder;
import io.mokamint.node.messages.api.RemoveMinerMessage;
import io.mokamint.node.messages.internal.RemoveMinerMessageImpl;
import io.mokamint.node.messages.internal.json.RemoveMinerMessageJson;

/**
 * A provider of {@link RemoveMinerMessage}.
 */
public final class RemoveMinerMessages {

	private RemoveMinerMessages() {}

	/**
	 * Yields an {@link RemoveMinerMessage}.
	 * 
	 * @param uuid the UUID of the miner that must be closed
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static RemoveMinerMessage of(UUID uuid, String id) {
		return new RemoveMinerMessageImpl(uuid, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MappedEncoder<RemoveMinerMessage, Json> {

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
	public static class Decoder extends MappedDecoder<RemoveMinerMessage, Json> {

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
    public static class Json extends RemoveMinerMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(RemoveMinerMessage message) {
    		super(message);
    	}
    }
}
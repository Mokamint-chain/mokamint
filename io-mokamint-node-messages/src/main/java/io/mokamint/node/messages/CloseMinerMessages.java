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

import io.mokamint.node.messages.api.CloseMinerMessage;
import io.mokamint.node.messages.internal.CloseMinerMessageImpl;
import io.mokamint.node.messages.internal.gson.CloseMinerMessageDecoder;
import io.mokamint.node.messages.internal.gson.CloseMinerMessageEncoder;
import io.mokamint.node.messages.internal.gson.CloseMinerMessageJson;

/**
 * A provider of {@link CloseMinerMessage}.
 */
public final class CloseMinerMessages {

	private CloseMinerMessages() {}

	/**
	 * Yields an {@link CloseMinerMessage}.
	 * 
	 * @param uuid the UUID of the miner that must be closed
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static CloseMinerMessage of(UUID uuid, String id) {
		return new CloseMinerMessageImpl(uuid, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends CloseMinerMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends CloseMinerMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends CloseMinerMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(CloseMinerMessage message) {
    		super(message);
    	}
    }
}
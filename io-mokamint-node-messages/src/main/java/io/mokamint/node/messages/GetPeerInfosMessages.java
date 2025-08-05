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

import io.hotmoka.websockets.beans.MappedDecoder;
import io.hotmoka.websockets.beans.MappedEncoder;
import io.mokamint.node.messages.api.GetPeerInfosMessage;
import io.mokamint.node.messages.internal.GetPeerInfosMessageImpl;
import io.mokamint.node.messages.internal.json.GetPeerInfosMessageJson;

/**
 * A provider of {@link GetPeerInfosMessage}.
 */
public final class GetPeerInfosMessages {

	private GetPeerInfosMessages() {}

	/**
	 * Yields a {@link GetPeerInfosMessage}.
	 * 
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetPeerInfosMessage of(String id) {
		return new GetPeerInfosMessageImpl(id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MappedEncoder<GetPeerInfosMessage, Json> {

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
	public static class Decoder extends MappedDecoder<GetPeerInfosMessage, Json> {

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
    public static class Json extends GetPeerInfosMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetPeerInfosMessage message) {
    		super(message);
    	}
    }
}
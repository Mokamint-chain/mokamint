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

import java.util.stream.Stream;

import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.messages.api.GetPeerInfosResultMessage;
import io.mokamint.node.messages.internal.GetPeerInfosResultMessageImpl;
import io.mokamint.node.messages.internal.gson.GetPeerInfosResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetPeerInfosResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.GetPeerInfosResultMessageJson;

/**
 * A provider of {@link GetPeerInfosResultMessage}.
 */
public final class GetPeerInfosResultMessages {

	private GetPeerInfosResultMessages() {}

	/**
	 * Yields a {@link GetPeerInfosResultMessage}.
	 * 
	 * @param peers the peers in the result
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetPeerInfosResultMessage of(Stream<PeerInfo> peers, String id) {
		return new GetPeerInfosResultMessageImpl(peers, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetPeerInfosResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetPeerInfosResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetPeerInfosResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetPeerInfosResultMessage message) {
    		super(message);
    	}
    }
}
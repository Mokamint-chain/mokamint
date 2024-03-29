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

import java.net.URI;

import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.api.WhisperPeerMessage;
import io.mokamint.node.messages.internal.WhisperPeerMessageImpl;
import io.mokamint.node.messages.internal.gson.WhisperPeerMessageDecoder;
import io.mokamint.node.messages.internal.gson.WhisperPeerMessageEncoder;
import io.mokamint.node.messages.internal.gson.WhisperPeerMessageJson;

/**
 * A provider of {@link WhisperPeerMessage}.
 */
public final class WhisperPeerMessages {

	private WhisperPeerMessages() {}

	/**
	 * Yields a {@link WhisperPeerMessage}.
	 * 
	 * @param peer the whispered peer
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static WhisperPeerMessage of(Peer peer, String id) {
		return new WhisperPeerMessageImpl(peer, id);
	}

	/**
	 * Yields a {@link WhisperPeerMessage}.
	 * 
	 * @param uri the URI of the whispered peer
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static WhisperPeerMessage of(URI uri, String id) {
		return new WhisperPeerMessageImpl(Peers.of(uri), id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends WhisperPeerMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends WhisperPeerMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends WhisperPeerMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(WhisperPeerMessage message) {
    		super(message);
    	}
    }
}
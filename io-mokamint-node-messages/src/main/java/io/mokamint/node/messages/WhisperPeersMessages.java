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

import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.api.WhisperPeersMessage;
import io.mokamint.node.messages.internal.WhisperPeersMessageImpl;
import io.mokamint.node.messages.internal.gson.WhisperPeersMessageDecoder;
import io.mokamint.node.messages.internal.gson.WhisperPeersMessageEncoder;
import io.mokamint.node.messages.internal.gson.WhisperPeersMessageJson;

/**
 * A provider of {@link WhisperPeersMessage}.
 */
public final class WhisperPeersMessages {

	private WhisperPeersMessages() {}

	/**
	 * Yields a {@link WhisperPeersMessage}.
	 * 
	 * @param peers the whispered peers
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static WhisperPeersMessage of(Stream<Peer> peers, String id) {
		return new WhisperPeersMessageImpl(peers, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends WhisperPeersMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends WhisperPeersMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends WhisperPeersMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(WhisperPeersMessage message) {
    		super(message);
    	}
    }
}
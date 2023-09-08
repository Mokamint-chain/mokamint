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

import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.api.RemovePeerMessage;
import io.mokamint.node.messages.internal.RemovePeerMessageImpl;
import io.mokamint.node.messages.internal.gson.RemovePeerMessageDecoder;
import io.mokamint.node.messages.internal.gson.RemovePeerMessageEncoder;
import io.mokamint.node.messages.internal.gson.RemovePeerMessageJson;

/**
 * A provider of {@link RemovePeerMessage}.
 */
public final class RemovePeerMessages {

	private RemovePeerMessages() {}

	/**
	 * Yields a {@link RemovePeerMessage}.
	 * 
	 * @param peer the peer to remove
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static RemovePeerMessage of(Peer peer, String id) {
		return new RemovePeerMessageImpl(peer, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends RemovePeerMessageEncoder {}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends RemovePeerMessageDecoder {}

	/**
     * Json representation.
     */
    public static class Json extends RemovePeerMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(RemovePeerMessage message) {
    		super(message);
    	}
    }
}
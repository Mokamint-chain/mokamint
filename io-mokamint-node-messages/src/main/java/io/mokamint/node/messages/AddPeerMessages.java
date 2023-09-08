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
import io.mokamint.node.messages.api.AddPeerMessage;
import io.mokamint.node.messages.internal.AddPeerMessageImpl;
import io.mokamint.node.messages.internal.gson.AddPeerMessageDecoder;
import io.mokamint.node.messages.internal.gson.AddPeerMessageEncoder;
import io.mokamint.node.messages.internal.gson.AddPeerMessageJson;

/**
 * A provider of {@link AddPeerMessage}.
 */
public final class AddPeerMessages {

	private AddPeerMessages() {}

	/**
	 * Yields an {@link AddPeerMessage}.
	 * 
	 * @param peer the peer to add
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static AddPeerMessage of(Peer peer, String id) {
		return new AddPeerMessageImpl(peer, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends AddPeerMessageEncoder {}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends AddPeerMessageDecoder {}

	/**
     * Json representation.
     */
    public static class Json extends AddPeerMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(AddPeerMessage message) {
    		super(message);
    	}
    }
}
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
import io.mokamint.node.messages.internal.AddPeersMessageImpl;
import io.mokamint.node.messages.internal.gson.AddPeersMessageDecoder;
import io.mokamint.node.messages.internal.gson.AddPeersMessageEncoder;
import io.mokamint.node.messages.internal.gson.AddPeersMessageJson;

/**
 * A provider of {@link AddPeersMessage}.
 */
public class AddPeersMessages {

	private AddPeersMessages() {}

	/**
	 * Yields an {@link AddPeersMessage}.
	 * 
	 * @param peers the peers to add
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static AddPeersMessage of(Stream<Peer> peers, String id) {
		return new AddPeersMessageImpl(peers, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends AddPeersMessageEncoder {}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends AddPeersMessageDecoder {}

	/**
     * Json representation.
     */
    public static class Json extends AddPeersMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(AddPeersMessage message) {
    		super(message);
    	}
    }
}
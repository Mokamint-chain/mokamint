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
import io.mokamint.node.messages.internal.GetPeersResultMessageImpl;
import io.mokamint.node.messages.internal.gson.GetPeersResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetPeersResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.GetPeersResultMessageJson;

/**
 * A provider of {@link GetPeersResultMessage}.
 */
public class GetPeersResultMessages {

	private GetPeersResultMessages() {}

	/**
	 * Yields a {@link GetPeersResultMessage}.
	 * 
	 * @param peers the peers in the result
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetPeersResultMessage of(Stream<PeerInfo> peers, String id) {
		return new GetPeersResultMessageImpl(peers, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetPeersResultMessageEncoder {}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetPeersResultMessageDecoder {}

	/**
     * Json representation.
     */
    public static class Json extends GetPeersResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetPeersResultMessage message) {
    		super(message);
    	}
    }
}
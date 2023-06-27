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

import io.mokamint.node.messages.internal.SuggestPeersResultMessageImpl;
import io.mokamint.node.messages.internal.gson.SuggestPeersResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.SuggestPeersResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.SuggestPeersResultMessageJson;

/**
 * A provider of {@link SuggestPeersResultMessage}.
 */
public class SuggestPeersResultMessages {

	private SuggestPeersResultMessages() {}

	/**
	 * Yields an {@link SuggestPeersResultMessage}.
	 * 
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static SuggestPeersResultMessage of(String id) {
		return new SuggestPeersResultMessageImpl(id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends SuggestPeersResultMessageEncoder {}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends SuggestPeersResultMessageDecoder {}

	/**
     * Json representation.
     */
    public static class Json extends SuggestPeersResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(SuggestPeersResultMessage message) {
    		super(message);
    	}
    }
}
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

import io.mokamint.node.messages.internal.AddPeerResultMessageImpl;
import io.mokamint.node.messages.internal.gson.AddPeerResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.AddPeerResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.AddPeerResultMessageJson;

/**
 * A provider of {@link AddPeerResultMessage}.
 */
public class AddPeerResultMessages {

	private AddPeerResultMessages() {}

	/**
	 * Yields an {@link AddPeerResultMessage}.
	 * 
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static AddPeerResultMessage of(String id) {
		return new AddPeerResultMessageImpl(id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends AddPeerResultMessageEncoder {}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends AddPeerResultMessageDecoder {}

	/**
     * Json representation.
     */
    public static class Json extends AddPeerResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(AddPeerResultMessage message) {
    		super(message);
    	}
    }
}
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

import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.messages.api.GetInfoResultMessage;
import io.mokamint.node.messages.internal.GetInfoResultMessageImpl;
import io.mokamint.node.messages.internal.gson.GetInfoResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetInfoResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.GetInfoResultMessageJson;

/**
 * A provider of {@link GetInfoResultMessage}.
 */
public final class GetInfoResultMessages {

	private GetInfoResultMessages() {}

	/**
	 * Yields a {@link GetInfoResultMessage}.
	 * 
	 * @param info the node information in the message
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetInfoResultMessage of(NodeInfo info, String id) {
		return new GetInfoResultMessageImpl(info, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetInfoResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetInfoResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetInfoResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetInfoResultMessage message) {
    		super(message);
    	}
    }
}
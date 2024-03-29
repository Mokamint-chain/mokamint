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

import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.messages.api.GetChainPortionResultMessage;
import io.mokamint.node.messages.internal.GetChainPortionResultMessageImpl;
import io.mokamint.node.messages.internal.gson.GetChainPortionResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetChainPortionResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.GetChainPortionResultMessageJson;

/**
 * A provider of {@link GetChainPortionResultMessage}.
 */
public final class GetChainPortionResultMessages {

	private GetChainPortionResultMessages() {}

	/**
	 * Yields a {@link GetChainPortionResultMessage}.
	 * 
	 * @param chain the hashes in the chain
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetChainPortionResultMessage of(ChainPortion chain, String id) {
		return new GetChainPortionResultMessageImpl(chain, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetChainPortionResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetChainPortionResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetChainPortionResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetChainPortionResultMessage message) {
    		super(message);
    	}
    }
}
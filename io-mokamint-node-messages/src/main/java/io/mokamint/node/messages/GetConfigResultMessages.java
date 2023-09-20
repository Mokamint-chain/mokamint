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

import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.messages.api.GetConfigResultMessage;
import io.mokamint.node.messages.internal.GetConfigResultMessageImpl;
import io.mokamint.node.messages.internal.gson.GetConfigResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetConfigResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.GetConfigResultMessageJson;

/**
 * A provider of {@link GetConfigResultMessage}.
 */
public final class GetConfigResultMessages {

	private GetConfigResultMessages() {}

	/**
	 * Yields a {@link GetConfigResultMessage}.
	 * 
	 * @param config the configuration in the result
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetConfigResultMessage of(ConsensusConfig<?,?> config, String id) {
		return new GetConfigResultMessageImpl(config, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetConfigResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetConfigResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetConfigResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetConfigResultMessage message) {
    		super(message);
    	}
    }
}
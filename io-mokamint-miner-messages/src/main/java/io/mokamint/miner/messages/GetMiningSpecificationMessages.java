/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.miner.messages;

import io.hotmoka.websockets.beans.MappedDecoder;
import io.hotmoka.websockets.beans.MappedEncoder;
import io.mokamint.miner.messages.api.GetMiningSpecificationMessage;
import io.mokamint.miner.messages.internal.GetMiningSpecificationMessageImpl;
import io.mokamint.miner.messages.internal.json.GetMiningSpecificationMessageJson;

/**
 * A provider of {@link GetMiningSpecificationMessage}.
 */
public final class GetMiningSpecificationMessages {

	private GetMiningSpecificationMessages() {}

	/**
	 * Yields a {@link GetMiningSpecificationMessage}.
	 * 
	 * @param chainId the chain identifier of the deadlines expected by the miner having the specification
	 * @return the message
	 */
	public static GetMiningSpecificationMessage of(String chainId) {
		return new GetMiningSpecificationMessageImpl(chainId);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MappedEncoder<GetMiningSpecificationMessage, Json> {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {
			super(Json::new);
		}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends MappedDecoder<GetMiningSpecificationMessage, Json> {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {
			super(Json.class);
		}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetMiningSpecificationMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetMiningSpecificationMessage message) {
    		super(message);
    	}
    }
}
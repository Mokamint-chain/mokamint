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

import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.messages.api.GetMempoolInfoResultMessage;
import io.mokamint.node.messages.internal.GetMempoolInfoResultMessageImpl;
import io.mokamint.node.messages.internal.gson.GetMempoolInfoResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetMempoolInfoResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.GetMempoolInfoResultMessageJson;

/**
 * A provider of {@link GetMempoolInfoResultMessage}.
 */
public final class GetMempoolInfoResultMessages {

	private GetMempoolInfoResultMessages() {}

	/**
	 * Yields a {@link GetMempoolInfoResultMessage}.
	 * 
	 * @param info the mempool information in the message
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetMempoolInfoResultMessage of(MempoolInfo info, String id) {
		return new GetMempoolInfoResultMessageImpl(info, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetMempoolInfoResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetMempoolInfoResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetMempoolInfoResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetMempoolInfoResultMessage message) {
    		super(message);
    	}
    }
}
/*
Copyright 2024 Fausto Spoto

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

package io.mokamint.application.messages;

import io.hotmoka.websockets.beans.MappedDecoder;
import io.hotmoka.websockets.beans.MappedEncoder;
import io.mokamint.application.messages.api.ExecuteTransactionMessage;
import io.mokamint.application.messages.internal.ExecuteExecuteMessageImpl;
import io.mokamint.application.messages.internal.json.ExecuteTransactionMessageJson;
import io.mokamint.node.api.Request;

/**
 * A provider of {@link ExecuteTransactionMessage}.
 */
public abstract class ExecuteTransactionMessages {

	private ExecuteTransactionMessages() {}

	/**
	 * Yields a {@link ExecuteTransactionMessage}.
	 * 
	 * @param groupId the identifier of the group of transactions in the message
	 * @param request the request in the message
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static ExecuteTransactionMessage of(int groupId, Request request, String id) {
		return new ExecuteExecuteMessageImpl(groupId, request, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MappedEncoder<ExecuteTransactionMessage, Json> {

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
	public static class Decoder extends MappedDecoder<ExecuteTransactionMessage, Json> {

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
    public static class Json extends ExecuteTransactionMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(ExecuteTransactionMessage message) {
    		super(message);
    	}
    }
}
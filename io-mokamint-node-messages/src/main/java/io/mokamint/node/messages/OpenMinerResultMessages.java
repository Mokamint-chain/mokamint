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

import io.mokamint.node.messages.api.OpenMinerResultMessage;
import io.mokamint.node.messages.internal.OpenMinerResultMessageImpl;
import io.mokamint.node.messages.internal.gson.OpenMinerResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.OpenMinerResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.OpenMinerResultMessageJson;

/**
 * A provider of {@link OpenMinerResultMessage}.
 */
public final class OpenMinerResultMessages {

	private OpenMinerResultMessages() {}

	/**
	 * Yields an {@link OpenMinerResultMessage}.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static OpenMinerResultMessage of(boolean result, String id) {
		return new OpenMinerResultMessageImpl(result, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends OpenMinerResultMessageEncoder {}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends OpenMinerResultMessageDecoder {}

	/**
     * Json representation.
     */
    public static class Json extends OpenMinerResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(OpenMinerResultMessage message) {
    		super(message);
    	}
    }
}
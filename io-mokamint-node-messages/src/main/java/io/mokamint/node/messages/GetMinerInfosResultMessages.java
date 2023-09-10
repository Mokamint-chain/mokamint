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

import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.messages.api.GetMinerInfosResultMessage;
import io.mokamint.node.messages.internal.GetMinerInfosResultMessageImpl;
import io.mokamint.node.messages.internal.gson.GetMinerInfosResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetMinerInfosResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.GetMinerInfosResultMessageJson;

/**
 * A provider of {@link GetMinerInfosResultMessage}.
 */
public final class GetMinerInfosResultMessages {

	private GetMinerInfosResultMessages() {}

	/**
	 * Yields a {@link GetMinerInfosResultMessage}.
	 * 
	 * @param miners the miners in the result
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetMinerInfosResultMessage of(Stream<MinerInfo> miners, String id) {
		return new GetMinerInfosResultMessageImpl(miners, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetMinerInfosResultMessageEncoder {}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetMinerInfosResultMessageDecoder {}

	/**
     * Json representation.
     */
    public static class Json extends GetMinerInfosResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetMinerInfosResultMessage message) {
    		super(message);
    	}
    }
}
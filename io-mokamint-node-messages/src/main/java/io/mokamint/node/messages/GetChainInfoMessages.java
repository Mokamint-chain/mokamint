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

import io.mokamint.node.messages.api.GetChainInfoMessage;
import io.mokamint.node.messages.internal.GetChainInfoMessageImpl;
import io.mokamint.node.messages.internal.gson.GetChainInfoMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetChainInfoMessageEncoder;

/**
 * A provider of {@link GetChainInfoMessage}.
 */
public final class GetChainInfoMessages {

	private GetChainInfoMessages() {}

	/**
	 * Yields a {@link GetChainInfoMessage}.
	 * 
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetChainInfoMessage of(String id) {
		return new GetChainInfoMessageImpl(id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetChainInfoMessageEncoder {}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetChainInfoMessageDecoder {}
}
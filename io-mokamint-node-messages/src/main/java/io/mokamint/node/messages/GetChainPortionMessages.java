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

import io.mokamint.node.messages.api.GetChainPortionMessage;
import io.mokamint.node.messages.internal.GetChainPortionMessageImpl;
import io.mokamint.node.messages.internal.gson.GetChainPortionMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetChainPortionMessageEncoder;

/**
 * A provider of {@link GetChainPortionMessage}.
 */
public final class GetChainPortionMessages {

	private GetChainPortionMessages() {}

	/**
	 * Yields a {@link GetChainPortionMessage}.
	 * 
	 * @param start the {@code start} parameter of the message
	 * @param count the {@code count} parameter of the message
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetChainPortionMessage of(long start, long count, String id) {
		return new GetChainPortionMessageImpl(start, count, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetChainPortionMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetChainPortionMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}
}
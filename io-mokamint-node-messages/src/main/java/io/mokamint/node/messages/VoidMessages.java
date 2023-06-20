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

import io.mokamint.node.messages.internal.VoidMessageImpl;
import io.mokamint.node.messages.internal.gson.VoidResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.VoidResultMessageEncoder;

/**
 * A provider of {@link VoidMessage}.
 */
public class VoidMessages {

	private VoidMessages() {}

	/**
	 * Yields a {@link VoidMessage}.
	 * 
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static VoidMessage of(String id) {
		return new VoidMessageImpl(id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends VoidResultMessageEncoder {}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends VoidResultMessageDecoder {}
}
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

package io.mokamint.node;

import java.util.stream.Stream;

import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.internal.ChainPortionImpl;
import io.mokamint.node.internal.gson.ChainPortionDecoder;
import io.mokamint.node.internal.gson.ChainPortionEncoder;
import io.mokamint.node.internal.gson.ChainPortionJson;

/**
 * Providers of objects containing the hashes of a sequential portion
 * of the current best chain.
 */
public abstract class ChainPortions {

	private ChainPortions() {}

	/**
	 * Yields the hashes of a sequential
	 * portion of the current best chain of a Mokamint node.
	 * 
	 * @param hashes the hashes
	 * @return the object containing the sequential hashes
	 */
	public static ChainPortion of(Stream<byte[]> hashes) {
		return new ChainPortionImpl(hashes);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends ChainPortionEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends ChainPortionDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
    public static class Json extends ChainPortionJson {

    	/**
    	 * Creates the Json representation for the given portion of chain.
    	 * 
    	 * @param chain the portion of chain
    	 */
    	public Json(ChainPortion chain) {
    		super(chain);
    	}
    }
}
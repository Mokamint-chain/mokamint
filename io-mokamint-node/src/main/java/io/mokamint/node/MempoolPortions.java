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

import io.mokamint.node.api.MempoolPortion;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.internal.MempoolPortionImpl;
import io.mokamint.node.internal.gson.MempoolPortionDecoder;
import io.mokamint.node.internal.gson.MempoolPortionEncoder;
import io.mokamint.node.internal.gson.MempoolPortionJson;

/**
 * Providers of objects containing the transaction information objects of a sorted,
 * sequential portion of the mempool of a Mokamint node.
 */
public abstract class MempoolPortions {

	private MempoolPortions() {}

	/**
	 * Yields the container of the information transaction objects of a sequential
	 * portion of the mempool of a Mokamint node.
	 * 
	 * @param transactions the transaction information objects, in increasing order of priority
	 * @return the object containing the sequential information transaction objects
	 */
	public static MempoolPortion of(Stream<MempoolEntry> transactions) {
		return new MempoolPortionImpl(transactions);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MempoolPortionEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends MempoolPortionDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
    public static class Json extends MempoolPortionJson {

    	/**
    	 * Creates the Json representation for the given portion of mempool.
    	 * 
    	 * @param mempool the portion of mempool
    	 */
    	public Json(MempoolPortion mempool) {
    		super(mempool);
    	}
    }
}
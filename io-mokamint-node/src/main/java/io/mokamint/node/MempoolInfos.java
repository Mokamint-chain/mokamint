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

import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.internal.MempoolInfoImpl;
import io.mokamint.node.internal.gson.MempoolInfoDecoder;
import io.mokamint.node.internal.gson.MempoolInfoEncoder;
import io.mokamint.node.internal.gson.MempoolInfoJson;

/**
 * Providers of mempool information objects.
 */
public abstract class MempoolInfos {

	private MempoolInfos() {}

	/**
	 * Yields a new mempool information object.
	 * 
	 * @param size the size (number of entries) of the mempool
	 * @return the mempool information object
	 */
	public static MempoolInfo of(long size) {
		return new MempoolInfoImpl(size);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MempoolInfoEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends MempoolInfoDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
    public static class Json extends MempoolInfoJson {

    	/**
    	 * Creates the Json representation for the given mempool information object.
    	 * 
    	 * @param info the mempool information object
    	 */
    	public Json(MempoolInfo info) {
    		super(info);
    	}
    }
}
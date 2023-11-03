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

import io.mokamint.node.api.TransactionInfo;
import io.mokamint.node.internal.TransactionInfoImpl;
import io.mokamint.node.internal.gson.TransactionInfoDecoder;
import io.mokamint.node.internal.gson.TransactionInfoEncoder;
import io.mokamint.node.internal.gson.TransactionInfoJson;

/**
 * Providers of transaction information objects.
 */
public abstract class TransactionInfos {

	private TransactionInfos() {}

	/**
	 * Yields a new transaction information object.
	 * 
	 * @param hash the hash of the transaction
	 * @param priority the priority of the transaction
	 * @return the transaction information object
	 */
	public static TransactionInfo of(byte[] hash, long priority) {
		return new TransactionInfoImpl(hash, priority);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends TransactionInfoEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends TransactionInfoDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
    public static class Json extends TransactionInfoJson {

    	/**
    	 * Creates the Json representation for the given transaction information object.
    	 * 
    	 * @param info the transaction information object
    	 */
    	public Json(TransactionInfo info) {
    		super(info);
    	}
    }
}
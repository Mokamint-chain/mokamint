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

import io.mokamint.node.api.Transaction;
import io.mokamint.node.internal.TransactionImpl;
import io.mokamint.node.internal.gson.TransactionDecoder;
import io.mokamint.node.internal.gson.TransactionEncoder;
import io.mokamint.node.internal.gson.TransactionJson;

/**
 * Providers of transaction objects.
 */
public abstract class Transactions {

	private Transactions() {}

	/**
	 * Yields a new transaction object.
	 * 
	 * @param bytes the bytes of the transaction
	 * @return the transaction object
	 */
	public static Transaction of(byte[] bytes) {
		return new TransactionImpl(bytes);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends TransactionEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends TransactionDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
    public static class Json extends TransactionJson {

    	/**
    	 * Creates the Json representation for the given transaction.
    	 * 
    	 * @param transaction the transaction
    	 */
    	public Json(Transaction transaction) {
    		super(transaction);
    	}
    }
}
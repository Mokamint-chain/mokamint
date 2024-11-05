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

import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.internal.TransactionAddressImpl;
import io.mokamint.node.internal.gson.TransactionAddressDecoder;
import io.mokamint.node.internal.gson.TransactionAddressEncoder;
import io.mokamint.node.internal.gson.TransactionAddressJson;

/**
 * Providers of transaction address objects.
 */
public abstract class TransactionAddresses {

	private TransactionAddresses() {}

	/**
	 * Yields a new transaction address.
	 * 
	 * @param blockHash the hash of the block containing the transaction
	 * @param progressive the progressive number of the transaction inside the table of the
	 *                    transactions inside the block
	 * @return the transaction address
	 */
	public static TransactionAddress of(byte[] blockHash, int progressive) {
		return new TransactionAddressImpl(blockHash, progressive);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends TransactionAddressEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends TransactionAddressDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
    public static class Json extends TransactionAddressJson {

    	/**
    	 * Creates the Json representation for the given transaction address.
    	 * 
    	 * @param address the transaction address
    	 */
    	public Json(TransactionAddress address) {
    		super(address);
    	}
    }
}
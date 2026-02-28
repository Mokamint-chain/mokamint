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

import io.mokamint.node.api.RequestAddress;
import io.mokamint.node.internal.RequestAddressImpl;
import io.mokamint.node.internal.json.RequestAddressDecoder;
import io.mokamint.node.internal.json.RequestAddressEncoder;
import io.mokamint.node.internal.json.RequestAddressJson;

/**
 * Providers of request address objects.
 */
public abstract class RequestAddresses {

	private RequestAddresses() {}

	/**
	 * Yields a new request address.
	 * 
	 * @param blockHash the hash of the block containing the request
	 * @param progressive the progressive number of the request inside the table of the
	 *                    requests inside the block
	 * @return the request address
	 */
	public static RequestAddress of(byte[] blockHash, int progressive) {
		return new RequestAddressImpl(blockHash, progressive);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends RequestAddressEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends RequestAddressDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
    public static class Json extends RequestAddressJson {

    	/**
    	 * Creates the Json representation for the given request address.
    	 * 
    	 * @param address the request address
    	 */
    	public Json(RequestAddress address) {
    		super(address);
    	}
    }
}
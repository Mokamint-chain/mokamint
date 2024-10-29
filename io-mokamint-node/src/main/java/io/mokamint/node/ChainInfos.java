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

import java.util.Optional;
import java.util.function.Function;

import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.internal.ChainInfoImpl;
import io.mokamint.node.internal.gson.ChainInfoDecoder;
import io.mokamint.node.internal.gson.ChainInfoEncoder;
import io.mokamint.node.internal.gson.ChainInfoJson;

/**
 * Providers of chain information objects.
 */
public abstract class ChainInfos {

	private ChainInfos() {}

	/**
	 * Yields a new chain information object.
	 * 
	 * @param height the height of the chain
	 * @param genesisHash the hash of the genesis block, if any
	 * @param headHash the hash of the head block, if any
	 * @param headStateId the state identifier at the end of the execution of the head block, if any
	 * @return the chain information object
	 */
	public static ChainInfo of(long height, Optional<byte[]> genesisHash, Optional<byte[]> headHash, Optional<byte[]> headStateId) {
		return new ChainInfoImpl(height, genesisHash, headHash, headStateId);
	}

	/**
	 * Yields a new chain information object.
	 * 
	 * @param height the height of the chain
	 * @param genesisHash the hash of the genesis block, if any
	 * @param headHash the hash of the head block, if any
	 * @param headStateId the state identifier at the end of the execution of the head block, if any
	 * @param onNull the generator of the exception to throw if some argument is {@code null}
	 * @param onIllegal the generator of the exception to throw if some argument has an illegal value
	 * @return the chain information object
	 * @throws ON_NULL if some argument is {@code null}
	 * @throws ON_ILLEGAL if some argument has an illegal value
	 */
	public static <ON_NULL extends Exception, ON_ILLEGAL extends Exception> ChainInfo of(long height, Optional<byte[]> genesisHash, Optional<byte[]> headHash, Optional<byte[]> headStateId, Function<String, ON_NULL> onNull, Function<String, ON_ILLEGAL> onIllegal) throws ON_NULL, ON_ILLEGAL {
		return new ChainInfoImpl(height, genesisHash, headHash, headStateId, onNull, onIllegal);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends ChainInfoEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends ChainInfoDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
    public static class Json extends ChainInfoJson {

    	/**
    	 * Creates the Json representation for the given chain info.
    	 * 
    	 * @param info the chain info
    	 */
    	public Json(ChainInfo info) {
    		super(info);
    	}
    }
}
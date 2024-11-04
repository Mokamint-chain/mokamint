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

import java.util.function.Function;

import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.internal.MempoolEntryImpl;
import io.mokamint.node.internal.gson.MempoolEntryDecoder;
import io.mokamint.node.internal.gson.MempoolEntryEncoder;
import io.mokamint.node.internal.gson.MempoolEntryJson;

/**
 * Providers of entries of the mempool of a Mokamint node.
 */
public abstract class MempoolEntries {

	private MempoolEntries() {}

	/**
	 * Yields a new mempool entry.
	 * 
	 * @param hash the hash of the transaction in the entry
	 * @param priority the priority of the transaction in the entry
	 * @return the mempool entry
	 */
	public static MempoolEntry of(byte[] hash, long priority) {
		return new MempoolEntryImpl(hash, priority);
	}

	/**
	 * Yields a new mempool entry.
	 * 
	 * @param hash the hash of the transaction in the entry
	 * @param priority the priority of the transaction in the entry
	 * @return the mempool entry
	 * @param onNull the generator of the exception to throw if some argument is {@code null}
	 * @param onIllegal the generator of the exception to throw if some argument has an illegal value
	 * @throws ON_NULL if some argument is {@code null}
	 * @throws ON_ILLEGAL if some argument has an illegal value
	 */
	public static <ON_NULL extends Exception, ON_ILLEGAL extends Exception> MempoolEntry of(byte[] hash, long priority, Function<String, ON_NULL> onNull, Function<String, ON_ILLEGAL> onIllegal) throws ON_NULL, ON_ILLEGAL {
		return new MempoolEntryImpl(hash, priority, onNull, onIllegal);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MempoolEntryEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends MempoolEntryDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
    public static class Json extends MempoolEntryJson {

    	/**
    	 * Creates the Json representation for the given mempool entry.
    	 * 
    	 * @param entry the mempool entry
    	 */
    	public Json(MempoolEntry entry) {
    		super(entry);
    	}
    }
}
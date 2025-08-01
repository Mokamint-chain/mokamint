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

import java.util.UUID;

import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.internal.MinerInfoImpl;
import io.mokamint.node.internal.json.MinerInfoDecoder;
import io.mokamint.node.internal.json.MinerInfoEncoder;
import io.mokamint.node.internal.json.MinerInfoJson;

/**
 * Providers of miner information objects.
 */
public abstract class MinerInfos {

	private MinerInfos() {}

	/**
	 * Yields a miner information object.
	 * 
	 * @param uuid the unique identifier of the miner
	 * @param points the points of the miner
	 * @param description the description of the miner
	 * @return the miner information object
	 */
	public static MinerInfo of(UUID uuid, long points, String description) {
		return new MinerInfoImpl(uuid, points, description);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MinerInfoEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends MinerInfoDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
	public static class Json extends MinerInfoJson {

    	/**
    	 * Creates the Json representation for the given miner information.
    	 * 
    	 * @param info the miner information
    	 */
    	public Json(MinerInfo info) {
    		super(info);
    	}
    }
}
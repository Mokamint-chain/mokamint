/*
Copyright 2025 Fausto Spoto

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
package io.mokamint.miner;

import java.security.PublicKey;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.websockets.beans.MappedDecoder;
import io.hotmoka.websockets.beans.MappedEncoder;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.internal.MiningSpecificationImpl;
import io.mokamint.miner.internal.json.MiningSpecificationJson;

/**
 * Providers of the specification of the mining parameters of the deadlines expected by a miner.
 */
public abstract class MiningSpecifications {

	private MiningSpecifications() {}

	/**
	 * Yields a mining specification for the given deadline parameters.
	 * 
	 * @param name the name of the mining specification
	 * @param description a description of the mining specification
	 * @param chainId the chain id of the deadlines expected by the miner
	 * @param hashingForDeadlines the hashing algorithm used for computing the deadlines expected by a miner
	 *                            having this specification
	 * @param signatureForBlocks the signature algorithm used for the key identifying the node
	 *                           in the deadlines expected by a miner having this specification
	 * @param signatureForDeadlines the signature algorithm used for the key identifying the plot
	 *                              containing the deadlines expected by a miner having this specification
	 * @param publicKeyForSigningBlocks the public key identifying the node in the deadlines
	 *                                  expected by a miner having this specification. This is a public key for the
	 *                                  {@code signatureForBlocks} algorithm
	 * @return the mining specification
	 */
	public static MiningSpecification of(String name, String description, String chainId, HashingAlgorithm hashingForDeadlines, SignatureAlgorithm signatureForBlocks, SignatureAlgorithm signatureForDeadlines, PublicKey publicKeyForSigningBlocks) {
		return new MiningSpecificationImpl(name, description, chainId, hashingForDeadlines, signatureForBlocks, signatureForDeadlines, publicKeyForSigningBlocks);
	}

	/**
	 * JSON representation.
	 */
	public static class Json extends MiningSpecificationJson {
	
		/**
		 * Creates the JSON representation for the given mining specification.
		 * 
		 * @param spec the mining specification
		 */
		public Json(MiningSpecification spec) {
			super(spec);
		}
	}

	/**
	 * JSON encoder.
	 */
	public static class Encoder extends MappedEncoder<MiningSpecification, Json> {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {
			super(Json::new);
		}
	}

	/**
	 * JSON decoder.
	 */
	public static class Decoder extends MappedDecoder<MiningSpecification, Json> {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {
			super(Json.class);
		}
	}
}
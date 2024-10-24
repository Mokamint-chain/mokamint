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

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.LocalDateTime;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.node.internal.AbstractBlockDescription;
import io.mokamint.node.internal.GenesisBlockDescriptionImpl;
import io.mokamint.node.internal.NonGenesisBlockDescriptionImpl;
import io.mokamint.node.internal.gson.BlockDescriptionDecoder;
import io.mokamint.node.internal.gson.BlockDescriptionEncoder;
import io.mokamint.node.internal.gson.BlockDescriptionJson;
import io.mokamint.nonce.api.Deadline;

/**
 * Providers of block descriptions.
 */
public abstract class BlockDescriptions {

	private BlockDescriptions() {}

	/**
	 * Yields a new non-genesis block description.
	 * 
	 * @param height the block height, non-negative, counting from 0, which is the genesis block
	 * @param power the power of the block, computed as the sum, for each block from genesis to the block,
	 *              of 2^(hashing bits) / (value of the deadline in the block + 1). This allows one to compare
	 *              forks and choose the one whose tip has the highest power. Intuitively, the power
	 *              expresses the space used to compute the chain leading to the block
	 * @param totalWaitingTime the total waiting time between the creation of the genesis block and the creation of the block
	 * @param weightedWaitingTime the weighted waiting time between the creation of the genesis block and the creation of the block
	 * @param acceleration a value used to divide the deadline to derive the time needed to wait for it.
	 *                     The higher, the shorter the time. This value changes dynamically to cope with
	 *                     varying mining power in the network. It is the inverse of Bitcoin's difficulty
	 * @param deadline the deadline computed for the block
	 * @param hashOfPreviousBlock the reference to the previous block
	 * @param hashingForBlocks the hashing algorithm used for the blocks
	 * @return the non-genesis block description
	 */
	public static NonGenesisBlockDescription of(long height, BigInteger power, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration, Deadline deadline, byte[] hashOfPreviousBlock, HashingAlgorithm hashingForBlocks) {
		return new NonGenesisBlockDescriptionImpl(height, power, totalWaitingTime, weightedWaitingTime, acceleration, deadline, hashOfPreviousBlock, hashingForBlocks);
	}

	/**
	 * Yields a new genesis block description.
	 * 
	 * @param startDateTimeUTC the moment when the block has been created
	 * @param acceleration the initial value of the acceleration, that is,
	 *                     a value used to divide deadlines to derive the time needed to wait for it.
	 *                     The higher, the shorter the time. This value will change dynamically to cope with
	 *                     varying mining power in the network. It is the inverse of Bitcoin's difficulty
	 * @param hashingForDeadlines the hashing algorithm used for the deadlines
	 * @param hashingForGenerations the hashing algorithm used for the generation signatures
	 * @param signatureForBlock the signature algorithm for the block
	 * @param publicKey the public key of the signer of the block
	 * @return the genesis block description
	 * @throws InvalidKeyException if the public key is invalid
	 */
	public static GenesisBlockDescription genesis(LocalDateTime startDateTimeUTC, BigInteger acceleration, HashingAlgorithm hashingForDeadlines, HashingAlgorithm hashingForGenerations, SignatureAlgorithm signatureForBlock, PublicKey publicKey) throws InvalidKeyException {
		return new GenesisBlockDescriptionImpl(startDateTimeUTC, acceleration, hashingForDeadlines, hashingForGenerations, signatureForBlock, publicKey);
	}

	/**
	 * Unmarshals a block description from the given context. It assumes that the description was marshalled
	 * by using {@link BlockDescription#intoWithoutConfigurationData(io.hotmoka.marshalling.api.MarshallingContext)}.
	 * 
	 * @param context the context
	 * @param config the consensus configuration of the node storing the block description
	 * @return the block description
	 * @throws IOException if the block description cannot be unmarshalled
	 */
	public static BlockDescription from(UnmarshallingContext context, ConsensusConfig<?,?> config) throws IOException {
		return AbstractBlockDescription.from(context, config);
	}

	/**
	 * Unmarshals a block description from the given context. It assumes that the description was marshalled
	 * by using {@link BlockDescription#into(io.hotmoka.marshalling.api.MarshallingContext)}.
	 * 
	 * @param context the context
	 * @return the block description
	 * @throws IOException if the block description cannot be unmarshalled
	 * @throws NoSuchAlgorithmException if the block description refers to an unknown cryptographic algorithm
	 */
	public static BlockDescription from(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		return AbstractBlockDescription.from(context);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends BlockDescriptionEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends BlockDescriptionDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
	public static class Json extends BlockDescriptionJson {

    	/**
    	 * Creates the Json representation for the given block.
    	 * 
    	 * @param description the block description
    	 */
    	public Json(BlockDescription description) {
    		super(description);
    	}
    }
}
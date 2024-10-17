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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.util.stream.Stream;

import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.internal.AbstractBlock;
import io.mokamint.node.internal.GenesisBlockImpl;
import io.mokamint.node.internal.NonGenesisBlockImpl;
import io.mokamint.node.internal.gson.BlockDecoder;
import io.mokamint.node.internal.gson.BlockEncoder;
import io.mokamint.node.internal.gson.BlockJson;

/**
 * Providers of blocks.
 */
public abstract class Blocks {

	private Blocks() {}

	/**
	 * Yields a non-genesis block with the given description. It adds a signature to the resulting block,
	 * by using the signature algorithm in the prolog of the deadline and the given private key.
	 * 
	 * @param description the description
	 * @param transactions the transactions inside the block
	 * @param stateHash the hash of the state of the application at the end of this block
	 * @param privateKey the private key for signing the block
	 * @return the non-genesis block
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public static NonGenesisBlock of(NonGenesisBlockDescription description, Stream<Transaction> transactions, byte[] stateHash, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		return new NonGenesisBlockImpl(description, transactions, stateHash, privateKey);
	}

	/**
	 * Yields a genesis block with the given description. It adds a signature to the resulting block,
	 * by using the signature algorithm in the prolog of the deadline and the given private key.
	 * 
	 * @param description the description of the block
	 * @param stateHash the hash of the state of the application at the end of this block
	 * @param privateKey the key used for signing the block
	 * @return the genesis block
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public static GenesisBlock genesis(GenesisBlockDescription description, byte[] stateHash, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		return new GenesisBlockImpl(description, stateHash, privateKey);
	}

	/**
	 * Unmarshals a block from the given context. It assumes that the block was marshalled
	 * by using {@link Block#intoWithoutConfigurationData(io.hotmoka.marshalling.api.MarshallingContext)}.
	 * 
	 * @param context the context
	 * @param config the consensus configuration of the node storing the block description
	 * @return the block
	 * @throws IOException if the block cannot be unmarshalled
	 */
	public static Block from(UnmarshallingContext context, ConsensusConfig<?,?> config) throws IOException {
		return AbstractBlock.from(context, config);
	}

	/**
	 * Unmarshals a block from the given context. It assumes that the block was marshalled
	 * by using {@link Block#into(io.hotmoka.marshalling.api.MarshallingContext)}.
	 * 
	 * @param context the context
	 * @return the block
	 * @throws IOException if the block cannot be unmarshalled
	 * @throws NoSuchAlgorithmException if the block refers to an unknown cryptographic algorithm
	 */
	public static Block from(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		return AbstractBlock.from(context);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends BlockEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends BlockDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
	public static class Json extends BlockJson {

    	/**
    	 * Creates the Json representation for the given block.
    	 * 
    	 * @param block the block
    	 */
    	public Json(Block block) {
    		super(block);
    	}
    }
}
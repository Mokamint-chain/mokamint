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
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;

import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.node.internal.AbstractBlock;
import io.mokamint.node.internal.GenesisBlockImpl;
import io.mokamint.node.internal.NonGenesisBlockImpl;
import io.mokamint.node.internal.gson.BlockDecoder;
import io.mokamint.node.internal.gson.BlockEncoder;
import io.mokamint.node.internal.gson.BlockJson;
import io.mokamint.nonce.api.Deadline;

/**
 * Providers of blocks.
 */
public abstract class Blocks {

	private Blocks() {}

	/**
	 * Yields a new non-genesis block with the given description. It adds a signature to the resulting block,
	 * by using the signature algorithm in the prolog of the deadline and the given private key.
	 * 
	 * @param description the description
	 * @param privateKey the private key for signing the block
	 * @return the non-genesis block
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public static NonGenesisBlock of(NonGenesisBlockDescription description, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		return new NonGenesisBlockImpl(description, privateKey);
	}

	/**
	 * Yields a new non-genesis block. The signature will be computed from its hash by using the
	 * signature algorithm in the prolog of the deadline and the given private key.
	 * 
	 * @param height the block height, non-negative, counting from 0, which is the genesis block
	 * @param power the power of this block, computed as the sum, for each block from genesis to this block,
	 *              of 2^(hashing bits) / (value of the deadline in the block + 1). This allows one to compare
	 *              forks and choose the one whose tip has the highest power. Intuitively, the power
	 *              expresses the space used to compute the chain leading to the block
	 * @param totalWaitingTime the total waiting time between the creation of the genesis block and the creation of this block
	 * @param weightedWaitingTime the weighted waiting time between the creation of the genesis block and the creation of this block
	 * @param acceleration a value used to divide the deadline to derive the time needed to wait for it.
	 *                     The higher, the shorter the time. This value changes dynamically to cope with
	 *                     varying mining power in the network. It is the inverse of Bitcoin's difficulty
	 * @param deadline the deadline computed for this block
	 * @param hashOfPreviousBlock the reference to the previous block
	 * @param privateKey the private key to use to sign the block; it must match the public key contained in the deadline
	 * @return the non-genesis block
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public static NonGenesisBlock of(long height, BigInteger power, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration,
			Deadline deadline, byte[] hashOfPreviousBlock, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		return new NonGenesisBlockImpl(height, power, totalWaitingTime, weightedWaitingTime, acceleration, deadline, hashOfPreviousBlock, privateKey);
	}

	/**
	 * Yields a new non-genesis block with the given signature.
	 * 
	 * @param height the block height, non-negative, counting from 0, which is the genesis block
	 * @param power the power of this block, computed as the sum, for each block from genesis to this block,
	 *              of 2^(hashing bits) / (value of the deadline in the block + 1). This allows one to compare
	 *              forks and choose the one whose tip has the highest power. Intuitively, the power
	 *              expresses the space used to compute the chain leading to the block
	 * @param totalWaitingTime the total waiting time between the creation of the genesis block and the creation of this block
	 * @param weightedWaitingTime the weighted waiting time between the creation of the genesis block and the creation of this block
	 * @param acceleration a value used to divide the deadline to derive the time needed to wait for it.
	 *                     The higher, the shorter the time. This value changes dynamically to cope with
	 *                     varying mining power in the network. It is the inverse of Bitcoin's difficulty
	 * @param deadline the deadline computed for this block
	 * @param hashOfPreviousBlock the reference to the previous block
	 * @param signature the signature of the block
	 * @return the non-genesis block
	 */
	public static NonGenesisBlock of(long height, BigInteger power, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration,
			Deadline deadline, byte[] hashOfPreviousBlock, byte[] signature) {
		return new NonGenesisBlockImpl(height, power, totalWaitingTime, weightedWaitingTime, acceleration, deadline, hashOfPreviousBlock, signature);
	}

	/**
	 * Yields a new genesis block.
	 * 
	 * @param description the description of the block
	 * @param privateKey the key used for signing the block
	 * @return the genesis block
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public static GenesisBlock genesis(GenesisBlockDescription description, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		return new GenesisBlockImpl(description, privateKey);
	}

	/**
	 * Yields a new genesis block.
	 * 
	 * @param startDateTimeUTC the moment when the block has been created
	 * @param acceleration the initial value of the acceleration, that is,
	 *                     a value used to divide deadlines to derive the time needed to wait for it.
	 *                     The higher, the shorter the time. This value changes dynamically to cope with
	 *                     varying mining power in the network. It is the inverse of Bitcoin's difficulty
	 * @param signatureForBlocks the signature algorithm for the blocks
	 * @param keys the key pair to use for signing the block
	 * @return the genesis block
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public static GenesisBlock genesis(LocalDateTime startDateTimeUTC, BigInteger acceleration, SignatureAlgorithm signatureForBlocks, KeyPair keys) throws InvalidKeyException, SignatureException {
		return new GenesisBlockImpl(startDateTimeUTC, acceleration, signatureForBlocks, keys);
	}

	/**
	 * Yields a new genesis block.
	 * 
	 * @param startDateTimeUTC the moment when the block has been created
	 * @param acceleration the initial value of the acceleration, that is,
	 *                     a value used to divide deadlines to derive the time needed to wait for it.
	 *                     The higher, the shorter the time. This value changes dynamically to cope with
	 *                     varying mining power in the network. It is the inverse of Bitcoin's difficulty
	 * @param signatureForBlocks the signature algorithm for the blocks
	 * @param publicKey the public key corresponding to the private key used for generating the signature
	 * @param signature the signature of the block
	 * @return the genesis block
	 * @throws InvalidKeySpecException if the public key is invalid
	 */
	public static GenesisBlock genesis(LocalDateTime startDateTimeUTC, BigInteger acceleration, SignatureAlgorithm signatureForBlocks, String publicKey, byte[] signature) throws InvalidKeySpecException {
		return new GenesisBlockImpl(startDateTimeUTC, acceleration, signatureForBlocks, publicKey, signature);
	}

	/**
	 * Unmarshals a block from the given context.
	 * 
	 * @param context the context
	 * @return the block
	 * @throws NoSuchAlgorithmException if some hashing or signature algorithm in the block is unknown
	 * @throws IOException if the block cannot be unmarshalled
	 */
	public static Block from(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
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
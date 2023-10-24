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
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;

import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.node.internal.AbstractBlockDescription;
import io.mokamint.node.internal.GenesisBlockDescriptionImpl;
import io.mokamint.node.internal.NonGenesisBlockDescriptionImpl;
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
	 * @return the non-genesis block description
	 */
	public static NonGenesisBlockDescription of(long height, BigInteger power, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration, Deadline deadline, byte[] hashOfPreviousBlock) {
		return new NonGenesisBlockDescriptionImpl(height, power, totalWaitingTime, weightedWaitingTime, acceleration, deadline, hashOfPreviousBlock);
	}

	/**
	 * Yields a new genesis block description.
	 * 
	 * @param startDateTimeUTC the moment when the block has been created
	 * @param acceleration the initial value of the acceleration, that is,
	 *                     a value used to divide deadlines to derive the time needed to wait for it.
	 *                     The higher, the shorter the time. This value changes dynamically to cope with
	 *                     varying mining power in the network. It is the inverse of Bitcoin's difficulty
	 * @param signatureForBlocks the signature algorithm for the blocks
	 * @param publicKey the public key of the signer of the block
	 * @return the genesis block description
	 * @throws InvalidKeyException if the publi ckey is invalid
	 */
	public static GenesisBlockDescription genesis(LocalDateTime startDateTimeUTC, BigInteger acceleration, SignatureAlgorithm signatureForBlocks, PublicKey publicKey) throws InvalidKeyException {
		return new GenesisBlockDescriptionImpl(startDateTimeUTC, acceleration, signatureForBlocks, publicKey);
	}

	/**
	 * Yields a new genesis block description.
	 * 
	 * @param startDateTimeUTC the moment when the block has been created
	 * @param acceleration the initial value of the acceleration, that is,
	 *                     a value used to divide deadlines to derive the time needed to wait for it.
	 *                     The higher, the shorter the time. This value changes dynamically to cope with
	 *                     varying mining power in the network. It is the inverse of Bitcoin's difficulty
	 * @param signatureForBlocks the signature algorithm for the blocks
	 * @param publicKeyBase58 the public key of the signer of the block, in Base58 format
	 * @return the genesis block description
	 * @throws InvalidKeySpecException if the public key is invalid
	 */
	public static GenesisBlockDescription genesis(LocalDateTime startDateTimeUTC, BigInteger acceleration, SignatureAlgorithm signatureForBlocks, String publicKeyBase58) throws InvalidKeySpecException {
		return new GenesisBlockDescriptionImpl(startDateTimeUTC, acceleration, signatureForBlocks, publicKeyBase58);
	}

	/**
	 * Unmarshals a block description from the given context.
	 * 
	 * @param context the context
	 * @return the block description
	 * @throws NoSuchAlgorithmException if some hashing or signature algorithm in the block description is unknown
	 * @throws IOException if the block description cannot be unmarshalled
	 */
	public static BlockDescription from(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		return AbstractBlockDescription.from(context);
	}
}
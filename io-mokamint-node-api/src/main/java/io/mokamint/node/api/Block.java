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

package io.mokamint.node.api;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.Marshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.mokamint.nonce.api.Deadline;

/**
 * A block of the Mokamint blockchain.
 */
@Immutable
public interface Block extends Marshallable, Whisperable {

	/**
	 * Yields the description of this block.
	 * 
	 * @return the description
	 */
	BlockDescription getDescription();

	/**
	 * Yields the signature of this block, computed from its hash by the node
	 * that mined this block. This signature must have been computed with the
	 * private key corresponding to the node's public key, which is inside the prolog
	 * of the deadline for non-genesis blocks or inside the genesis blocks themselves.
	 * 
	 * @return the signature
	 */
	byte[] getSignature();

	/**
	 * Yields the identifier of the state of the application at the end of the execution of the
	 * transactions from the beginning of the blockchain to the end of this block.
	 * 
	 * @return the identifier of the state of the application
	 */
	byte[] getStateId();

	/**
	 * Yields the hash of this block, by using the given hashing algorithm.
	 * This hash does not use the signature of the node (if any) which is, instead,
	 * computed from this hash and the private key of the signer.
	 * 
	 * @param hashing the hashing algorithm
	 * @return the hash of this block
	 */
	byte[] getHash(HashingAlgorithm hashing);

	/**
	 * Yields the hash of this block, by using the given hashing algorithm,
	 * as an hexadecimal string.
	 * 
	 * @param hashing the hashing algorithm
	 * @return the hash of this block, as a hexadecimal string
	 */
	String getHexHash(HashingAlgorithm hashing);

	/**
	 * Yields the description of the next block, assuming that it has the given deadline.
	 * 
	 * @param deadline the deadline of the next block
	 * @param config the consensus configuration of the node storing this block
	 * @return the description
	 */
	NonGenesisBlockDescription getNextBlockDescription(Deadline deadline, ConsensusConfig<?,?> config);

	@Override
	String toString();

	/**
	 * Yields a string representation of this block description. This yields a more informative
	 * representation of the block description than {@link #toString()}, with extra information
	 * computed by using the given configuration for the node.
	 * 
	 * @param config the configuration used to interpret and reconstruct the extra
	 *               information about the block description, if any
	 * @param startDateTimeUTC the creation time of the genesis block of the chain of the block description, if any
	 * @return the representation
	 */
	String toString(Optional<ConsensusConfig<?,?>> config, Optional<LocalDateTime> startDateTimeUTC);

	/**
	 * Marshals this object into a given stream. This method in general
	 * performs better than standard Java serialization, wrt the size of the marshalled data.
	 * It does not report information that can be recomputed from the configuration of the
	 * node storing this block.
	 * 
	 * @param context the context holding the stream
	 * @throws IOException if this object cannot be marshalled
	 */
	void intoWithoutConfigurationData(MarshallingContext context) throws IOException;
}
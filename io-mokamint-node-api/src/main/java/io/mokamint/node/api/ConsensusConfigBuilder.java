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

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;

/**
 * The builder of a consensus configuration object.
 * 
 * @param <C> the concrete type of the configuration
 * @param <B> the concrete type of the builder
 */
public interface ConsensusConfigBuilder<C extends ConsensusConfig<C,B>, B extends ConsensusConfigBuilder<C,B>> {

	/**
	 * Sets the chain identifier of the blockchain the node belongs to.
	 * 
	 * @param chainId the chain identifier
	 * @return this builder
	 */
	B setChainId(String chainId);

	/**
	 * Sets the hashing algorithm for computing the deadlines and hence also the
	 * plot files used by the miners.
	 * 
	 * @param hashingForDeadlines the hashing algorithm
	 * @return this builder
	 */
	B setHashingForDeadlines(HashingAlgorithm hashingForDeadlines);

	/**
	 * Sets the hashing algorithm for computing the new generation and the new scoop
	 * number from the previous block.
	 * 
	 * @param hashingForGenerations the hashing algorithm
	 * @return this builder
	 */
	B setHashingForGenerations(HashingAlgorithm hashingForGenerations);

	/**
	 * Sets the hashing algorithm for identifying the blocks in the Mokamint blockchain.
	 * 
	 * @param hashingForBlocks the hashing algorithm
	 * @return this builder
	 */
	B setHashingForBlocks(HashingAlgorithm hashingForBlocks);

	/**
	 * Sets the signature algorithm that the nodes use to sign the blocks.
	 * 
	 * @param signatureForBlocks the signature algorithm
	 * @return this builder
	 */
	B setSignatureForBlocks(SignatureAlgorithm signatureForBlocks);

	/**
	 * Sets the signature algorithm that the miners use to sign the deadlines.
	 * 
	 * @param signatureAlgorithmForDeadlines the signature algorithm
	 * @return this builder
	 */
	B setSignatureForDeadlines(SignatureAlgorithm signatureAlgorithmForDeadlines);

	/**
	 * Sets the acceleration for the genesis block. This specifies how
	 * quickly get blocks generated at the beginning of a chain. The less
	 * mining power has the network at the beginning, the higher the
	 * initial acceleration should be, or otherwise the creation of the first blocks
	 * might take a long time.
	 * 
	 * @param initialAcceleration the initial acceleration
	 * @return this builder
	 */
	B setInitialAcceleration(long initialAcceleration);

	/**
	 * Sets the target time interval, in milliseconds, between the creation of a block
	 * and the creation of a next block.  The network will strive to get close
	 * to this time. The higher the hashing power of the network, the more precise
	 * this will be.
	 * 
	 * @param targetBlockCreationTime the target time interval, in milliseconds
	 * @return this builder
	 */
	B setTargetBlockCreationTime(long targetBlockCreationTime);

	/**
	 * Builds the configuration.
	 * 
	 * @return the configuration
	 */
	C build();
}
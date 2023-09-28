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

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;

/**
 * The configuration of a Mokamint node. Nodes of the same network must agree
 * on this data in order to achieve consensus.
 * 
 * @param <C> the concrete type of the configuration
 * @param <B> the concrete type of the builder
 */
@Immutable
public interface ConsensusConfig<C extends ConsensusConfig<C,B>, B extends ConsensusConfigBuilder<C,B>> {

	/**
	 * Yields the chain identifier of the blockchain the node belongs to.
	 * 
	 * @return the chain identifier
	 */
	String getChainId();

	/**
	 * Yields the hashing algorithm used for computing the deadlines, hence
	 * also in the plot files used by the miners.
	 * 
	 * @return the hashing algorithm
	 */
	HashingAlgorithm<byte[]> getHashingForDeadlines();

	/**
	 * Yields the hashing algorithm used for computing the next generation signature
	 * and the new scoop number from the previous block.
	 * 
	 * @return the hashing algorithm
	 */
	HashingAlgorithm<byte[]> getHashingForGenerations();

	/**
	 * Yields the hashing algorithm used for the identifying the blocks of
	 * the Mokamint blockchain.
	 * 
	 * @return the hashing algorithm
	 */
	HashingAlgorithm<Block> getHashingForBlocks();

	/**
	 * Yields the signature algorithm that nodes use to sign the blocks.
	 * 
	 * @return the signature algorithm
	 */
	SignatureAlgorithm getSignatureForBlocks();

	/**
	 * Yields the signature algorithm that miners use to sign the deadlines.
	 * 
	 * @return the signature algorithm
	 */
	SignatureAlgorithm getSignatureForDeadlines();

	/**
	 * Yields the acceleration for the genesis block. This specifies how
	 * quickly get blocks generated at the beginning of a chain. The less
	 * mining power has the network at the beginning, the higher the
	 * initial acceleration should be, or otherwise the creation of the first blocks
	 * might take a long time.
	 * 
	 * @return the initial acceleration
	 */
	long getInitialAcceleration();

	/**
	 * Yields the target time interval, in milliseconds, between the creation of a block
	 * and the creation of a next block. The network will strive to get close
	 * to this time. The higher the hashing power of the network, the more precise
	 * this will be.
	 * 
	 * @return the time interval
	 */
	long getTargetBlockCreationTime();

	/**
	 * Yields a toml representation of this configuration.
	 * 
	 * @return the toml representation, as a string
	 */
	String toToml();

	/**
	 * Yields a builder initialized with the information in this object.
	 * 
	 * @return the builder
	 */
	B toBuilder();

	@Override
	boolean equals(Object other);

	@Override
	String toString();
}
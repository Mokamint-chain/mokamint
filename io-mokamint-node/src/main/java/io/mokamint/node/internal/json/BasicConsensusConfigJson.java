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

package io.mokamint.node.internal.json;

import java.security.NoSuchAlgorithmException;

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.internal.BasicConsensusConfigBuilder;

/**
 * The JSON representation of a {@link ConsensusConfig}.
 */
public abstract class BasicConsensusConfigJson implements JsonRepresentation<ConsensusConfig<?,?>> {
	private final String chainId;
	private final String hashingForDeadlines;
	private final String hashingForGenerations;
	private final String hashingForBlocks;
	private final String hashingForTransactions;
	private final String signatureForBlocks;
	private final String signatureForDeadlines;
	private final int targetBlockCreationTime;
	private final int maxBlockSize;
	private final int maxRequestSize;
	private final int oblivion;

	protected BasicConsensusConfigJson(ConsensusConfig<?,?> config) {
		this.chainId = config.getChainId();
		this.hashingForDeadlines = config.getHashingForDeadlines().getName();
		this.hashingForGenerations = config.getHashingForGenerations().getName();
		this.hashingForBlocks = config.getHashingForBlocks().getName();
		this.hashingForTransactions = config.getHashingForRequests().getName();
		this.signatureForBlocks = config.getSignatureForBlocks().getName();
		this.signatureForDeadlines = config.getSignatureForDeadlines().getName();
		this.targetBlockCreationTime = config.getTargetBlockCreationTime();
		this.maxBlockSize = config.getMaxBlockSize();
		this.maxRequestSize = config.getMaxRequestSize();
		this.oblivion = config.getOblivion();
	}

	/**
	 * Yields the chain identifier of the blockchain the node belongs to.
	 * 
	 * @return the chain identifier
	 */
	public String getChainId() {
		return chainId;
	}

	/**
	 * Yields the name of the hashing algorithm used for computing the deadlines, hence
	 * also in the plot files used by the miners.
	 * 
	 * @return the name of the hashing algorithm
	 */
	public String getHashingForDeadlines() {
		return hashingForDeadlines;
	}

	/**
	 * Yields the name of the hashing algorithm used for computing the next generation signature
	 * and the new scoop number from the previous block.
	 * 
	 * @return the name of the hashing algorithm
	 */
	public String getHashingForGenerations() {
		return hashingForGenerations;
	}

	/**
	 * Yields the name of the hashing algorithm used for the identifying the blocks of
	 * the Mokamint blockchain.
	 * 
	 * @return the name of the hashing algorithm
	 */
	public String getHashingForBlocks() {
		return hashingForBlocks;
	}

	/**
	 * Yields the name of the hashing algorithm used for the identifying the requests of
	 * the Mokamint blockchain.
	 * 
	 * @return the name of the hashing algorithm
	 */
	public String getHashingForRequests() {
		return hashingForTransactions;
	}

	/**
	 * Yields the name of the signature algorithm that nodes use to sign the blocks.
	 * 
	 * @return the name of the signature algorithm
	 */
	public String getSignatureForBlocks() {
		return signatureForBlocks;
	}

	/**
	 * Yields the name of the signature algorithm that miners use to sign the deadlines.
	 * 
	 * @return the name of the signature algorithm
	 */
	public String getSignatureForDeadlines() {
		return signatureForDeadlines;
	}

	/**
	 * Yields the target time interval, in milliseconds, between the creation of a block
	 * and the creation of a next block.
	 * 
	 * @return the time interval
	 */
	public int getTargetBlockCreationTime() {
		return targetBlockCreationTime;
	}

	/**
	 * Yields the maximal size of a block's requests table.
	 * 
	 * @return the maximal size (in bytes)
	 */
	public int getMaxBlockSize() {
		return maxBlockSize;
	}

	/**
	 * Yields the maximal size of a request.
	 * 
	 * @return the maximal size (in bytes)
	 */
	public int getMaxRequestSize() {
		return maxRequestSize;
	}

	/**
	 * Yields the rapidity of the changes of acceleration for the creation time of new blocks.
	 * 
	 * @return the rapidity of changes of acceleration. It is a value between 0
	 *         (no acceleration change) to 100,000 (maximally fast change)
	 */
	public int getOblivion() {
		return oblivion;
	}

	@Override
	public ConsensusConfig<?,?> unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		return new BasicConsensusConfigBuilder(this).build();
	}
}
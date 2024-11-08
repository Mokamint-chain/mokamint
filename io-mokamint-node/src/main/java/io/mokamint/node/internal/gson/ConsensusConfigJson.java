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

package io.mokamint.node.internal.gson;

import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.ConsensusConfigBuilders;
import io.mokamint.node.api.ConsensusConfig;

/**
 * The JSON representation of a {@code Config}.
 */
public abstract class ConsensusConfigJson implements JsonRepresentation<ConsensusConfig<?,?>> {
	private final String chainId;
	private final String hashingForDeadlines;
	private final String hashingForGenerations;
	private final String hashingForBlocks;
	private final String hashingForTransactions;
	private final String signatureForBlocks;
	private final String signatureForDeadlines;
	private final int targetBlockCreationTime;
	private final int maxBlockSize;
	private final int oblivion;

	protected ConsensusConfigJson(ConsensusConfig<?,?> config) {
		this.chainId = config.getChainId();
		this.hashingForDeadlines = config.getHashingForDeadlines().getName();
		this.hashingForGenerations = config.getHashingForGenerations().getName();
		this.hashingForBlocks = config.getHashingForBlocks().getName();
		this.hashingForTransactions = config.getHashingForTransactions().getName();
		this.signatureForBlocks = config.getSignatureForBlocks().getName();
		this.signatureForDeadlines = config.getSignatureForDeadlines().getName();
		this.targetBlockCreationTime = config.getTargetBlockCreationTime();
		this.maxBlockSize = config.getMaxBlockSize();
		this.oblivion = config.getOblivion();
	}

	@Override
	public ConsensusConfig<?,?> unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		try {
			return ConsensusConfigBuilders.defaults() // TODO: maybe add constructor with JSON
					.setChainId(chainId)
					.setHashingForDeadlines(HashingAlgorithms.of(hashingForDeadlines))
					.setHashingForGenerations(HashingAlgorithms.of(hashingForGenerations))
					.setHashingForBlocks(HashingAlgorithms.of(hashingForBlocks))
					.setHashingForTransactions(HashingAlgorithms.of(hashingForTransactions))
					.setSignatureForBlocks(SignatureAlgorithms.of(signatureForBlocks))
					.setSignatureForDeadlines(SignatureAlgorithms.of(signatureForDeadlines))
					.setTargetBlockCreationTime(targetBlockCreationTime)
					.setMaxBlockSize(maxBlockSize)
					.setOblivion(oblivion)
					.build();
		}
		catch (NullPointerException | IllegalArgumentException e) {
			throw new InconsistentJsonException(e);
		}
	}
}
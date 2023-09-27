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
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.ConsensusConfigBuilders;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.nonce.api.Deadline;

/**
 * The JSON representation of a {@code Config}.
 */
public abstract class ConsensusConfigJson implements JsonRepresentation<ConsensusConfig<?,?>> {
	private String chainId;
	private String hashingForDeadlines;
	private String hashingForGenerations;
	private String hashingForBlocks;
	private String signatureForBlocks;
	private String signatureForDeadlines;
	private long targetBlockCreationTime;
	private long initialAcceleration;

	protected ConsensusConfigJson(ConsensusConfig<?,?> config) {
		this.chainId = config.getChainId();
		this.hashingForDeadlines = config.getHashingForDeadlines().getName();
		this.hashingForGenerations = config.getHashingForGenerations().getName();
		this.hashingForBlocks = config.getHashingForBlocks().getName();
		this.signatureForBlocks = config.getSignatureForBlocks().getName();
		this.signatureForDeadlines = config.getSignatureForDeadlines().getName();
		this.targetBlockCreationTime = config.getTargetBlockCreationTime();
		this.initialAcceleration = config.getInitialAcceleration();
	}

	@Override
	public ConsensusConfig<?,?> unmap() throws NoSuchAlgorithmException {
		return ConsensusConfigBuilders.defaults()
			.setChainId(chainId)
			.setHashingForDeadlines(hashingForDeadlines)
			.setHashingForGenerations(hashingForGenerations)
			.setHashingForBlocks(HashingAlgorithms.of(hashingForBlocks, Block::toByteArray).getSupplier())
			.setSignatureForBlocks(SignatureAlgorithms.of(signatureForBlocks, NonGenesisBlock::toByteArray).getSupplier())
			.setSignatureForDeadlines(SignatureAlgorithms.of(signatureForDeadlines, Deadline::toByteArray).getSupplier())
			.setTargetBlockCreationTime(targetBlockCreationTime)
			.setInitialAcceleration(initialAcceleration)
			.build();
	}
}
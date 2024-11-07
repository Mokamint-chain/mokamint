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

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.Hex;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.node.internal.AbstractBlockDescription;
import io.mokamint.nonce.Deadlines;

/**
 * The JSON representation of a {@link BlockDescription}.
 */
public abstract class BlockDescriptionJson implements JsonRepresentation<BlockDescription> {
	private String startDateTimeUTC;
	private Long height;
	private BigInteger power;
	private Long totalWaitingTime;
	private Long weightedWaitingTime;
	private BigInteger acceleration;
	private Deadlines.Json deadline;
	private final int targetBlockCreationTime;
	private final String hashingForBlocks;
	private String hashingForDeadlines;
	private String hashingForGenerations;
	private final String hashingForTransactions;
	private String hashOfPreviousBlock;
	private String signatureForBlocks;
	private String publicKey;

	protected BlockDescriptionJson(BlockDescription description) {
		this.targetBlockCreationTime = description.getTargetBlockCreationTime();
		this.hashingForBlocks = description.getHashingForBlocks().getName();
		this.hashingForTransactions = description.getHashingForTransactions().getName();

		if (description instanceof GenesisBlockDescription gbd) {
			this.startDateTimeUTC = ISO_LOCAL_DATE_TIME.format(gbd.getStartDateTimeUTC());
			this.hashingForDeadlines = gbd.getHashingForDeadlines().getName();
			this.hashingForGenerations = gbd.getHashingForGenerations().getName();
			this.signatureForBlocks = gbd.getSignatureForBlocks().getName();
			this.publicKey = gbd.getPublicKeyForSigningBlockBase58();
		}
		else {
			var ngbd = (NonGenesisBlockDescription) description;
			this.height = ngbd.getHeight();
			this.power = ngbd.getPower();
			this.totalWaitingTime = ngbd.getTotalWaitingTime();
			this.weightedWaitingTime = ngbd.getWeightedWaitingTime();
			this.acceleration = ngbd.getAcceleration();
			this.deadline = new Deadlines.Json(ngbd.getDeadline());
			this.hashOfPreviousBlock = Hex.toHexString(ngbd.getHashOfPreviousBlock());
		}
	}

	public Long getHeight() {
		return height;
	}

	public BigInteger getPower() {
		return power;
	}

	public Long getTotalWaitingTime() {
		return totalWaitingTime;
	}

	public Long getWeightedWaitingTime() {
		return weightedWaitingTime;
	}

	public BigInteger getAcceleration() {
		return acceleration;
	}

	public Deadlines.Json getDeadline() {
		return deadline;
	}

	public String getHashOfPreviousBlock() {
		return hashOfPreviousBlock;
	}

	public String getStartDateTimeUTC() {
		return startDateTimeUTC;
	}

	public int getTargetBlockCreationTime() {
		return targetBlockCreationTime;
	}

	public String getHashingForBlocks() {
		return hashingForBlocks;
	}

	public String getHashingForTransactions() {
		return hashingForTransactions;
	}

	public String getHashingForDeadlines() {
		return hashingForDeadlines;
	}

	public String getHashingForGenerations() {
		return hashingForGenerations;
	}

	public String getSignatureForBlocks() {
		return signatureForBlocks;
	}

	public String getPublicKey() {
		return publicKey;
	}

	@Override
	public BlockDescription unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		return AbstractBlockDescription.from(this);
	}
}
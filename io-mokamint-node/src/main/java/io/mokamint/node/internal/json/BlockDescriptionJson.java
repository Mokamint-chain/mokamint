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
	private final int oblivion;
	private final String hashingForBlocks;
	private String hashingForDeadlines;
	private String hashingForGenerations;
	private final String hashingForTransactions;
	private String hashOfPreviousBlock;
	private String signatureForBlocks;
	private String publicKey;

	protected BlockDescriptionJson(BlockDescription description) {
		this.targetBlockCreationTime = description.getTargetBlockCreationTime();
		this.oblivion = description.getOblivion();
		this.hashingForBlocks = description.getHashingForBlocks().getName();
		this.hashingForTransactions = description.getHashingForRequests().getName();

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

	/**
	 * Yields the height of the block, counting from 0 for the genesis block.
	 * 
	 * @return the height of the block
	 */
	public Long getHeight() {
		return height;
	}

	/**
	 * Yields the power of the block.
	 * 
	 * @return the power of the block
	 */
	public BigInteger getPower() {
		return power;
	}

	/**
	 * Yields the total waiting time, in milliseconds, from the genesis block
	 * until the creation of the block.
	 * 
	 * @return the total waiting time
	 */
	public Long getTotalWaitingTime() {
		return totalWaitingTime;
	}

	/**
	 * Yields the weighted waiting time, in milliseconds, from the genesis block
	 * until the creation of the block.
	 * 
	 * @return the weighted waiting time
	 */
	public Long getWeightedWaitingTime() {
		return weightedWaitingTime;
	}

	/**
	 * Yields the acceleration used for the creation of the block.
	 * 
	 * @return the acceleration used for the creation of the block
	 */
	public BigInteger getAcceleration() {
		return acceleration;
	}

	/**
	 * Yields the deadline computed for the block.
	 * 
	 * @return the deadline, if any; this might be {@code null}
	 */
	public Deadlines.Json getDeadline() {
		return deadline;
	}

	/**
	 * Yields the reference to the previous block.
	 * 
	 * @return the reference to the previous block, if any, as a hexadecimal string;
	 *         this might be {@code null}
	 */
	public String getHashOfPreviousBlock() {
		return hashOfPreviousBlock;
	}

	/**
	 * The moment when the block has been mined. This is the moment
	 * when the blockchain started.
	 * 
	 * @return the moment when the block has been mined
	 */
	public String getStartDateTimeUTC() {
		return startDateTimeUTC;
	}

	/**
	 * Yields the target creation time for the blocks.
	 * 
	 * @return the target creation time, in milliseconds
	 */
	public int getTargetBlockCreationTime() {
		return targetBlockCreationTime;
	}

	/**
	 * Yields the rapidity of the changes of acceleration for the creation time of new blocks.
	 * 
	 * @return the rapidity of changes of acceleration
	 */
	public int getOblivion() {
		return oblivion;
	}

	/**
	 * Yields the name of the hashing algorithm used for the blocks.
	 * 
	 * @return the name of the hashing algorithm for the blocks
	 */
	public String getHashingForBlocks() {
		return hashingForBlocks;
	}

	/**
	 * Yields the name of the hashing algorithm used for the requests in the blocks.
	 * 
	 * @return the name of the hashing algorithm for the requests in the blocks
	 */
	public String getHashingForRequests() {
		return hashingForTransactions;
	}

	/**
	 * Yields the name of the hashing algorithm used for the deadlines.
	 * 
	 * @return the name of the hashing algorithm for the deadlines
	 */
	public String getHashingForDeadlines() {
		return hashingForDeadlines;
	}

	/**
	 * Yields the name of the hashing algorithm used for the generation signatures.
	 * 
	 * @return the name of the hashing algorithm for the generation signatures
	 */
	public String getHashingForGenerations() {
		return hashingForGenerations;
	}

	/**
	 * Yields the name of the signature algorithm used for signing the blocks.
	 * 
	 * @return the name of the signature algorithm
	 */
	public String getSignatureForBlocks() {
		return signatureForBlocks;
	}

	/**
	 * Yields the public key used for signing the block, in Base58 format.
	 * 
	 * @return the Base58-encoded public key
	 */
	public String getPublicKey() {
		return publicKey;
	}

	@Override
	public BlockDescription unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		return AbstractBlockDescription.from(this);
	}
}
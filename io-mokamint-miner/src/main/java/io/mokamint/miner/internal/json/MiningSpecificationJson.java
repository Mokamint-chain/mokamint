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

package io.mokamint.miner.internal.json;

import java.security.NoSuchAlgorithmException;

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.internal.MiningSpecificationImpl;

/**
 * The JSON representation of a {@link MiningSpecification}.
 */
public abstract class MiningSpecificationJson implements JsonRepresentation<MiningSpecification> {
	private final String name;
	private final String description;
	private final String chainId;
	private final String hashingForDeadlines;
	private final String signatureForBlocks;
	private final String signatureForDeadlines;
	private final String publicKeyForSigningBlocksBase58;

	protected MiningSpecificationJson(MiningSpecification spec) {
		this.name = spec.getName();
		this.description = spec.getDescription();
		this.chainId = spec.getChainId();
		this.hashingForDeadlines = spec.getHashingForDeadlines().getName();
		this.signatureForBlocks = spec.getSignatureForBlocks().getName();
		this.signatureForDeadlines = spec.getSignatureForDeadlines().getName();
		this.publicKeyForSigningBlocksBase58 = spec.getPublicKeyForSigningBlocksBase58();
	}

	/**
	 * Yields the name of the chain.
	 * 
	 * @return the name of the chain
	 */
	public String getName() {
		return name;
	}

	/**
	 * Yields the description of the chain.
	 * 
	 * @return the description of the chain
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Yields the chain identifier.
	 * 
	 * @return the chain identifier
	 */
	public String getChainId() {
		return chainId;
	}

	/**
	 * Yields the name of the hashing algorithm used for deadlines.
	 * 
	 * @return the name of the hashing algorithm used for deadlines
	 */
	public String getHashingForDeadlines() {
		return hashingForDeadlines;
	}

	/**
	 * Yields the name of the signature algorithm used for the key identifying the node
	 * in the deadlines expected by a miner having this specification.
	 * 
	 * @return the name of the signature algorithm
	 */
	public String getSignatureForBlocks() {
		return signatureForBlocks;
	}

	/**
	 * Yields the name of the signature algorithm used for the key identifying the plot
	 * containing the deadlines expected by a miner having this specification.
	 * 
	 * @return the name of the signature algorithm
	 */
	public String getSignatureForDeadlines() {
		return signatureForDeadlines;
	}

	/**
	 * Yields the Base58-encoded public key identifying the node in the deadlines
	 * expected by a miner having this specification. This is a public key for the
	 * {@link #getSignatureForBlocks()} algorithm.
	 * 
	 * @return the Base58-encoded public key
	 */
	public String getPublicKeyForSigningBlocksBase58() {
		return publicKeyForSigningBlocksBase58;
	}

	@Override
	public MiningSpecification unmap() throws InconsistentJsonException, NoSuchAlgorithmException {
		return new MiningSpecificationImpl(this);
	}
}
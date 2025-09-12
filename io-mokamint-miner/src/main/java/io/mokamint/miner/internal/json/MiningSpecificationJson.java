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

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getChainId() {
		return chainId;
	}

	public String getHashingForDeadlines() {
		return hashingForDeadlines;
	}

	public String getSignatureForBlocks() {
		return signatureForBlocks;
	}

	public String getSignatureForDeadlines() {
		return signatureForDeadlines;
	}

	public String getPublicKeyForSigningBlocksBase58() {
		return publicKeyForSigningBlocksBase58;
	}

	@Override
	public MiningSpecification unmap() throws InconsistentJsonException, NoSuchAlgorithmException {
		return new MiningSpecificationImpl(this);
	}
}
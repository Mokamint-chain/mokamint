/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.miner.internal;

import io.hotmoka.annotations.Immutable;
import io.mokamint.miner.api.MiningSpecification;

/**
 * Implementation of the specification of the mining parameter for the deadlines expected by a miner.
 */
@Immutable
public class MiningSpecificationImpl implements MiningSpecification {

	/**
	 * The chain id of the deadlines expected by a miner having this specification.
	 */
	private final String chainId;

	/**
	 * Creates the mining specification for a miner.
	 * 
	 * @param chainId the chain id of the deadlines expected by the miner
	 */
	public MiningSpecificationImpl(String chainId) {
		this.chainId = chainId;
	}

	@Override
	public String getChainId() {
		return chainId;
	}
}
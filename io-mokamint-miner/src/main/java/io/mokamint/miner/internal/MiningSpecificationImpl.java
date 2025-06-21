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
import io.hotmoka.exceptions.ExceptionSupplier;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.internal.json.MiningSpecificationJson;

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
		this(chainId, IllegalArgumentException::new);
	}

	/**
	 * Creates a mining specification object from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public MiningSpecificationImpl(MiningSpecificationJson json) throws InconsistentJsonException {
		this(json.getChainId(), InconsistentJsonException::new);
	}

	/**
	 * Builds a mining specification for a miner.
	 * 
	 * @param chainId the chain id of the deadlines expected by the miner
	 * @param onIllegalArgs the generator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> MiningSpecificationImpl(String chainId, ExceptionSupplier<? extends E> onIllegalArgs) throws E {
		this.chainId = Objects.requireNonNull(chainId, onIllegalArgs);
	}

	@Override
	public String getChainId() {
		return chainId;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof MiningSpecification ms && chainId.equals(ms.getChainId());
	}

	@Override
	public int hashCode() {
		return chainId.hashCode();
	}
}
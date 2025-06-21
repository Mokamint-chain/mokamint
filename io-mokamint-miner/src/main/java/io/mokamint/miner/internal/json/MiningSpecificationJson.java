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

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.internal.MiningSpecificationImpl;

/**
 * The JSON representation of a {@link MiningSpecification}.
 */
public abstract class MiningSpecificationJson implements JsonRepresentation<MiningSpecification> {
	private final String chainId;

	protected MiningSpecificationJson(MiningSpecification spec) {
		this.chainId = spec.getChainId();
	}

	public String getChainId() {
		return chainId;
	}

	@Override
	public MiningSpecification unmap() throws InconsistentJsonException {
		return new MiningSpecificationImpl(this);
	}
}
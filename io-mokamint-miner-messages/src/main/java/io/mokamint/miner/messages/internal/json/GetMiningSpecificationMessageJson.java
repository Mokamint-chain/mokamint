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

package io.mokamint.miner.messages.internal.json;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.miner.messages.api.GetMiningSpecificationMessage;
import io.mokamint.miner.messages.internal.GetMiningSpecificationMessageImpl;

/**
 * The JSON representation of an {@link GetMiningSpecificationMessage}.
 */
public abstract class GetMiningSpecificationMessageJson extends AbstractRpcMessageJsonRepresentation<GetMiningSpecificationMessage> {

	protected GetMiningSpecificationMessageJson(GetMiningSpecificationMessage message) {
		super(message);
	}

	@Override
	public GetMiningSpecificationMessage unmap() throws InconsistentJsonException {
		return new GetMiningSpecificationMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return GetMiningSpecificationMessage.class.getName();
	}
}
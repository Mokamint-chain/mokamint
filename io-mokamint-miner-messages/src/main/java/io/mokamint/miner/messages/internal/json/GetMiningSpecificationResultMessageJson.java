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

import java.security.NoSuchAlgorithmException;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.miner.MiningSpecifications;
import io.mokamint.miner.messages.api.GetMiningSpecificationResultMessage;
import io.mokamint.miner.messages.internal.GetMiningSpecificationResultMessageImpl;

/**
 * The JSON representation of a {@link GetMiningSpecificationResultMessage}.
 */
public abstract class GetMiningSpecificationResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetMiningSpecificationResultMessage> {
	private final MiningSpecifications.Json result;

	protected GetMiningSpecificationResultMessageJson(GetMiningSpecificationResultMessage message) {
		super(message);

		this.result = new MiningSpecifications.Json(message.get());
	}

	public final MiningSpecifications.Json getResult() {
		return result;
	}

	@Override
	public GetMiningSpecificationResultMessage unmap() throws InconsistentJsonException, NoSuchAlgorithmException {
		return new GetMiningSpecificationResultMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return GetMiningSpecificationResultMessage.class.getName();
	}
}
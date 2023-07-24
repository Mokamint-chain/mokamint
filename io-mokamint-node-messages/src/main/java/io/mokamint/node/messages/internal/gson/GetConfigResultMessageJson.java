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

package io.mokamint.node.messages.internal.gson;

import java.security.NoSuchAlgorithmException;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.node.ConsensusConfigs;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.api.GetConfigResultMessage;

/**
 * The JSON representation of a {@link GetConfigResultMessage}.
 */
public abstract class GetConfigResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetConfigResultMessage> {
	private ConsensusConfigs.Json config;

	protected GetConfigResultMessageJson(GetConfigResultMessage message) {
		super(message);

		this.config = new ConsensusConfigs.Json(message.get());
	}

	@Override
	public GetConfigResultMessage unmap() throws NoSuchAlgorithmException {
		return GetConfigResultMessages.of(config.unmap(), getId());
	}

	@Override
	protected String getExpectedType() {
		return GetConfigResultMessage.class.getName();
	}
}
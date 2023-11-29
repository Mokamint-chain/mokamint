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

import java.util.Optional;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.node.MinerInfos;
import io.mokamint.node.messages.OpenMinerResultMessages;
import io.mokamint.node.messages.api.OpenMinerResultMessage;

/**
 * The JSON representation of a {@link OpenMinerResultMessage}.
 */
public abstract class OpenMinerResultMessageJson extends AbstractRpcMessageJsonRepresentation<OpenMinerResultMessage> {
	private final MinerInfos.Json info;

	protected OpenMinerResultMessageJson(OpenMinerResultMessage message) {
		super(message);

		this.info = message.get().map(MinerInfos.Json::new).orElse(null);
	}

	@Override
	public OpenMinerResultMessage unmap() {
		return OpenMinerResultMessages.of(Optional.ofNullable(info == null ? null : info.unmap()), getId());
	}

	@Override
	protected String getExpectedType() {
		return OpenMinerResultMessage.class.getName();
	}
}
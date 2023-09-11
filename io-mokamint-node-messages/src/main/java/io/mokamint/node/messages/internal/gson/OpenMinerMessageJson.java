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

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.node.messages.OpenMinerMessages;
import io.mokamint.node.messages.api.OpenMinerMessage;

/**
 * The JSON representation of an {@link OpenMinerMessage}.
 */
public abstract class OpenMinerMessageJson extends AbstractRpcMessageJsonRepresentation<OpenMinerMessage> {
	private int port;

	protected OpenMinerMessageJson(OpenMinerMessage message) {
		super(message);

		this.port = message.getPort();
	}

	@Override
	public OpenMinerMessage unmap() {
		return OpenMinerMessages.of(port, getId());
	}

	@Override
	protected String getExpectedType() {
		return OpenMinerMessage.class.getName();
	}
}
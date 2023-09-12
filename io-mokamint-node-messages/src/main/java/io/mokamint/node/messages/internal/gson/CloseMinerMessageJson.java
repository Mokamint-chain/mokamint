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

import java.util.UUID;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.node.messages.CloseMinerMessages;
import io.mokamint.node.messages.api.CloseMinerMessage;

/**
 * The JSON representation of an {@link CloseMinerMessage}.
 */
public abstract class CloseMinerMessageJson extends AbstractRpcMessageJsonRepresentation<CloseMinerMessage> {
	private String uuid;

	protected CloseMinerMessageJson(CloseMinerMessage message) {
		super(message);

		this.uuid = message.getUUID().toString();
	}

	@Override
	public CloseMinerMessage unmap() {
		return CloseMinerMessages.of(UUID.fromString(uuid), getId());
	}

	@Override
	protected String getExpectedType() {
		return CloseMinerMessage.class.getName();
	}
}
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
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.messages.RemoveMinerMessages;
import io.mokamint.node.messages.api.RemoveMinerMessage;

/**
 * The JSON representation of a {@link RemoveMinerMessage}.
 */
public abstract class RemoveMinerMessageJson extends AbstractRpcMessageJsonRepresentation<RemoveMinerMessage> {
	private final String uuid;

	protected RemoveMinerMessageJson(RemoveMinerMessage message) {
		super(message);

		this.uuid = message.getUUID().toString();
	}

	@Override
	public RemoveMinerMessage unmap() throws InconsistentJsonException {
		try {
			return RemoveMinerMessages.of(UUID.fromString(uuid), getId());
		}
		catch (IllegalArgumentException e) {
			throw new InconsistentJsonException(e);
		}
	}

	@Override
	protected String getExpectedType() {
		return RemoveMinerMessage.class.getName();
	}
}
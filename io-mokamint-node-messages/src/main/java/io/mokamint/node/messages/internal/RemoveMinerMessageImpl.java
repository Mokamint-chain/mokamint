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

package io.mokamint.node.messages.internal;

import java.util.Objects;
import java.util.UUID;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.node.api.RestrictedNode;
import io.mokamint.node.messages.api.RemoveMinerMessage;

/**
 * Implementation of the network message corresponding to {@link RestrictedNode#removeMiner(java.util.UUID)}.
 */
public class RemoveMinerMessageImpl extends AbstractRpcMessage implements RemoveMinerMessage {

	private final UUID uuid;

	/**
	 * Creates the message.
	 * 
	 * @param uuid the UUID of the miner that must be closed
	 * @param id the identifier of the message
	 */
	public RemoveMinerMessageImpl(UUID uuid, String id) {
		super(id);

		this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof RemoveMinerMessage rmm && super.equals(other) && uuid.equals(rmm.getUUID());
	}

	@Override
	protected String getExpectedType() {
		return RemoveMinerMessage.class.getName();
	}
}
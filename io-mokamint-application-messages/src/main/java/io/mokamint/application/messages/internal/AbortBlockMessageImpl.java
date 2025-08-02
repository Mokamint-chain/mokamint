/*
Copyright 2024 Fausto Spoto

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

package io.mokamint.application.messages.internal;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.AbortBlockMessage;
import io.mokamint.application.messages.internal.json.AbortBlockMessageJson;

/**
 * Implementation of the network message corresponding to {@link Application#abortBlock(int)}.
 */
public class AbortBlockMessageImpl extends AbstractRpcMessage implements AbortBlockMessage {
	private final int groupId;

	/**
	 * Creates the message.
	 * 
	 * @param groupId the identifier of the group of transactions in the message
	 * @param id the identifier of the message
	 */
	public AbortBlockMessageImpl(int groupId, String id) {
		super(id);

		this.groupId = groupId;
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 */
	public AbortBlockMessageImpl(AbortBlockMessageJson json) {
		super (json.getId());

		this.groupId = json.getGroupId();
	}

	@Override
	public int getGroupId() {
		return groupId;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof AbortBlockMessage abm && super.equals(other) && groupId == abm.getGroupId();
	}

	@Override
	protected String getExpectedType() {
		return AbortBlockMessage.class.getName();
	}
}
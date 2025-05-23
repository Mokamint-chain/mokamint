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

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.node.api.RestrictedNode;
import io.mokamint.node.messages.api.RemoveMinerResultMessage;
import io.mokamint.node.messages.internal.gson.RemoveMinerResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link RestrictedNode#removeMiner(java.util.UUID)} method.
 */
public class RemoveMinerResultMessageImpl extends AbstractRpcMessage implements RemoveMinerResultMessage {

	/**
	 * The result of the call.
	 */
	private final boolean result;

	/**
	 * Creates the message.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 */
	public RemoveMinerResultMessageImpl(boolean result, String id) {
		super(id);

		this.result = result;
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 */
	public RemoveMinerResultMessageImpl(RemoveMinerResultMessageJson json) {
		super(json.getId());

		this.result = json.getResult();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof RemoveMinerResultMessage cmrm && super.equals(other) && result == cmrm.get().booleanValue();
	}

	@Override
	protected String getExpectedType() {
		return RemoveMinerResultMessage.class.getName();
	}

	@Override
	public Boolean get() {
		return result;
	}
}
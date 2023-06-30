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
import io.mokamint.node.messages.RemovePeerResultMessage;

/**
 * Implementation of the network message corresponding to the {@link RestrictedNode#removePeer(io.mokamint.node.api.Peer)} method of a node.
 */
public class RemovePeerResultMessageImpl extends AbstractRpcMessage implements RemovePeerResultMessage {

	/**
	 * Creates the message.
	 * 
	 * @param id the identifier of the message
	 */
	public RemovePeerResultMessageImpl(String id) {
		super(id);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof RemovePeerResultMessage && super.equals(other);
	}

	@Override
	protected String getExpectedType() {
		return RemovePeerResultMessage.class.getName();
	}
}
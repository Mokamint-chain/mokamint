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

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.RestrictedNode;
import io.mokamint.node.messages.api.RemovePeerMessage;
import io.mokamint.node.messages.internal.json.RemovePeerMessageJson;

/**
 * Implementation of the network message corresponding to {@link RestrictedNode#remove(Peer)}.
 */
public class RemovePeerMessageImpl extends AbstractRpcMessage implements RemovePeerMessage {

	private final Peer peer;

	/**
	 * Creates the message.
	 * 
	 * @param peer the peer to remove
	 * @param id the identifier of the message
	 */
	public RemovePeerMessageImpl(Peer peer, String id) {
		super(id);

		this.peer = Objects.requireNonNull(peer, "peer cannot be null");
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public RemovePeerMessageImpl(RemovePeerMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		var peer = json.getPeer();
		if (peer == null)
			throw new InconsistentJsonException("peer cannot be null");

		this.peer = peer.unmap();
	}

	@Override
	public Peer getPeer() {
		return peer;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof RemovePeerMessage rpm && super.equals(other) && peer.equals(rpm.getPeer());
	}

	@Override
	protected String getExpectedType() {
		return RemovePeerMessage.class.getName();
	}
}
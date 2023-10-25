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

import java.net.URISyntaxException;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.node.Peers;
import io.mokamint.node.messages.RemovePeerMessages;
import io.mokamint.node.messages.api.RemovePeerMessage;

/**
 * The JSON representation of a {@link RemovePeerMessage}.
 */
public abstract class RemovePeerMessageJson extends AbstractRpcMessageJsonRepresentation<RemovePeerMessage> {
	private final Peers.Json peer;

	protected RemovePeerMessageJson(RemovePeerMessage message) {
		super(message);

		this.peer = new Peers.Json(message.getPeer());
	}

	@Override
	public RemovePeerMessage unmap() throws URISyntaxException {
		return RemovePeerMessages.of(peer.unmap(), getId());
	}

	@Override
	protected String getExpectedType() {
		return RemovePeerMessage.class.getName();
	}
}
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
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.Peers;
import io.mokamint.node.messages.api.WhisperPeerMessage;
import io.mokamint.node.messages.internal.WhisperPeerMessageImpl;

/**
 * The JSON representation of an {@link WhisperPeerMessage}.
 */
public abstract class WhisperPeerMessageJson extends AbstractRpcMessageJsonRepresentation<WhisperPeerMessage> {
	private final Peers.Json peer;

	protected WhisperPeerMessageJson(WhisperPeerMessage message) {
		super(message);

		this.peer = new Peers.Json(message.getWhispered());
	}

	public Peers.Json getPeer() {
		return peer;
	}

	@Override
	public WhisperPeerMessage unmap() throws InconsistentJsonException {
		return new WhisperPeerMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return WhisperPeerMessage.class.getName();
	}
}
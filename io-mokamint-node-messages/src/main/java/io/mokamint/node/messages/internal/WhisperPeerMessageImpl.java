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
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.api.WhisperPeerMessage;

/**
 * Implementation of the network message sent to whisper a peer between whisperers.
 */
public class WhisperPeerMessageImpl extends AbstractRpcMessage implements WhisperPeerMessage {

	/**
	 * The whispered peer.
	 */
	private final Peer peer;

	/**
	 * Creates the message.
	 * 
	 * @param peer the whispered peer
	 * @param id the identifier of the message
	 */
	public WhisperPeerMessageImpl(Peer peer, String id) {
		super(id);

		Objects.requireNonNull(peer);
		this.peer = peer;
	}

	@Override
	public Peer getPeer() {
		return peer;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof WhisperPeerMessage wpm && super.equals(other) && peer.equals(wpm.getPeer());
	}

	@Override
	protected String getExpectedType() {
		return WhisperPeerMessage.class.getName();
	}
}
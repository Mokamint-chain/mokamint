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

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.SuggestPeersMessage;

/**
 * Implementation of the network message corresponding to {@link PublicNode#suggestPeers(java.util.stream.Stream)}.
 */
public class SuggestPeersMessageImpl extends AbstractRpcMessage implements SuggestPeersMessage {

	private final Peer[] peers;

	/**
	 * Creates the message.
	 * 
	 * @param peers the peers suggsted for addition
	 * @param id the identifier of the message
	 */
	public SuggestPeersMessageImpl(Stream<Peer> peers, String id) {
		super(id);

		this.peers = peers.map(Objects::requireNonNull).toArray(Peer[]::new);
	}

	@Override
	public Stream<Peer> getPeers() {
		return Stream.of(peers);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof SuggestPeersMessage && Arrays.equals(peers, ((SuggestPeersMessage) other).getPeers().toArray(Peer[]::new));
	}

	@Override
	protected String getExpectedType() {
		return SuggestPeersMessage.class.getName();
	}
}
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

import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.SuggestPeersMessage;

/**
 * Implementation of the network message sent by a public node service
 * to the connected remote nodes, to signify that a new peer has been added to
 * the serviced node.
 */
public class SuggestPeersMessageImpl implements SuggestPeersMessage {

	private final Peer[] peers;

	/**
	 * Creates the message.
	 * 
	 * @param peers the peers suggested for addition
	 */
	public SuggestPeersMessageImpl(Stream<Peer> peers) {
		this.peers = peers.map(Objects::requireNonNull).toArray(Peer[]::new);
	}

	@Override
	public Stream<Peer> getPeers() {
		return Stream.of(peers);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof SuggestPeersMessage spm && Arrays.equals(peers, spm.getPeers().toArray(Peer[]::new));
	}
}
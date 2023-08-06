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
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.api.GetPeerInfosResultMessage;

/**
 * Implementation of the network message corresponding to the result of the {@link PublicNode#getPeerInfos()} method.
 */
public class GetPeerInfosResultMessageImpl extends AbstractRpcMessage implements GetPeerInfosResultMessage {

	private final PeerInfo[] peers;

	/**
	 * Creates the message.
	 * 
	 * @param peers the peers in the message
	 * @param id the identifier of the message
	 */
	public GetPeerInfosResultMessageImpl(Stream<PeerInfo> peers, String id) {
		super(id);

		this.peers = peers
			.map(Objects::requireNonNull)
			.toArray(PeerInfo[]::new);
	}

	@Override
	public Stream<PeerInfo> get() {
		return Stream.of(peers);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetPeerInfosResultMessage gprm && super.equals(other) && Arrays.equals(peers, gprm.get().toArray(PeerInfo[]::new));
	}

	@Override
	protected String getExpectedType() {
		return GetPeerInfosResultMessage.class.getName();
	}
}
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

import java.util.stream.Stream;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.messages.GetPeerInfosResultMessages;
import io.mokamint.node.messages.api.GetPeerInfosResultMessage;

/**
 * The JSON representation of a {@link GetPeerInfosResultMessage}.
 */
public abstract class GetPeerInfosResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetPeerInfosResultMessage> {
	private final PeerInfos.Json[] peers;

	protected GetPeerInfosResultMessageJson(GetPeerInfosResultMessage message) {
		super(message);

		this.peers = message.get().map(PeerInfos.Json::new).toArray(PeerInfos.Json[]::new);
	}

	@Override
	public GetPeerInfosResultMessage unmap() throws InconsistentJsonException {
		var peerInfos = new PeerInfo[peers.length];
		for (int pos = 0; pos < peers.length; pos++)
			peerInfos[pos] = peers[pos].unmap();

		return GetPeerInfosResultMessages.of(Stream.of(peerInfos), getId());
	}

	@Override
	protected String getExpectedType() {
		return GetPeerInfosResultMessage.class.getName();
	}
}
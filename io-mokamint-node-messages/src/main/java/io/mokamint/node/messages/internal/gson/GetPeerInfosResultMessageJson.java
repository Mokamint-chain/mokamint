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

import static io.hotmoka.exceptions.CheckSupplier.check;
import static io.hotmoka.exceptions.UncheckFunction.uncheck;

import java.net.URISyntaxException;
import java.util.stream.Stream;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.messages.GetPeerInfosResultMessages;
import io.mokamint.node.messages.api.GetPeerInfosResultMessage;

/**
 * The JSON representation of a {@link GetPeerInfosResultMessage}.
 */
public abstract class GetPeerInfosResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetPeerInfosResultMessage> {
	private PeerInfos.Json[] peers;

	protected GetPeerInfosResultMessageJson(GetPeerInfosResultMessage message) {
		super(message);

		this.peers = message.get().map(PeerInfos.Json::new).toArray(PeerInfos.Json[]::new);
	}

	@Override
	public GetPeerInfosResultMessage unmap() throws URISyntaxException {
		// using PeerInfos.Json::unmap below leads to a run-time error in the JVM!
		return check(URISyntaxException.class, () -> GetPeerInfosResultMessages.of(Stream.of(peers).map(uncheck(peer -> peer.unmap())), getId()));
	}

	@Override
	protected String getExpectedType() {
		return GetPeerInfosResultMessage.class.getName();
	}
}
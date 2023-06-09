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

import io.hotmoka.websockets.beans.JsonRepresentation;
import io.mokamint.node.Peers;
import io.mokamint.node.messages.SuggestPeersMessage;
import io.mokamint.node.messages.SuggestPeersMessages;

/**
 * The JSON representation of an {@link SuggestPeersMessage}.
 */
public abstract class SuggestPeersMessageJson implements JsonRepresentation<SuggestPeersMessage> {
	private Peers.Json[] peers;

	protected SuggestPeersMessageJson(SuggestPeersMessage message) {
		this.peers = message.getPeers().map(Peers.Json::new).toArray(Peers.Json[]::new);
	}

	@Override
	public SuggestPeersMessage unmap() throws URISyntaxException {
		// using Peers.Json::unmap below leads to a run-time error in the JVM!
		return check(URISyntaxException.class, () -> SuggestPeersMessages.of(Stream.of(peers).map(uncheck(peer -> peer.unmap()))));
	}
}
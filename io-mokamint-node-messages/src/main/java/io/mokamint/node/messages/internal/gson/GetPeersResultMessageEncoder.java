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

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.hotmoka.websockets.beans.BaseEncoder;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.GetPeersResultMessage;
import io.mokamint.node.messages.GetPeersResultMessages;

/**
 * An encoder of {@code GetPeersResultMessage}.
 */
public class GetPeersResultMessageEncoder extends BaseEncoder<GetPeersResultMessage> {

	public GetPeersResultMessageEncoder() {
		super(GetPeersResultMessage.class);
	}

	@Override
	public Supplier<GetPeersResultMessage> map(GetPeersResultMessage message) {
		return new Json(message);
	}

	private static class Json implements Supplier<GetPeersResultMessage> {
		@SuppressWarnings("rawtypes")
		private List<Supplier> peers;
	
		private Json(GetPeersResultMessage message) {
			var encoder = new Peers.Encoder();
			this.peers = message.get().map(encoder::map).collect(Collectors.toList());
		}

		@Override
		public GetPeersResultMessage get() {
			return GetPeersResultMessages.of(peers.stream().map(Supplier::get).map(p -> (Peer) p));
		}
	}
}
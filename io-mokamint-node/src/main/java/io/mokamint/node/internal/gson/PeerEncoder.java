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

package io.mokamint.node.internal.gson;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Supplier;

import io.hotmoka.websockets.beans.BaseEncoder;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;

public class PeerEncoder extends BaseEncoder<Peer> {

	public PeerEncoder() {
		super(Peer.class);
	}

	@Override
	public Supplier<Peer> map(Peer peer) {
		return new Json(peer);
	}

	private static class Json implements Supplier<Peer> {
		private String uri;
	
		private Json(Peer peer) {
			this.uri = peer.getURI().toString();
		}

		@Override
		public Peer get() {
			try {
				return Peers.of(new URI(uri));
			}
			catch (URISyntaxException e) {
				throw new IllegalArgumentException(e);
			}
		}
	}
}
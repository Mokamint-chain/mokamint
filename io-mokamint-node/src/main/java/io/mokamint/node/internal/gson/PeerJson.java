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

import io.hotmoka.websockets.beans.JsonRepresentation;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;

/**
 * The JSON representation of a {@code Peer}.
 */
public abstract class PeerJson implements JsonRepresentation<Peer> {
	private String uri;

	protected PeerJson(Peer peer) {
		this.uri = peer.getURI().toString();
	}

	@Override
	public Peer unmap() throws URISyntaxException {
		return Peers.of(new URI(uri));
	}
}
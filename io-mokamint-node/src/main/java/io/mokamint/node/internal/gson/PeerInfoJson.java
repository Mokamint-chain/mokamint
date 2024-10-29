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

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.api.PeerInfo;

/**
 * The JSON representation of a {@link PeerInfo}.
 */
public abstract class PeerInfoJson implements JsonRepresentation<PeerInfo> {
	private final Peers.Json peer;
	private final long points;
	private final boolean connected;

	protected PeerInfoJson(PeerInfo info) {
		this.peer = new Peers.Json(info.getPeer());
		this.points = info.getPoints();
		this.connected = info.isConnected();
	}

	@Override
	public PeerInfo unmap() throws InconsistentJsonException {
		try {
			return PeerInfos.of(peer.unmap(), points, connected);
		}
		catch (NullPointerException | IllegalArgumentException e) {
			throw new InconsistentJsonException(e);
		}
	}
}
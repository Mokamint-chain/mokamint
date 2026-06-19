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

package io.mokamint.node.internal.json;

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.Peers;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.internal.PeerInfoImpl;

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

	/**
	 * Yields the peer described by this information.
	 * 
	 * @return the peer
	 */
	public Peers.Json getPeer() {
		return peer;
	}

	/**
	 * Yields the points of the peer. They are an estimation of how much well the
	 * peer behaved recently.
	 *
	 * @return the points; this should always be positive
	 */
	public long getPoints() {
		return points;
	}

	/**
	 * Yields the connection status of the peer.
	 * 
	 * @return true if and only if the node is connected to the peer
	 */
	public boolean isConnected() {
		return connected;
	}

	@Override
	public PeerInfo unmap() throws InconsistentJsonException {
		return new PeerInfoImpl(this);
	}
}
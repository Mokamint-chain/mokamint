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

package io.mokamint.node.internal;

import java.util.function.Function;

import io.hotmoka.annotations.Immutable;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;

/**
 * An implementation of peer information.
 */
@Immutable
public class PeerInfoImpl implements PeerInfo {

	/**
	 * The peer.
	 */
	private final Peer peer;

	/**
	 * The points of the peer.
	 */
	private final long points;

	/**
	 * True if and only if the peer is currently connected.
	 */
	private final boolean connected;

	/**
	 * Creates a peer information object.
	 * 
	 * @param peer the peer described by the peer information
	 * @param points the points of the peer
	 * @param connected the connection status of the peer
	 */
	public PeerInfoImpl(Peer peer, long points, boolean connected) {
		this(peer, points, connected, NullPointerException::new, IllegalArgumentException::new);
	}

	/**
	 * Creates a peer information object.
	 * 
	 * @param peer the peer described by the peer information
	 * @param points the points of the peer
	 * @param connected the connection status of the peer
	 * @param onNull the generator of the exception to throw if some argument is {@code null}
	 * @param onIllegal the generator of the exception to throw if some argument has an illegal value
	 * @throws ON_NULL if some argument is {@code null}
	 * @throws ON_ILLEGAL if some argument has an illegal value
	 */
	public <ON_NULL extends Exception, ON_ILLEGAL extends Exception> PeerInfoImpl(Peer peer, long points, boolean connected, Function<String, ON_NULL> onNull, Function<String, ON_ILLEGAL> onIllegal) throws ON_NULL, ON_ILLEGAL {
		if (points <= 0)
			throw onIllegal.apply("points must be positive");

		if (peer == null)
			throw onNull.apply("peer cannot be null");

		this.peer = peer;
		this.points = points;
		this.connected = connected;
	}

	@Override
	public Peer getPeer() {
		return peer;
	}

	@Override
	public long getPoints() {
		return points;
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof PeerInfo info &&
			peer.equals(info.getPeer()) &&
			points == info.getPoints() &&
			connected == info.isConnected();
	}

	@Override
	public int hashCode() {
		return peer.hashCode() ^ Long.hashCode(points);
	}

	@Override
	public int compareTo(PeerInfo other) {
		int diff = -Boolean.compare(connected, other.isConnected());
		if (diff != 0)
			return diff;

		diff = -Long.compare(points, other.getPoints());
		if (diff != 0)
			return diff;

		return peer.compareTo(other.getPeer());
	}

	@Override
	public String toString() {
		return peer + ", points = " + points + ", connected: " + connected;
	}
}
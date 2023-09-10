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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.UnmarshallingContexts;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;

/**
 * An implementation of peer information.
 */
@Immutable
public class PeerInfoImpl extends AbstractMarshallable implements PeerInfo {
	private final Peer peer;
	private final long points;
	private final boolean connected;

	/**
	 * Creates a peer information object.
	 * 
	 * @param peer the peer described by the peer information
	 * @param points the points of the peer
	 * @param connected the connection status of the peer
	 */
	public PeerInfoImpl(Peer peer, long points, boolean connected) {
		Objects.requireNonNull(peer);
		if (points <= 0)
			throw new IllegalArgumentException("points must be positive");

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
		return peer.hashCode() ^ (int) points;
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

	@Override
	public void into(MarshallingContext context) throws IOException {
		peer.into(context);
		context.writeLong(points);
		context.writeBoolean(connected);
	}

	/**
	 * Unmarshals a peer information object from the given bytes.
	 * 
	 * @param bytes the bytes
	 * @return the peer information
	 * @throws IOException if the peer information cannot be unmarshalled
	 * @throws URISyntaxException if the bytes contain a URI with illegal syntax
	 */
	public static PeerInfoImpl from(byte[] bytes) throws IOException, URISyntaxException {
		try (var bais = new ByteArrayInputStream(bytes); var context = UnmarshallingContexts.of(bais)) {
			return from(context);
		}
	}

	/**
	 * Unmarshals a peer information object from the given context.
	 * 
	 * @param context the context
	 * @return the peer information
	 * @throws IOException if the peer information cannot be unmarshalled
	 * @throws URISyntaxException if the context contains a URI with illegal syntax
	 */
	public static PeerInfoImpl from(UnmarshallingContext context) throws IOException, URISyntaxException {
		return new PeerInfoImpl(Peers.from(context), context.readLong(), context.readBoolean());
	}
}
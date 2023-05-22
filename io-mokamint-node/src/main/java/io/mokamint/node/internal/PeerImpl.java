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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Peer;

/**
 * An implementation of a peer.
 */
@Immutable
public class PeerImpl extends AbstractMarshallable implements Peer {
	private final URI uri;

	/**
	 * Creates a peer with the given URI.
	 * 
	 * @param uri the URI of the peer
	 */
	public PeerImpl(URI uri) {
		if (uri == null)
			throw new NullPointerException();

		this.uri = uri;
	}

	@Override
	public URI getURI() {
		return uri;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Peer && uri.equals(((Peer) other).getURI());
	}

	@Override
	public int hashCode() {
		return uri.hashCode();
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		context.writeUTF(uri.toString());
	}

	@Override
	public int compareTo(Peer other) {
		return uri.compareTo(other.getURI());
	}

	@Override
	public String toString() {
		return uri.toString();
	}

	/**
	 * Unmarshals a peer from the given context.
	 * 
	 * @param context the context
	 * @return the peer
	 * @throws IOException if the peer cannot be unmarshalled
	 * @throws URISyntaxException if the context contains a URI with illegal syntax
	 */
	public static PeerImpl from(UnmarshallingContext context) throws IOException, URISyntaxException {
		return new PeerImpl(new URI(context.readUTF()));
	}
}
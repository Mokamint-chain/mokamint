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
import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.internal.json.PeerJson;

/**
 * An implementation of a peer.
 */
@Immutable
public class PeerImpl extends AbstractMarshallable implements Peer {

	/**
	 * The URI of the peer.
	 */
	private final URI uri;

	/**
	 * Creates a peer with the given URI.
	 * 
	 * @param uri the URI of the peer
	 */
	public PeerImpl(URI uri) {
		this(Objects.requireNonNull(uri, "uri cannot be null", IllegalArgumentException::new).toString(), IllegalArgumentException::new);
	}

	/**
	 * Creates a peer from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public PeerImpl(PeerJson json) throws InconsistentJsonException {
		this(Objects.requireNonNull(json.getUri(), "uri cannot be null", InconsistentJsonException::new), InconsistentJsonException::new);
	}

	/**
	 * Unmarshals a peer from the given context.
	 * 
	 * @param context the context
	 * @return the peer
	 * @throws IOException if the peer cannot be unmarshalled
	 */
	public PeerImpl(UnmarshallingContext context) throws IOException {
		this(context.readStringUnshared(), IOException::new);
	}

	/**
	 * Creates the peer.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param uri the URI of the peer
	 * @param onIllegalArgs the creator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> PeerImpl(String uri, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		try {
			this.uri = new URI(Objects.requireNonNull(uri, "uri cannot be null", onIllegalArgs));
		}
		catch (URISyntaxException e) {
			throw onIllegalArgs.apply(e.getMessage());
		}
	}

	@Override
	public URI getURI() {
		return uri;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Peer peer && uri.equals(peer.getURI());
	}

	@Override
	public int hashCode() {
		return uri.hashCode();
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		context.writeStringUnshared(uri.toString());
	}

	@Override
	public int compareTo(Peer other) {
		return uri.compareTo(other.getURI());
	}

	@Override
	public String toString() {
		String uri = this.uri.toString();
		return uri.length() > 100 ? uri.substring(0, 100) + "..." : uri;
	}
}
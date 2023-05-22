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

package io.mokamint.node;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import io.hotmoka.marshalling.UnmarshallingContexts;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Peer;
import io.mokamint.node.internal.PeerDecoder;
import io.mokamint.node.internal.PeerEncoder;
import io.mokamint.node.internal.PeerImpl;

/**
 * Providers of peers.
 */
public interface Peers {

	/**
	 * Yields a peer with the given URI.
	 * 
	 * @param uri the URI of the peer
	 * @return the peer
	 */
	static Peer of(URI uri) {
		return new PeerImpl(uri);
	}

	/**
	 * Unmarshals a peer from the given bytes.
	 * 
	 * @param bytes the bytes
	 * @return the peer
	 * @throws IOException if the peer cannot be unmarshalled
	 * @throws URISyntaxException if the bytes contain a URI with illegal syntax
	 */
	static Peer from(byte[] bytes) throws IOException, URISyntaxException {
		try (var bais = new ByteArrayInputStream(bytes); var context = UnmarshallingContexts.of(bais)) {
			return PeerImpl.from(context);
		}
	}

	/**
	 * Unmarshals a peer from the given context.
	 * 
	 * @param context the context
	 * @return the peer
	 * @throws IOException if the peer cannot be unmarshalled
	 * @throws URISyntaxException if the bytes contain a URI with illegal syntax
	 */
	static Peer from(UnmarshallingContext context) throws IOException, URISyntaxException {
		return PeerImpl.from(context);
	}

	/**
	 * Gson encoder.
	 */
	static class Encoder extends PeerEncoder {}

	/**
	 * Gson decoder.
	 */
    static class Decoder extends PeerDecoder {}
}
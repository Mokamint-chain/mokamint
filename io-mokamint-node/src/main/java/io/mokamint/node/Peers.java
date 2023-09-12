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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Peer;
import io.mokamint.node.internal.PeerImpl;
import io.mokamint.node.internal.gson.PeerDecoder;
import io.mokamint.node.internal.gson.PeerEncoder;
import io.mokamint.node.internal.gson.PeerJson;

/**
 * Providers of peers.
 */
public abstract class Peers {

	private Peers() {}

	/**
	 * Yields a peer with the given URI.
	 * 
	 * @param uri the URI of the peer
	 * @return the peer
	 */
	public static Peer of(URI uri) {
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
	public static Peer from(byte[] bytes) throws IOException, URISyntaxException {
		return PeerImpl.from(bytes);
	}

	/**
	 * Unmarshals a peer from the given context.
	 * 
	 * @param context the context
	 * @return the peer
	 * @throws IOException if the peer cannot be unmarshalled
	 * @throws URISyntaxException if the bytes contain a URI with illegal syntax
	 */
	public static Peer from(UnmarshallingContext context) throws IOException, URISyntaxException {
		return PeerImpl.from(context);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends PeerEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends PeerDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
	public static class Json extends PeerJson {

    	/**
    	 * Creates the Json representation for the given peer.
    	 * 
    	 * @param peer the peer
    	 */
    	public Json(Peer peer) {
    		super(peer);
    	}
    }
}
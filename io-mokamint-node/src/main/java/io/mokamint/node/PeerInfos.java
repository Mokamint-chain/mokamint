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

import java.util.function.Function;

import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.internal.PeerInfoImpl;
import io.mokamint.node.internal.gson.PeerInfoDecoder;
import io.mokamint.node.internal.gson.PeerInfoEncoder;
import io.mokamint.node.internal.gson.PeerInfoJson;

/**
 * Providers of peer information objects.
 */
public abstract class PeerInfos {

	private PeerInfos() {}

	/**
	 * Yields peer information with the given data.
	 * 
	 * @param peer the peer described by the peer information
	 * @param points the points of the peer
	 * @param connected the connection status of the peer
	 * @return the peer information
	 */
	public static PeerInfo of(Peer peer, long points, boolean connected) {
		return new PeerInfoImpl(peer, points, connected);
	}

	/**
	 * Yields peer information with the given data.
	 * 
	 * @param peer the peer described by the peer information
	 * @param points the points of the peer
	 * @param connected the connection status of the peer
	 * @param onNull the generator of the exception to throw if some argument is {@code null}
	 * @param onIllegal the generator of the exception to throw if some argument has an illegal value
	 * @return the peer information
	 * @throws ON_NULL if some argument is {@code null}
	 * @throws ON_ILLEGAL if some argument has an illegal value
	 */
	public static <ON_NULL extends Exception, ON_ILLEGAL extends Exception> PeerInfo of(Peer peer, long points, boolean connected, Function<String, ON_NULL> onNull, Function<String, ON_ILLEGAL> onIllegal) throws ON_NULL, ON_ILLEGAL {
		return new PeerInfoImpl(peer, points, connected, onNull, onIllegal);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends PeerInfoEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends PeerInfoDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
	public static class Json extends PeerInfoJson {

    	/**
    	 * Creates the Json representation for the given peer information.
    	 * 
    	 * @param info the peer information
    	 */
    	public Json(PeerInfo info) {
    		super(info);
    	}
    }
}
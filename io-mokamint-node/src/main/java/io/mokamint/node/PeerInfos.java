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
import java.net.URISyntaxException;
import java.time.LocalDateTime;

import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.internal.PeerInfoImpl;
import io.mokamint.node.internal.gson.PeerInfoDecoder;
import io.mokamint.node.internal.gson.PeerInfoEncoder;
import io.mokamint.node.internal.gson.PeerInfoJson;

/**
 * Providers of peer informations.
 */
public abstract class PeerInfos {

	private PeerInfos() {}

	/**
	 * Yields peer information with the given data.
	 * 
	 * @param peer the peer described by the peer information
	 * @param points the points of the peer
	 * @param connected the connection status of the peer
	 * @param localDateTimeUTC the local date and time of the peer, in UTC
	 * @return the peer information
	 */
	public static PeerInfo of(Peer peer, long points, boolean connected, LocalDateTime localDateTimeUTC) {
		return new PeerInfoImpl(peer, points, connected, localDateTimeUTC);
	}

	/**
	 * Unmarshals a peer information object from the given bytes.
	 * 
	 * @param bytes the bytes
	 * @return the peer information object
	 * @throws IOException if the peer information cannot be unmarshalled
	 * @throws URISyntaxException if the bytes contain a URI with illegal syntax
	 */
	public static PeerInfo from(byte[] bytes) throws IOException, URISyntaxException {
		return PeerInfoImpl.from(bytes);
	}

	/**
	 * Unmarshals a peer information object from the given context.
	 * 
	 * @param context the context
	 * @return the peer information
	 * @throws IOException if the peer information cannot be unmarshalled
	 * @throws URISyntaxException if the bytes contain a URI with illegal syntax
	 */
	public static PeerInfo from(UnmarshallingContext context) throws IOException, URISyntaxException {
		return PeerInfoImpl.from(context);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends PeerInfoEncoder {}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends PeerInfoDecoder {}

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
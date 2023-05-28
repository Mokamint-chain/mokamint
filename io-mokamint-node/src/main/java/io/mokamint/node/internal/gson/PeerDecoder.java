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

import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.websockets.beans.AbstractDecoder;
import io.mokamint.node.api.Peer;
import jakarta.websocket.DecodeException;

/**
 * A decoder for {@link Peer}.
 */
public class PeerDecoder extends AbstractDecoder<Peer> {

	private final static Logger LOGGER = Logger.getLogger(PeerDecoder.class.getName());

	public PeerDecoder() {
		super(Peer.class);
	}

	@Override
	public Peer decode(String s) throws DecodeException {
		try {
			return gson.fromJson(s, PeerGsonHelper.class).toBean();
		}
		catch (Exception e) {
			LOGGER.log(Level.WARNING, "could not decode a Peer", e);
			throw new DecodeException(s, "could not decode a Peer", e);
		}
	}
}
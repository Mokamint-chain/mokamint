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

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import io.hotmoka.websockets.beans.BaseDecoder;
import io.hotmoka.websockets.beans.BaseDeserializer;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;

/**
 * A decoder for {@link Peer}.
 */
public class PeerDecoder extends BaseDecoder<Peer> {

	public PeerDecoder() {
		super(new PeerDeserializer());
	}

	private static class PeerDeserializer extends BaseDeserializer<Peer> {

		protected PeerDeserializer() {
			super(Peer.class);
		}

		@Override
		protected void registerTypeDeserializers(GsonBuilder where) {
			where.registerTypeAdapter(URI.class, new URIDeserializer());
		}

		@Override
		protected Peer deserialize(JsonElement json, Gson gson) throws JsonParseException {
			return gson.fromJson(json, PeerGsonHelper.class).toBean();
		}
	}

	private static class URIDeserializer implements JsonDeserializer<URI> {

		@Override
		public URI deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			try {
				return new URI(json.getAsString());
			}
			catch (URISyntaxException e) {
				throw new JsonParseException(e);
			}
		}
	}

	private static class PeerGsonHelper {
		private URI uri;

		private Peer toBean() {
			return Peers.of(uri);
	    }
	}
}
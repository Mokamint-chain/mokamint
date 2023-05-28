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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import io.hotmoka.websockets.beans.BaseEncoder;
import io.hotmoka.websockets.beans.BaseSerializer;
import io.mokamint.node.api.Peer;

public class PeerEncoder extends BaseEncoder<Peer> {

	public PeerEncoder() {
		super(new PeerSerializer());
	}

	private static class PeerSerializer extends BaseSerializer<Peer> {

		private PeerSerializer() {
			super(Peer.class);
		}

		@Override
		protected void registerTypeSerializers(GsonBuilder where) {
			where.registerTypeAdapter(URI.class, new URISerializer());
		}
	}

	private static class URISerializer implements JsonSerializer<URI> {

		@Override
		public JsonElement serialize(URI uri, Type type, JsonSerializationContext context) {
			// a URI is simply represented as its string representation
			return new JsonPrimitive(uri.toString());
		}
	}
}
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

package io.mokamint.node.messages.internal.gson;

import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import io.hotmoka.websockets.beans.BaseDecoder;
import io.hotmoka.websockets.beans.BaseDeserializer;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.GetPeersResultMessage;
import io.mokamint.node.messages.GetPeersResultMessages;

/**
 * A decoder for {@link GetPeersResultMessage}.
 */
public class GetPeersResultMessageDecoder extends BaseDecoder<GetPeersResultMessage> {

	public GetPeersResultMessageDecoder() {
		super(new Deserializer());
	}

	private static class Deserializer extends BaseDeserializer<GetPeersResultMessage> {

		protected Deserializer() {
			super(GetPeersResultMessage.class);
		}

		@Override
		protected void registerTypeDeserializers(GsonBuilder where) {
			new Peers.Decoder().registerAsTypeDeserializer(where);
		}

		@Override
		protected GetPeersResultMessage deserialize(JsonElement json, Gson gson) throws JsonParseException {
			return gson.fromJson(json, GsonHelper.class).toBean();
		}
	}

	private static class GsonHelper {
		private Peer[] peers;

		private GetPeersResultMessage toBean() {
			return GetPeersResultMessages.of(Stream.of(peers));
	    }
	}
}
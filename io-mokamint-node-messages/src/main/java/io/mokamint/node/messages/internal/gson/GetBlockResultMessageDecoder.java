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

import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import io.hotmoka.websockets.beans.BaseDecoder;
import io.hotmoka.websockets.beans.BaseDeserializer;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.Block;
import io.mokamint.node.messages.GetBlockResultMessage;
import io.mokamint.node.messages.GetBlockResultMessages;

/**
 * A decoder for {@link GetBlockResultMessage}.
 */
public class GetBlockResultMessageDecoder extends BaseDecoder<GetBlockResultMessage> {

	public GetBlockResultMessageDecoder() {
		super(new Deserializer());
	}

	private static class Deserializer extends BaseDeserializer<GetBlockResultMessage> {

		protected Deserializer() {
			super(GetBlockResultMessage.class);
		}

		@Override
		protected void registerTypeDeserializers(GsonBuilder where) {
			new Blocks.Decoder().registerAsTypeDeserializer(where);
		}

		@Override
		protected GetBlockResultMessage deserialize(JsonElement json, Gson gson) throws JsonParseException {
			return gson.fromJson(json, GsonHelper.class).toBean();
		}
	}

	private static class GsonHelper {
		private Block block;

		private GetBlockResultMessage toBean() {
			return GetBlockResultMessages.of(Optional.ofNullable(block));
	    }
	}
}
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

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import io.hotmoka.websockets.beans.BaseDecoder;
import io.mokamint.node.messages.GetBlockMessage;
import io.mokamint.node.messages.GetBlockMessages;

/**
 * A decoder for {@link GetBlockMessage}.
 */
public class GetBlockMessageDecoder extends BaseDecoder<GetBlockMessage> {

	public GetBlockMessageDecoder() {
		super(GetBlockMessage.class);
	}

	@Override
	protected GetBlockMessage decode(JsonElement element, Gson gson) {
		return gson.fromJson(element, GetBlockMessageGsonHelper.class).toBean();
	}

	private static class GetBlockMessageGsonHelper {
		private byte[] hash;

		private GetBlockMessage toBean() {
			return GetBlockMessages.of(hash);
	    }
	}
}
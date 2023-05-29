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
import io.mokamint.node.messages.GetPeersMessage;
import io.mokamint.node.messages.GetPeersMessages;

/**
 * A decoder for {@link GetPeersMessage}.
 */
public class GetPeersMessageDecoder extends BaseDecoder<GetPeersMessage> {

	public GetPeersMessageDecoder() {
		super(GetPeersMessage.class);
	}

	@Override
	protected GetPeersMessage decode(JsonElement element, Gson gson) {
		return gson.fromJson(element, GetPeersMessageGsonHelper.class).toBean();
	}

	private static class GetPeersMessageGsonHelper {
		private GetPeersMessage toBean() {
			return GetPeersMessages.instance();
	    }
	}
}
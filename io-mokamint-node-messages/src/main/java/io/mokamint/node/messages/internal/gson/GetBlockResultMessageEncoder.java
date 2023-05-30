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

import com.google.gson.GsonBuilder;

import io.hotmoka.websockets.beans.BaseEncoder;
import io.hotmoka.websockets.beans.BaseSerializer;
import io.mokamint.node.Blocks;
import io.mokamint.node.messages.GetBlockResultMessage;

/**
 * An encoder of {@code GetBlockResultMessage}.
 */
public class GetBlockResultMessageEncoder extends BaseEncoder<GetBlockResultMessage> {

	public GetBlockResultMessageEncoder() {
		super(new Serializer());
	}

	private static class Serializer extends BaseSerializer<GetBlockResultMessage> {

		private Serializer() {
			super(GetBlockResultMessage.class);
		}

		@Override
		protected void registerTypeSerializers(GsonBuilder where) {
			new Blocks.Encoder().registerAsTypeSerializer(where);
		}
	}
}
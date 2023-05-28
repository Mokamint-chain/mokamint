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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import io.hotmoka.websockets.beans.BaseEncoder;
import io.hotmoka.websockets.beans.BaseSerializer;
import io.mokamint.node.api.Block;
import io.mokamint.nonce.Deadlines;

public class BlockEncoder extends BaseEncoder<Block> {

	public BlockEncoder() {
		super(new BlockSerializer());
	}

	private static class BlockSerializer extends BaseSerializer<Block> {

		private BlockSerializer() {
			super(Block.class);
		}

		@Override
		protected void registerTypeSerializers(GsonBuilder where) {
			where.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer());
			new Deadlines.Encoder().registerAsTypeSerializer(where);
		}
	}

	private static class LocalDateTimeSerializer implements JsonSerializer<LocalDateTime> {

		@Override
		public JsonElement serialize(LocalDateTime time, Type type, JsonSerializationContext context) {
			// a date and time is represented as its string representation in ISO
			return new JsonPrimitive(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(time));
		}
	}
}
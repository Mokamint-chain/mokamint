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

package io.mokamint.nonce.internal.gson;

import java.lang.reflect.Type;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.websockets.beans.BaseEncoder;
import io.hotmoka.websockets.beans.BaseSerializer;
import io.mokamint.nonce.api.DeadlineDescription;

public class DeadlineDescriptionEncoder extends BaseEncoder<DeadlineDescription> {

	public DeadlineDescriptionEncoder() {
		super(new DeadlineDescriptionSerializer());
	}

	private static class DeadlineDescriptionSerializer extends BaseSerializer<DeadlineDescription> {

		private DeadlineDescriptionSerializer() {
			super(DeadlineDescription.class);
		}

		@Override
		protected void registerTypeSerializers(GsonBuilder where) {
			where.registerTypeAdapter(HashingAlgorithm.class, new HashingAlgorithmSerializer());
		}
	}

	private static class HashingAlgorithmSerializer implements JsonSerializer<HashingAlgorithm<byte[]>> {
	
		@Override
		public JsonElement serialize(HashingAlgorithm<byte[]> hashing, Type type, JsonSerializationContext context) {
			// a hashing algorithm is simply represented as its name
			return new JsonPrimitive(hashing.getName());
		}
	}
}
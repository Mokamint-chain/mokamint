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
import java.security.NoSuchAlgorithmException;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.websockets.beans.BaseDecoder;
import io.hotmoka.websockets.beans.BaseDeserializer;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.api.DeadlineDescription;

/**
 * A decoder for {@link io.mokamint.nonce.api.DeadlineDescription}.
 */
public class DeadlineDescriptionDecoder extends BaseDecoder<DeadlineDescription> {

	public DeadlineDescriptionDecoder() {
		super(new DeadlineDescriptionDeserializer());
	}

	private static class DeadlineDescriptionDeserializer extends BaseDeserializer<DeadlineDescription> {

		protected DeadlineDescriptionDeserializer() {
			super(DeadlineDescription.class);
		}

		@Override
		protected void registerTypeDeserializers(GsonBuilder where) {
			where.registerTypeAdapter(HashingAlgorithm.class, new HashingAlgorithmDeserializer());
		}

		@Override
		protected DeadlineDescription deserialize(JsonElement json, Gson gson) throws JsonParseException {
			return gson.fromJson(json, DeadlineDescriptionGsonHelper.class).toBean();
		}
	}

	private static class HashingAlgorithmDeserializer implements JsonDeserializer<HashingAlgorithm<byte[]>> {

		@Override
		public HashingAlgorithm<byte[]> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			try {
				return HashingAlgorithms.mk(json.getAsString(), Function.identity());
			}
			catch (NoSuchAlgorithmException e) {
				throw new JsonParseException(e);
			}
		}
	}

	private static class DeadlineDescriptionGsonHelper {
		private int scoopNumber;
		private byte[] data;
		private HashingAlgorithm<byte[]> hashing;

		private DeadlineDescription toBean() {
			return DeadlineDescriptions.of(scoopNumber, data, hashing);
	    }
	}
}
package io.mokamint.nonce.internal;

import java.lang.reflect.Type;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import io.hotmoka.crypto.api.HashingAlgorithm;

public class HashingAlgorithmSerializer implements JsonSerializer<HashingAlgorithm<byte[]>> {

	@Override
	public JsonElement serialize(HashingAlgorithm<byte[]> hashing, Type type, JsonSerializationContext context) {
		// a hashing algorithm is simply represented as its name
		return new JsonPrimitive(hashing.getName());
	}
}
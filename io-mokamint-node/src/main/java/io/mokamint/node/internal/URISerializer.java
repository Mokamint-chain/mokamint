package io.mokamint.node.internal;

import java.lang.reflect.Type;
import java.net.URI;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class URISerializer implements JsonSerializer<URI> {

	@Override
	public JsonElement serialize(URI uri, Type type, JsonSerializationContext context) {
		// a URI is simply represented as its string representation
		return new JsonPrimitive(uri.toString());
	}
}
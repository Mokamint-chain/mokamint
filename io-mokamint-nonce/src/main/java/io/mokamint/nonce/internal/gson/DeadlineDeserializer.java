package io.mokamint.nonce.internal.gson;

import java.lang.reflect.Type;
import java.security.NoSuchAlgorithmException;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import io.mokamint.nonce.api.Deadline;

public class DeadlineDeserializer implements JsonDeserializer<Deadline> {

	@Override
	public Deadline deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
		try {
			return new Gson().fromJson(json, DeadlineGsonHelper.class).toBean();
		}
		catch (NoSuchAlgorithmException e) {
			throw new JsonParseException(e);
		}
	}
}
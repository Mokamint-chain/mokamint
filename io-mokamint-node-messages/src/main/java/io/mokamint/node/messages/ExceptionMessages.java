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

package io.mokamint.node.messages;

import io.mokamint.node.messages.api.ExceptionMessage;
import io.mokamint.node.messages.internal.ExceptionResultMessageImpl;
import io.mokamint.node.messages.internal.gson.ExceptionResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.ExceptionResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.ExceptionResultMessageJson;

/**
 * A provider of {@link ExceptionMessage}.
 */
public final class ExceptionMessages {

	private ExceptionMessages() {}

	/**
	 * Yields an {@link ExceptionMessage}.
	 * 
	 * @param clazz the class of the exception
	 * @param message the message of the exception
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static ExceptionMessage of(Class<? extends Exception> clazz, String message, String id) {
		return new ExceptionResultMessageImpl(clazz, message, id);
	}

	/**
	 * Yields an {@link ExceptionMessage} built from the given exception.
	 * 
	 * @param exception the exception
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static ExceptionMessage of(Exception exception, String id) {
		return new ExceptionResultMessageImpl(exception.getClass(), exception.getMessage(), id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends ExceptionResultMessageEncoder {}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends ExceptionResultMessageDecoder {}

	/**
     * Json representation.
     */
	public static class Json extends ExceptionResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(ExceptionMessage message) {
    		super(message);
    	}

		@Override
		public String getExpectedType() {
			return ExceptionMessage.class.getName();
		}
    }
}
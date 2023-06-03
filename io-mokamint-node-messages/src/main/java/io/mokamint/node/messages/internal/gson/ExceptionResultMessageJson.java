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

import io.hotmoka.websockets.beans.JsonRepresentation;
import io.mokamint.node.messages.ExceptionResultMessage;
import io.mokamint.node.messages.ExceptionResultMessages;

/**
 * The JSON representation of an {@link ExceptionResultMessage}.
 */
public abstract class ExceptionResultMessageJson implements JsonRepresentation<ExceptionResultMessage> {
	private String clazz;
	private String message;

	protected ExceptionResultMessageJson(ExceptionResultMessage message) {
		this.clazz = message.getExceptionClass().getName();
		this.message = message.getMessage();
	}

	@SuppressWarnings("unchecked")
	@Override
	public ExceptionResultMessage unmap() throws ClassNotFoundException, ClassCastException {
		var exceptionClass = Class.forName(clazz);
		if (!Exception.class.isAssignableFrom(exceptionClass))
			throw new ClassCastException(clazz + " is not an Exception");

		return ExceptionResultMessages.of((Class<? extends Exception>) exceptionClass, message);
	}
}
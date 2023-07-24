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

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.api.ExceptionMessage;

/**
 * The JSON representation of an {@link ExceptionMessage}.
 */
public abstract class ExceptionResultMessageJson extends AbstractRpcMessageJsonRepresentation<ExceptionMessage> {
	private String clazz;
	private String message;

	protected ExceptionResultMessageJson(ExceptionMessage message) {
		super(message);

		this.clazz = message.getExceptionClass().getName();
		this.message = message.getMessage();
	}

	@SuppressWarnings("unchecked")
	@Override
	public ExceptionMessage unmap() throws ClassNotFoundException, ClassCastException {
		var exceptionClass = Class.forName(clazz);
		if (!Exception.class.isAssignableFrom(exceptionClass))
			throw new ClassCastException(clazz + " is not an Exception");

		return ExceptionMessages.of((Class<? extends Exception>) exceptionClass, message, getId());
	}

	@Override
	protected String getExpectedType() {
		return ExceptionMessage.class.getName();
	}
}
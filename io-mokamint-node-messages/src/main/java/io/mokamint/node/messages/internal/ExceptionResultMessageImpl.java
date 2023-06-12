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

package io.mokamint.node.messages.internal;

import java.util.Objects;

import io.mokamint.node.messages.ExceptionResultMessage;

/**
 * Implementation of the network message corresponding to an exception thrown by a method call.
 */
public class ExceptionResultMessageImpl implements ExceptionResultMessage {

	/**
	 * The class of the exception.
	 */
	private final Class<? extends Exception> clazz;

	/**
	 * The message of the exception.
	 */
	private final String message;

	/**
	 * Creates the message.
	 * 
	 * @param clazz the class of the exception
	 * @param message the message of the exception; this might be {@code null}
	 */
	public ExceptionResultMessageImpl(Class<? extends Exception> clazz, String message) {
		Objects.requireNonNull(clazz, "clazz");
		this.clazz = clazz;
		this.message = message;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof ExceptionResultMessage) {
			ExceptionResultMessage oerm = (ExceptionResultMessage) other;
			return clazz == oerm.getExceptionClass() && Objects.equals(message, oerm.getMessage());
		}
		else
			return false;
	}

	@Override
	public Class<? extends Exception> getExceptionClass() {
		return clazz;
	}

	@Override
	public String getMessage() {
		return message;
	}
}
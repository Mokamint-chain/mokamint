/*
Copyright 2021 Fausto Spoto

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

package io.mokamint.cli.internal;

import java.util.Objects;

/**
 * An exception thrown during the execution of a CLI command.
 */
public abstract class CommandExceptionImpl extends Exception {

	private static final long serialVersionUID = 2066756038127592236L;

	/**
	 * Creates the exception, with the given message and cause.
	 * 
	 * @param message the message
	 * @param cause the cause
	 */
	protected CommandExceptionImpl(String message, Throwable cause) {
		super(message, cause);

		Objects.requireNonNull(message);
		Objects.requireNonNull(cause);
	}

	/**
	 * Creates the exception, with an empty message and the given cause.
	 * 
	 * @param cause the cause
	 */
	protected CommandExceptionImpl(Throwable cause) {
		super("", cause);

		Objects.requireNonNull(cause);
	}

	/**
	 * Creates the exception, with the given message.
	 * 
	 * @param message the message
	 */
	protected CommandExceptionImpl(String message) {
		super(message);

		Objects.requireNonNull(message);
	}
}
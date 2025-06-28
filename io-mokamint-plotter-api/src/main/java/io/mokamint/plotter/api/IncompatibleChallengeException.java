/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.plotter.api;

import java.util.Objects;

/**
 * An exception stating that the challenge provided to a plot file is incompatible with that plot file.
 */
@SuppressWarnings("serial")
public class IncompatibleChallengeException extends Exception {

	/**
	 * Creates a new exception.
	 */
	public IncompatibleChallengeException() {
		super("Incompatible challenge");
	}

	/**
	 * Creates a new exception with the given message.
	 * 
	 * @param message the message
	 */
	public IncompatibleChallengeException(String message) {
		super(Objects.requireNonNull(message, "message cannot be null"));
	}

	/**
	 * Creates a new exception with the given cause.
	 * 
	 * @param cause the cause
	 */
	public IncompatibleChallengeException(Throwable cause) {
		super(Objects.requireNonNull(cause, "cause cannot be null"));
	}

	/**
	 * Creates a new exception with the given message and cause.
	 * 
	 * @param message the message
	 * @param cause the cause
	 */
	public IncompatibleChallengeException(String message, Throwable cause) {
		super(Objects.requireNonNull(message, "message cannot be null"), Objects.requireNonNull(cause, "cause cannot be null"));
	}
}
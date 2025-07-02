/*
Copyright 2024 Fausto Spoto

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

package io.mokamint.application.api;

/**
 * An exception stating that an application is closed and cannot perform the request.
 */
@SuppressWarnings("serial")
public class ClosedApplicationException extends ApplicationException {

	/**
	 * Creates a new exception.
	 */
	public ClosedApplicationException() {
		super("The application is closed");
	}

	/**
	 * Creates a new exception with the given message.
	 * 
	 * @param message the message
	 */
	public ClosedApplicationException(String message) {
		super(message);
	}

	/**
	 * Creates a new exception with the given cause.
	 * 
	 * @param cause the cause
	 */
	public ClosedApplicationException(Throwable cause) {
		super(cause.getMessage(), cause);
	}

	/**
	 * Creates a new exception with the given message and cause.
	 * 
	 * @param message the message
	 * @param cause the cause
	 */
	public ClosedApplicationException(String message, Throwable cause) {
		super(message, cause);
	}
}
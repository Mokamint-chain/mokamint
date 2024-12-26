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

package io.mokamint.application;

/**
 * An exception stating that an application is not available.
 */
@SuppressWarnings("serial")
public class ApplicationNotFoundException extends Exception {

	/**
	 * Creates a new exception with the given message.
	 * 
	 * @param message the message
	 */
	public ApplicationNotFoundException(String message) {
		super(message);
	}

	/**
	 * Creates a new exception with the given cause.
	 * 
	 * @param cause the cause
	 */
	public ApplicationNotFoundException(Throwable cause) {
		super(cause.getMessage(), cause);
	}

	/**
	 * Creates a new exception with the given message and cause.
	 * 
	 * @param message the message
	 * @param cause the cause
	 */
	public ApplicationNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}
}
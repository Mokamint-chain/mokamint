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

package io.mokamint.node.local.internal;

/**
 * An exception stating that the database of a Mokamint node is corrupted.
 */
@SuppressWarnings("serial")
public class UncheckedDatabaseException extends RuntimeException { // TODO: rename into DatabaseException at the end

	/**
	 * Creates a new exception with the given message.
	 * 
	 * @param message the message
	 */
	public UncheckedDatabaseException(String message) {
		super(message);
	}

	/**
	 * Creates a new exception with the given cause.
	 * 
	 * @param cause the cause
	 */
	public UncheckedDatabaseException(Throwable cause) {
		super(cause);
	}

	/**
	 * Creates a new exception with the given message and cause.
	 * 
	 * @param message the message
	 * @param cause the cause
	 */
	public UncheckedDatabaseException(String message, Throwable cause) {
		super(message, cause);
	}
}
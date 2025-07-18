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

package io.mokamint.node.local;

/**
 * An exception stating that a local Hotmoka node was not able to perform an operation.
 * This is a bug in the code of the node or a limit of the system. For instance,
 * it might occur because a database is corrupted, or a cryptographic algorithm is not available
 * or because a file cannot be accessed. Therefore, this exception is not meant to be caught.
 */
@SuppressWarnings("serial")
public class LocalNodeException extends RuntimeException {

	/**
	 * Creates a new exception with the given message.
	 * 
	 * @param message the message
	 */
	public LocalNodeException(String message) {
		super(message);
	}

	/**
	 * Creates a new exception with the given cause.
	 * 
	 * @param cause the cause
	 */
	public LocalNodeException(Throwable cause) {
		super(cause);
	}

	/**
	 * Creates a new exception with the given message and cause.
	 * 
	 * @param message the message
	 * @param cause the cause
	 */
	public LocalNodeException(String message, Throwable cause) {
		super(message, cause);
	}
}
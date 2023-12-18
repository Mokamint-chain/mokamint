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

package io.mokamint.node.api;

/**
 * An exception stating that a transaction added to a Mokamint node has been rejected.
 */
@SuppressWarnings("serial")
public class RejectedTransactionException extends Exception {

	/**
	 * Creates a new exception with the given message.
	 * 
	 * @param message the message
	 */
	public RejectedTransactionException(String message) {
		super(message);
	}

	/**
	 * Creates a new exception with the given message and cause.
	 * 
	 * @param message the message
	 * @param cause the cause
	 */
	public RejectedTransactionException(String message, Throwable cause) {
		super(message, cause);
	}
}
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

package io.mokamint.nonce.api;

/**
 * An exception thrown when a deadline validity check is not able to complete
 * and understand if the deadline was valid or not.
 */
@SuppressWarnings("serial")
public class DeadlineValidityCheckException extends Exception {

	/**
	 * Creates the exception.
	 * 
	 * @param message the message in the exception
	 */
	public DeadlineValidityCheckException(String message) {
		super(message);
	}

	/**
	 * Creates the exception.
	 * 
	 * @param cause the cause of the exception
	 */
	public DeadlineValidityCheckException(Exception cause) {
		super(cause);
	}
}
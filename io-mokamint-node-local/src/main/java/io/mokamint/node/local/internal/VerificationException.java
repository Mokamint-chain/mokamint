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
 * An exception thrown when trying to add a block to the blockchain,
 * that does not pass verification.
 */
@SuppressWarnings("serial")
public class VerificationException extends Exception {

	/**
	 * Creates a new exception that expresses the failure of the
	 * verification of a block added to the blockchain.
	 */
	public VerificationException(String message) {
		super(message);
	}
}
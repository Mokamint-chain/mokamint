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

package io.mokamint.node.api;

/**
 * An exception thrown if a peer is misbehaving.
 */
@SuppressWarnings("serial")
public class PeerException extends Exception {

	/**
	 * Creates the exception.
	 */
	public PeerException() {
	}

	/**
	 * Creates the exception.
	 * 
	 * @param message the message
	 */
	public PeerException(String message) {
		super(message);
	}

	/**
	 * Creates the exception.
	 * 
	 * @param cause the cause of the exception
	 */
	public PeerException(NodeException cause) {
		super(cause);
	}

	/**
	 * Creates the exception.
	 * 
	 * @param message the message of the exception
	 * @param cause the cause of the exception
	 */
	public PeerException(String message, NodeException cause) {
		super(message, cause);
	}
}
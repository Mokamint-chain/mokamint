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
 * An exception stating that the addition of a peer has been rejected for some reason,
 * for instance, it is closed or it is incompatible with another peer.
 */
@SuppressWarnings("serial")
public class PeerRejectedException extends Exception {

	/**
	 * Creates a new exception.
	 */
	public PeerRejectedException() {}

	/**
	 * Creates a new exception with the given message.
	 * 
	 * @param message the message
	 */
	public PeerRejectedException(String message) {
		super(message);
	}

	/**
	 * Creates a new exception with the given cause.
	 * 
	 * @param cause the cause
	 */
	public PeerRejectedException(Throwable cause) {
		super(cause);
	}

	/**
	 * Creates a new exception with the given message and cause.
	 * 
	 * @param message the message
	 * @param cause the cause
	 */
	public PeerRejectedException(String message, Throwable cause) {
		super(message, cause);
	}
}
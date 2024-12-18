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

import io.mokamint.node.api.NodeException;

/**
 * An exception thrown if a local node is requested to initialize itself
 * but its database already contains a chain.
 */
@SuppressWarnings("serial")
public class AlreadyInitializedException extends NodeException {

	/**
	 * Creates the exception.
	 */
	public AlreadyInitializedException() {
	}

	/**
	 * Creates the exception.
	 * 
	 * @param message the message
	 */
	public AlreadyInitializedException(String message) {
		super(message);
	}
}
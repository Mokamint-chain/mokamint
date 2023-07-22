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

package io.mokamint.node;

import java.io.IOException;

import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.Node;

/**
 * The internal API of a Mokamint node. It includes methods that are not
 * exported to the general users, but only used in the implementations of the nodes.
 */
public interface NodeInternals extends Node, AutoCloseable {

	/**
	 * Code executed when the node gets closed.
	 */
	interface CloseHandler {
		void close() throws InterruptedException;
	}

	/**
	 * Takes note that the given code must be executed when this node gets closed.
	 * 
	 * @param handler the code
	 */
	void addOnClosedHandler(CloseHandler handler);

	/**
	 * Removes the given code from that executed when this node gets closed.
	 * 
	 * @param handler the code
	 */
	void removeOnCloseHandler(CloseHandler handler);

	/**
	 * Closes the node.
	 * 
	 * @throws IOException if an I/O error occurred
	 * @throws DatabaseException if a database could not be closed correctly
	 * @throws InterruptedException if some closing activity has been interrupted
	 */
	@Override
	void close() throws IOException, DatabaseException, InterruptedException;
}
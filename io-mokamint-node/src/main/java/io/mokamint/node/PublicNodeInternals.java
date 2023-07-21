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
import java.util.stream.Stream;

import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.service.api.PublicNodeService;

/**
 * The internal API of a public Mokamint node. It includes methods that are not
 * exported to the general users, but only used in the implementations of the nodes.
 */
public interface PublicNodeInternals extends PublicNode, AutoCloseable {

	/**
	 * Takes note that the given service is wrapping this node.
	 * For instance, when this node gets closed, it closes the service as well.
	 * 
	 * @param service the service
	 */
	default void register(PublicNodeService service) {}

	/**
	 * Removes the given service from those that are wrapping this node.
	 * 
	 * @param service the service
	 */
	default void unregister(PublicNodeService service) {}

	/**
	 * Closes the node.
	 * 
	 * @throws IOException if an I/O error occurred
	 * @throws DatabaseException if a database could not be closed correctly
	 * @throws InterruptedException if some closing activity has been interrupted
	 */
	@Override
	void close() throws IOException, DatabaseException, InterruptedException;

	/**
	 * Whisper some peers to the node.
	 * 
	 * @param peers the peers
	 */
	void receiveWhisperedPeers(Stream<Peer> peers);
}
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

package io.mokamint.node.service;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import io.mokamint.node.api.PublicNode;
import io.mokamint.node.service.api.PublicNodeService;
import io.mokamint.node.service.internal.PublicNodeServiceImpl;
import jakarta.websocket.DeploymentException;

/**
 * A provider of node services for the public API of a node.
 */
public final class PublicNodeServices {

	private PublicNodeServices() {}

	/**
	 * Opens and yields a new service for the given node, at the given network port.
	 * The public URI of the machine is not provided to the service, therefore the
	 * service will try to guess its public IP and use it as its public URI.
	 * Uses 30 minutes as interval, in milliseconds, between successive
	 * broadcasts of the public IP of the service. Every such internal,
	 * the service will whisper its IP to its connected peers,
	 * in order to publish its willingness to become a peer. Uses 1000 as the size
	 * of the memory used to avoid whispering the same message again.
	 * 
	 * @param node the node
	 * @param port the port
	 * @return the new service
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public static PublicNodeService open(PublicNode node, int port) throws DeploymentException, IOException {
		return new PublicNodeServiceImpl(node, port, 1800000L, 1000, Optional.empty());
	}

	/**
	 * Opens and yields a new service for the given node, at the given network port.
	 * It allows one to specify the public URL of the machine, which will be suggested as a peer
	 * for the connected remotes. 
	 * 
	 * @param node the node
	 * @param port the port
	 * @param peerBroadcastInterval the time interval, in milliseconds, between successive
	 *                              broadcasts of the public IP of the service. Every such internal,
	 *                              the service will whisper its IP to its connected peers,
	 *                              in order to publish its willingness to become a peer
	 * @param whisperedMessagesSize the size of the memory used to avoid whispering the same
	 *                              message again; higher numbers reduce the circulation of
	 *                              spurious messages
	 * @param uri the URI that will be suggested as URL of the machine where the service is running; this might be
	 *            empty, which means that the service will try to guess its public IP and use it as its public URI
	 * @return the new service
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public static PublicNodeService open(PublicNode node, int port, long peerBroadcastInterval, int whisperedMessagesSize, Optional<URI> uri) throws DeploymentException, IOException {
		return new PublicNodeServiceImpl(node, port, peerBroadcastInterval, whisperedMessagesSize, uri);
	}
}
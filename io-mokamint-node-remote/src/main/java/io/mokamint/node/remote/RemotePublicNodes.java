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

package io.mokamint.node.remote;

import java.io.IOException;
import java.net.URI;

import io.mokamint.node.remote.internal.RemotePublicNodeImpl;
import jakarta.websocket.DeploymentException;

/**
 * Providers of remote public nodes. They present a programmatic interface
 * to a network service for a public node.
 */
public abstract class RemotePublicNodes {

	private RemotePublicNodes() {}

	/**
	 * Opens and yields a new public remote node for the public API of a network service.
	 * The resulting remote uses 1000 as the size of the memory used to avoid whispering the same message again.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 * @return the new remote node
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	public static RemotePublicNode of(URI uri, long timeout) throws DeploymentException, IOException {
		return new RemotePublicNodeImpl(uri, timeout, 1000);
	}

	/**
	 * Opens and yields a new public remote node for the public API of a network service.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 * @param whisperedMessagesSize the size of the memory used to avoid whispering the same
	 *                              message again; higher numbers reduce the circulation of
	 *                              spurious messages
	 * @return the new remote node
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	public static RemotePublicNode of(URI uri, long timeout, long whisperedMessagesSize) throws DeploymentException, IOException {
		return new RemotePublicNodeImpl(uri, timeout, whisperedMessagesSize);
	}
}
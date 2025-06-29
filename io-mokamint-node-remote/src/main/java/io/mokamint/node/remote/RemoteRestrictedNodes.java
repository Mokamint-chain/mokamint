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

import java.net.URI;

import io.hotmoka.websockets.api.FailedDeploymentException;
import io.mokamint.node.remote.api.RemoteRestrictedNode;
import io.mokamint.node.remote.internal.RemoteRestrictedNodeImpl;

/**
 * Providers of remote restricted nodes. They present a programmatic interface
 * to a network service for a restricted node.
 */
public abstract class RemoteRestrictedNodes {

	private RemoteRestrictedNodes() {}

	/**
	 * Opens and yields a new restricted remote node for the restricted API of a network service.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 * @return the new remote node
	 * @throws FailedDeploymentException if the remote node could not be created
	 */
	public static RemoteRestrictedNode of(URI uri, int timeout) throws FailedDeploymentException {
		return new RemoteRestrictedNodeImpl(uri, timeout);
	}
}
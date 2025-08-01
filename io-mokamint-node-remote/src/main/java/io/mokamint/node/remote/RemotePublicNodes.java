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
import java.util.concurrent.TimeoutException;

import io.hotmoka.websockets.api.FailedDeploymentException;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.remote.internal.RemotePublicNodeImpl;

/**
 * Providers of remote public nodes. They present a programmatic interface
 * to a network service for a public node.
 */
public abstract class RemotePublicNodes {

	private RemotePublicNodes() {}

	/**
	 * Opens and yields a new public remote node for the public API of a network service.
	 * The resulting remote uses 1000 as the size of the memory used to avoid whispering the same message again
	 * and does not broadcast the services opened on the node.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 * @return the new remote node
	 * @throws FailedDeploymentException if the remote node could not be created
	 * @throws InterruptedException if the current thread has been interrupted
	 * @throws TimeoutException if the creation has timed out
	 */
	public static RemotePublicNode of(URI uri, int timeout) throws FailedDeploymentException, TimeoutException, InterruptedException {
		return new RemotePublicNodeImpl(uri, timeout, -1, 1000);
	}

	/**
	 * Opens and yields a new public remote node for the public API of a network service.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 * @param serviceBroadcastInterval the time (in milliseconds) between successive broadcasts
	 *                                 of the services opened on this node; use a negative value to
	 *                                 disable service broadcasting
	 * @param whisperedMessagesSize the size of the memory used to avoid whispering the same
	 *                              message again; higher numbers reduce the circulation of spurious messages
	 * @return the new remote node
	 * @throws FailedDeploymentException if the remote node could not be created
	 * @throws InterruptedException if the current thread has been interrupted
	 * @throws TimeoutException if the creation has timed out
	 */
	public static RemotePublicNode of(URI uri, int timeout, int serviceBroadcastInterval, int whisperedMessagesSize) throws FailedDeploymentException, TimeoutException, InterruptedException {
		return new RemotePublicNodeImpl(uri, timeout, serviceBroadcastInterval, whisperedMessagesSize);
	}
}
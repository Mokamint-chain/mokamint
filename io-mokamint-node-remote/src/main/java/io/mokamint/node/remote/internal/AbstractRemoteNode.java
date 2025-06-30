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

package io.mokamint.node.remote.internal;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.client.AbstractRemote;
import io.mokamint.node.remote.api.RemoteNode;

/**
 * Shared code among the implementations of remote nodes that presents a programmatic interface
 * to a service for the public or restricted API of a Mokamint node.
 */
@ThreadSafe
abstract class AbstractRemoteNode extends AbstractRemote implements RemoteNode {

	/**
	 * Creates and opens a new remote node for the public or restricted API of a node.
	 * 
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 */
	protected AbstractRemoteNode(int timeout) {
		super(timeout);
	}
}
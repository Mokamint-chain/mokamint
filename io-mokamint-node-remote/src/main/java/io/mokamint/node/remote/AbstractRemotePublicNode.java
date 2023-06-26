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

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.remote.internal.AbstractRemotePublicNodeImpl;
import jakarta.websocket.DeploymentException;

/**
 * A partial implementation of a remote node that presents a programmatic interface
 * to a service for the public API of a Mokamint node.
 */
@ThreadSafe
public abstract class AbstractRemotePublicNode extends AbstractRemotePublicNodeImpl {

	/**
	 * Creates, opens and yields a new remote node for the public API of a node.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	public AbstractRemotePublicNode(URI uri) throws DeploymentException, IOException {
		super(uri);
	}
}
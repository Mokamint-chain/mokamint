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

import java.net.URI;
import java.util.Optional;

import io.mokamint.node.service.internal.AbstractPublicNodeServiceImpl;
import jakarta.websocket.DeploymentException;

/**
 * Partial implementation of a public node service. It publishes endpoints at a URL,
 * where clients can connect to query the public API of a Mokamint node.
 */
public abstract class AbstractPublicNodeService extends AbstractPublicNodeServiceImpl {

	/**
	 * Creates a new server, at the given network port.
	 * The public URI of the machine is not provided to the service, therefore the
	 * service will try to guess its public IP and use it as its public URI.
	 * 
	 * @param port the port
	 * @throws DeploymentException if the service cannot be deployed
	 */
	protected AbstractPublicNodeService(int port) throws DeploymentException {
		super(port, Optional.empty());
	}

	/**
	 * Creates a new server, at the given network port.
	 * 
	 * @param port the port
	 * @param uri the public URI of the machine where this service is running; if missing,
	 *            the service will try to determine the public IP of the machine and use it as its URI
	 * @throws DeploymentException if the service cannot be deployed
	 */
	protected AbstractPublicNodeService(int port, Optional<URI> uri) throws DeploymentException {
		super(port, uri);
	}
}
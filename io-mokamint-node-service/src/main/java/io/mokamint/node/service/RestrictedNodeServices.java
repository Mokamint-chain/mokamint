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

import io.mokamint.node.api.RestrictedNodeInternals;
import io.mokamint.node.service.api.RestrictedNodeService;
import io.mokamint.node.service.internal.RestrictedNodeServiceImpl;
import jakarta.websocket.DeploymentException;

/**
 * A provider of node services for the restricted API of a node.
 */
public final class RestrictedNodeServices {

	private RestrictedNodeServices() {}

	/**
	 * Opens and yields a new service for the given node, at the given network port.
	 * 
	 * @param node the node
	 * @param port the port
	 * @return the new service
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public static RestrictedNodeService open(RestrictedNodeInternals node, int port) throws DeploymentException, IOException {
		return new RestrictedNodeServiceImpl(node, port);
	}
}
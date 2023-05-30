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

import io.mokamint.node.api.PublicNode;
import io.mokamint.node.service.api.PublicNodeService;
import io.mokamint.node.service.internal.PublicNodeServiceImpl;
import jakarta.websocket.DeploymentException;

/**
 * A provider of public node services.
 */
public class PublicNodeServices {

	private PublicNodeServices() {}

	public static PublicNodeService of(PublicNode node, int port) throws DeploymentException, IOException {
		return new PublicNodeServiceImpl(node, port);
	}
}
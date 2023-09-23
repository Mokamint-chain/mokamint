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

package io.mokamint.node.tools.internal;

import java.net.URI;

import io.mokamint.node.remote.RemoteRestrictedNodes;
import io.mokamint.node.remote.api.RemoteRestrictedNode;
import picocli.CommandLine.Option;

/**
 * Shared code among the command that connect to a remote Mokamint node and perform Rpc calls
 * to the restricted API of the node.
 */
public abstract class AbstractRestrictedRpcCommand extends AbstractRpcCommand {

	@Option(names = "--restricted-uri", description = "the network URI where the restricted API of the node is published", defaultValue = "ws://localhost:8031")
	private URI restrictedUri;

	/**
	 * Yields the URI of the restricted API of the remote service.
	 * 
	 * @return the URI
	 */
	protected final URI restrictedUri() {
		return restrictedUri;
	}

	/**
	 * Opens a remote node connected to the restricted uri of the remote service and runs
	 * the given command body.
	 * 
	 * @param what the body
	 */
	protected void execute(RpcCommandBody<RemoteRestrictedNode> what) {
		execute(RemoteRestrictedNodes::of, what, restrictedUri);
	}
}
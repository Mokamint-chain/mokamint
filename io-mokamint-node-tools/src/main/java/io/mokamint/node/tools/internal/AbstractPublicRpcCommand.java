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
import java.util.logging.Logger;

import io.mokamint.node.remote.RemotePublicNode;
import io.mokamint.node.remote.RemotePublicNodes;
import picocli.CommandLine.Option;

/**
 * Shared code among the command that connect to a remote Mokamint node and perform Rpc calls
 * to the public API of the node.
 */
public abstract class AbstractPublicRpcCommand extends AbstractRpcCommand {

	@Option(names = { "--uri", "--public-uri" }, description = "the network URI where the public API of the node is published", defaultValue = "ws://localhost:8030")
	private URI publicUri;

	/**
	 * Yields the URI of the public API of the remote service.
	 * 
	 * @return the URI
	 */
	protected final URI publicUri() {
		return publicUri;
	}

	/**
	 * Opens a remote node connected to the public uri of the remote service and runs
	 * the given command body.
	 * 
	 * @param what the body
	 * @param logger the logger to use for reporting
	 */
	protected void execute(RpcCommandBody<RemotePublicNode> what, Logger logger) {
		execute(RemotePublicNodes::of, what, publicUri, logger);
	}
}
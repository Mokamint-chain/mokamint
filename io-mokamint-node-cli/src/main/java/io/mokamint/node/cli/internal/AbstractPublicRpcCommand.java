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

package io.mokamint.node.cli.internal;

import java.net.URI;

import io.hotmoka.cli.AbstractRpcCommand;
import io.hotmoka.cli.CommandException;
import io.hotmoka.cli.RpcCommandBody;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.remote.RemotePublicNodes;
import io.mokamint.node.remote.api.RemotePublicNode;
import picocli.CommandLine.Option;

/**
 * Shared code among the commands that connect to a remote and perform Rpc calls
 * to the public API of the remote.
 */
public abstract class AbstractPublicRpcCommand extends AbstractRpcCommand<RemotePublicNode, NodeException> {

	protected AbstractPublicRpcCommand() {
		super(NodeException.class);
	}

	@Option(names = { "--uri", "--public-uri" }, description = "the network URI where the public API of the service is published", defaultValue = "ws://localhost:8030")
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
	 * Opens a remote node connected to the public uri of the remote service and runs the given command body.
	 * 
	 * @param what the body
	 * @throws CommandException if something erroneous must be logged and the user must be informed
	 */
	protected void execute(RpcCommandBody<RemotePublicNode, NodeException> what) throws CommandException {
		execute(RemotePublicNodes::of, what, publicUri);
	}
}
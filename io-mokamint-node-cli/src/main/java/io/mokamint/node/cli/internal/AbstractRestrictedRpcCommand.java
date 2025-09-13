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
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.AbstractRpcCommand;
import io.hotmoka.cli.CommandException;
import io.mokamint.node.MisbehavingNodeException;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.remote.RemoteRestrictedNodes;
import io.mokamint.node.remote.api.RemoteRestrictedNode;
import picocli.CommandLine.Option;

/**
 * Shared code among the command that connect to a remote Mokamint node and perform Rpc calls
 * to the restricted API of the node.
 */
public abstract class AbstractRestrictedRpcCommand extends AbstractRpcCommand<RemoteRestrictedNode> {

	@Option(names = "--restricted-uri", description = "the network URI where the restricted API of the node is published", defaultValue = "ws://localhost:8031")
	private URI restrictedUri;

	@Option(names = "--redirection", paramLabel = "<path>", description = "the path where the output must be redirected, if any; if missing, the output is printed to the standard output")
	private Path redirection; // TODO: this is currently ignored in most commands

	@Option(names = "--json", description = "print the output in JSON", defaultValue = "false")
	private boolean json;

	protected AbstractRestrictedRpcCommand() {}

	/**
	 * Yields the URI of the restricted API of the remote service.
	 * 
	 * @return the URI
	 */
	protected final URI restrictedUri() {
		return restrictedUri;
	}

	/**
	 * Determines if the output is required in JSON format.
	 * 
	 * @return true if and only if that condition holds
	 */
	protected final boolean json() {
		return json;
	}

	/**
	 * Yields the path where the output must be redirected.
	 * If missing, the output must be printed to the standard output.
	 * 
	 * @return the path, if any
	 */
	protected final Optional<Path> redirection() {
		return Optional.ofNullable(redirection);
	}

	/**
	 * Opens a remote node connected to the restricted uri of the remote service and runs
	 * the given command body.
	 * 
	 * @param what the body
	 * @throws CommandException if something erroneous must be logged and the user must be informed
	 */
	protected void execute(RpcCommandBody what) throws CommandException {
		var body = new io.hotmoka.cli.RpcCommandBody<RemoteRestrictedNode>() {

			@Override
			public void run(RemoteRestrictedNode remote) throws TimeoutException, InterruptedException, CommandException {
				try {
					what.run(remote);
				}
				catch (MisbehavingNodeException e) {
					throw new CommandException("The node is misbehaving", e);
				}
				catch (ClosedNodeException e) {
					throw new CommandException("The node is already closed", e);
				}
			}
		};

		execute(RemoteRestrictedNodes::of, body, restrictedUri);
	}

	/**
	 * The body of the command.
	 */
	protected interface RpcCommandBody {

		/**
		 * Runs the body of the command.
		 * 
		 * @param remote the remote object
		 * @throws TimeoutException if the command timeouts
		 * @throws InterruptedException if the command was interrupted while waiting
		 * @throws ClosedNodeException if the remote node is already closed
		 * @throws MisbehavingNodeException if the remote node is misbehaving
		 * @throws CommandException if something erroneous must be logged and the user must be informed;
		 *                          throw this is the user provided a wrong argument to the command
		 */
		void run(RemoteRestrictedNode remote) throws TimeoutException, InterruptedException, ClosedNodeException, MisbehavingNodeException, CommandException;
	}
}
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

package io.mokamint.node.cli;

import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.CommandException;
import io.mokamint.node.MisbehavingNodeException;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.cli.internal.AbstractPublicRpcCommandImpl;
import io.mokamint.node.remote.api.RemotePublicNode;

/**
 * Shared code among the commands that connect to a remote and perform Rpc calls
 * to the public API of the remote.
 */
public abstract class AbstractPublicRpcCommand extends AbstractPublicRpcCommandImpl {

	/**
	 * Builds the Rpc command.
	 */
	protected AbstractPublicRpcCommand() {}

	/**
	 * The body of the command.
	 */
	public interface RpcCommandBody {

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
		void run(RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException, MisbehavingNodeException, CommandException;
	}
}
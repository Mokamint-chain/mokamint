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

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.AbstractCommand;
import io.hotmoka.cli.CommandException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.remote.api.RemoteNode;
import jakarta.websocket.DeploymentException;
import picocli.CommandLine.Option;

/**
 * Shared code among the command that connect to a remote Mokamint node and perform Rpc calls
 * to the public or restricted API of the node.
 */
public abstract class AbstractRpcCommand extends AbstractCommand {

	@Option(names = "--timeout", description = "the timeout of the connection, in milliseconds", defaultValue = "10000")
	private long timeout;

	@Option(names = "--json", description = "print the output in JSON", defaultValue = "false")
	private boolean json;

	/**
	 * Determines if the output must be reported in JSON format.
	 * 
	 * @return true if and only if that condition holds
	 */
	protected final boolean json() {
		return json;
	}

	/**
	 * Yields the timeout of the connection, in milliseconds.
	 * 
	 * @return the timeout
	 */
	protected final long timeout() {
		return timeout;
	}

	/**
	 * Opens a remote node connected to the public uri of the remote service and runs
	 * the given command body.
	 * 
	 * @param supplier the supplier of the remote node
	 * @param what the body
	 * @param uri the uri where the remote service can be contacted
	 * @throws CommandException if something erroneous must be logged and the user must be informed
	 */
	protected <N extends RemoteNode> void execute(RemoteSupplier<N> supplier, RpcCommandBody<N> what, URI uri) throws CommandException {
		try (var remote = supplier.get(uri, timeout)) {
			what.run(remote);
		}
		catch (NodeException e) {
			throw new CommandException("The node published at " + uri + " could not complete the operation", e);
		}
		catch (IOException e) {
			throw new CommandException("I/O error! Are you sure that a Mokamint node is actually published at " + uri + " and is accessible?", e);
		}
		catch (DeploymentException e) {
			throw new CommandException("Cannot contact the remote service! Are you sure that a Mokamint node is actually published at " + uri + " and is accessible?", e);
		}
		catch (TimeoutException e) {
			throw new CommandException("Timeout: I waited for " + timeout + "ms but the remote service didn't answer!", e);
		}
		catch (InterruptedException e) {
			throw new CommandException("Unexpected interruption while waiting for " + uri + "!", e);
		}
		catch (DatabaseException e) {
			throw new CommandException("The database of the node at " + uri + " seems corrupted!", e);
		}
	}

	/**
	 * A supplier of a remote node.
	 *
	 * @param <N> the type of the remote node
	 */
	protected interface RemoteSupplier<N extends RemoteNode> {
		N get(URI uri, long timeout) throws IOException, DeploymentException;
	}

	/**
	 * The body of an Rpc command on the public or restricted API of a node.
	 * 
	 * @param <N> the type of the remote node used to execute the command
	 */
	protected interface RpcCommandBody<N extends RemoteNode> {

		/**
		 * Runs the body of the command.
		 * 
		 * @param remote the remote node
		 * @throws TimeoutException if the command does into a timeout
		 * @throws InterruptedException if the command was interrupted while waiting
		 * @throws NodeException if the remote node could not complete the operation
		 * @throws DatabaseException if the database of the node is corrupted
		 * @throws CommandException if something erroneous must be logged and the user must be informed
		 */
		void run(N remote) throws TimeoutException, InterruptedException, NodeException, DatabaseException, CommandException;
	}

	/**
	 * Throws the given exception as one of the exceptions allowed in
	 * {@link RpcCommandBody#run(RemoteNode)}.
	 * 
	 * @param e the exception
	 * @throws NodeException if {@code e} belongs to that class
	 * @throws TimeoutException if {@code e} belongs to that class
	 * @throws InterruptedException if {@code e} belongs to that class
	 * @throws DatabaseException if {@code e} belongs to that class
	 * @throws CommandException if {@code e} belongs to that class
	 */
	protected static void throwAsRpcCommandException(Exception e) throws TimeoutException, InterruptedException, NodeException, DatabaseException, CommandException {
		if (e instanceof NodeException cne)
			throw cne;
		else if (e instanceof TimeoutException te)
			throw te;
		else if (e instanceof InterruptedException ie)
			throw ie;
		else if (e instanceof DatabaseException de)
			throw de;
		else if (e instanceof CommandException ce)
			throw ce;
		else if (e instanceof RuntimeException re)
			throw re;
		else
			throw new RuntimeException("Unexpected exception", e);
	}
}
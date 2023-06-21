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

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.mokamint.node.remote.AutoCloseableRemoteNode;
import io.mokamint.tools.AbstractCommand;
import jakarta.websocket.DeploymentException;
import picocli.CommandLine.Help.Ansi;
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
	 * @param logger the logger to use for reporting
	 */
	protected <N extends AutoCloseableRemoteNode> void execute(RemoteSupplier<N> supplier, RpcCommandBody<N> what, URI uri, Logger logger) {
		try (var remote = supplier.get(uri, timeout)) {
			what.run(remote);
		}
		catch (IOException e) {
			System.out.println(Ansi.AUTO.string("@|red I/O error! Are you sure that a Mokamint node is actually published at " + uri + " and is accessible?|@"));
			logger.log(Level.SEVERE, "I/O error while accessing \"" + uri + "\"", e);
		}
		catch (DeploymentException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot contact the remote service! Are you sure that a Mokamint node is actually published at " + uri + " and is accessible?|@"));
			logger.log(Level.SEVERE, "failed deployment a remote node for \"" + uri + "\"", e);
		}
		catch (TimeoutException e) {
			System.out.println(Ansi.AUTO.string("@|red Timeout: I waited for " + timeout + "ms but the remote service didn't answer.|@"));
			logger.log(Level.SEVERE, "a call to \"" + uri + "\" has timed-out", e);
		}
		catch (InterruptedException e) {
			System.out.println(Ansi.AUTO.string("@|red Unexpected interruption while waiting for \"" + uri + "\".|@"));
			logger.log(Level.SEVERE, "a call to \"" + uri + "\" has been interrupted", e);
		}
	}

	/**
	 * A supplier of a remote node.
	 *
	 * @param <N> the type of the remote node
	 */
	protected interface RemoteSupplier<N extends AutoCloseableRemoteNode> {
		N get(URI uri, long timeout) throws IOException, DeploymentException;
	}

	/**
	 * The body of an Rpc command on the public or restricted API of a node.
	 * 
	 * @param <N> the type of the remote node used to execute the command
	 */
	protected interface RpcCommandBody<N extends AutoCloseableRemoteNode> {

		/**
		 * Runs the body of the command.
		 * 
		 * @param remote the remote node
		 * @throws TimeoutException if the command does into a timeout
		 * @throws InterruptedException if the command was interrupted while waiting
		 */
		void run(N remote) throws TimeoutException, InterruptedException;
	}
}
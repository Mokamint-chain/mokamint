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

import io.mokamint.node.remote.RemotePublicNode;
import io.mokamint.node.remote.RemotePublicNodes;
import io.mokamint.tools.AbstractCommand;
import jakarta.websocket.DeploymentException;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;

/**
 * Shared code among the command that connect to a remote Mokamint node and perform Rpc calls to the node.
 */
public abstract class AbstractRpcCommand extends AbstractCommand {

	@Option(names = "--uri, --public-uri", description = "the network URI where the public API of the node is published", defaultValue = "ws://localhost:8030")
	private URI publicUri;

	@Option(names = "--restricted-uri", description = "the network URI where the restricted API of the node is published", defaultValue = "ws://localhost:8031")
	private URI restrictedUri;

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
	 * Yields the URI of the public API of the remote service.
	 * 
	 * @return the URI
	 */
	protected final URI publicUri() {
		return publicUri;
	}

	/**
	 * Yields the URI of the restricted API of the remote service.
	 * 
	 * @return the URI
	 */
	protected final URI restrictedUri() {
		return restrictedUri;
	}

	/**
	 * Opens a remote node connected to the public uri of the remote service and runs
	 * the given command body.
	 * 
	 * @param what the body
	 * @param logger the logger to use for reporting
	 */
	protected void executeOnPublicAPI(PublicRpcCommandBody what, Logger logger) {
		try (var remote = RemotePublicNodes.of(publicUri, timeout)) {
			what.run(remote);
		}
		catch (IOException e) {
			System.out.println(Ansi.AUTO.string("@|red I/O error! Are you sure that a Mokamint node is actually published at " + publicUri + " and is accessible?|@"));
			logger.log(Level.SEVERE, "I/O error while accessing \"" + publicUri + "\"", e);
		}
		catch (DeploymentException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot contact the remote service! Are you sure that a Mokamint node is actually published at " + publicUri + " and is accessible?|@"));
			logger.log(Level.SEVERE, "failed deployment a remote node for \"" + publicUri + "\"", e);
		}
		catch (TimeoutException e) {
			System.out.println(Ansi.AUTO.string("@|red Timeout: I waited for " + timeout + "ms but the remote service didn't answer.|@"));
			logger.log(Level.SEVERE, "a call to \"" + publicUri + "\" has timed-out", e);
		}
		catch (InterruptedException e) {
			System.out.println(Ansi.AUTO.string("@|red Unexpected interruption while waiting for \"" + publicUri + "\".|@"));
			logger.log(Level.SEVERE, "a call to \"" + publicUri + "\" has been interrupted", e);
		}
	}

	/**
	 * Opens a remote node connected to the restricted uri of the remote service and runs
	 * the given command body.
	 * 
	 * @param what the body
	 * @param logger the logger to use for reporting
	 */
	protected void executeOnRestrictedAPI(RestrictedRpcCommandBody what, Logger logger) {
		// TODO
	}

	/**
	 * Opens a remote node connected to the public uri of the remote service and a remote
	 * node connected to the restricted uri of the remote service and runs the given command body.
	 * 
	 * @param what the body
	 * @param logger the logger to use for reporting
	 */
	protected void executeOnFullAPI(PublicRpcCommandBody what, Logger logger) {
		// TODO
	}
	
	/**
	 * The body of an Rpc command on the public API of a node.
	 * 
	 * @throws TimeoutException if the command timed-out
	 * @throws InterruptedException if the command was interrupted while waiting
	 */
	protected interface PublicRpcCommandBody {
		void run(RemotePublicNode remote) throws TimeoutException, InterruptedException;
	}

	/**
	 * The body of an Rpc command on the restricted API of a node.
	 * 
	 * @throws TimeoutException if the command timed-out
	 * @throws InterruptedException if the command was interrupted while waiting
	 */
	protected interface RestrictedRpcCommandBody {
		//TODO void run(RemoteRestrictedNode remote) throws TimeoutException, InterruptedException;
	}

	/**
	 * The body of an Rpc command on the public and restricted API of a node.
	 * 
	 * @throws TimeoutException if the command timed-out
	 * @throws InterruptedException if the command was interrupted while waiting
	 */
	protected interface FullRpcCommandBody {
		//TODO void run(RemotePublicNode publicRemote, RemoteRestrictedNode restrictedRemote) throws TimeoutException, InterruptedException;
	}
}
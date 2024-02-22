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

package io.mokamint.node.remote.internal;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.closeables.CloseHandlersManagers;
import io.hotmoka.closeables.api.OnCloseHandler;
import io.hotmoka.closeables.api.OnCloseHandlersManager;
import io.hotmoka.websockets.beans.api.ExceptionMessage;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.hotmoka.websockets.client.AbstractClientEndpoint;
import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.hotmoka.websockets.client.RPCMessageQueuesContainers;
import io.hotmoka.websockets.client.api.RPCMessageQueuesContainer;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.remote.api.RemoteNode;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * Shared code among the implementations of remote nodes that presents a programmatic interface
 * to a service for the public or restricted API of a Mokamint node.
 */
@ThreadSafe
abstract class AbstractRemoteNode extends AbstractWebSocketClient implements RemoteNode {

	/**
	 * A map from path into the session listening to that path.
	 */
	private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();

	/**
	 * The manager of the close handlers.
	 */
	private final OnCloseHandlersManager manager = CloseHandlersManagers.create();

	/**
	 * Queues of messages received from the external world.
	 */
	private final RPCMessageQueuesContainer queues;

	/**
	 * True if and only if this node has been closed already.
	 */
	private final AtomicBoolean isClosed = new AtomicBoolean();

	private final static Logger LOGGER = Logger.getLogger(AbstractRemoteNode.class.getName());

	/**
	 * Creates and opens a new remote node for the public or restricted API of a node.
	 * 
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 */
	protected AbstractRemoteNode(long timeout) {
		this.queues = RPCMessageQueuesContainers.of(timeout);
	}

	@Override
	public void close() throws NodeException, InterruptedException {
		if (!isClosed.getAndSet(true))
			callOnCloseHandlersAndCloseSessions();
	}

	@Override
	public final void addOnCloseHandler(OnCloseHandler what) {
		manager.addOnCloseHandler(what);
	}

	@Override
	public final void removeOnCloseHandler(OnCloseHandler what) {
		manager.removeOnCloseHandler(what);
	}

	/**
	 * Adds a session at the given path starting at the given URI, connected to the
	 * endpoint resulting from the given supplier.
	 * 
	 * @param path the path
	 * @param uri the URI
	 * @param endpoint the supplier of the endpoint
	 * @throws DeploymentException if the session cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	protected final void addSession(String path, URI uri, Supplier<Endpoint> endpoint) throws DeploymentException, IOException {
		sessions.put(path, endpoint.get().deployAt(uri.resolve(path)));
	}

	protected final Session getSession(String key) {
		return sessions.get(key);
	}

	/**
	 * Determines if the given exception message deals with an exception that all
	 * methods of a node are expected to throw. These are
	 * {@code java.lang.TimeoutException}, {@code java.lang.InterruptedException}
	 * and {@link ClosedNodeException}.
	 * 
	 * @param message the message
	 * @return true if and only if that condition holds
	 */
	protected final boolean processStandardExceptions(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return TimeoutException.class.isAssignableFrom(clazz) ||
			InterruptedException.class.isAssignableFrom(clazz) ||
			ClosedNodeException.class.isAssignableFrom(clazz);
	}

	/**
	 * Ensures that this node is currently open.
	 * 
	 * @throws ClosedNodeException if this node is closed already
	 */
	protected final void ensureIsOpen() throws ClosedNodeException {
		if (isClosed.get())
			throw new ClosedNodeException("The node has been closed");
	}

	/**
	 * Notifies the given message to the waiting queue for its identifier.
	 * 
	 * @param message the message to notify
	 */
	protected void notifyResult(RpcMessage message) {
		queues.notifyResult(message);
	}

	/**
	 * Yields the identifier for the next message.
	 * 
	 * @return the identifier
	 */
	protected final String nextId() {
		return queues.nextId();
	}

	/**
	 * Waits until a reply arrives for the message with the given identifier.
	 * 
	 * @param <T> the type of the replied value
	 * @param id the identifier
	 * @param processSuccess a function that defines how to generate the replied value from the RPC message
	 * @param processException a predicate that determines if an exception message is accepted for the RPC message
	 * @return the replied value
	 * @throws Exception if the execution of the message led into this exception
	 */
	protected final <T> T waitForResult(String id, Function<RpcMessage, T> processSuccess, Predicate<ExceptionMessage> processException) throws Exception {
		return queues.waitForResult(id, processSuccess, processException);
	}

	protected abstract class Endpoint extends AbstractClientEndpoint<AbstractRemoteNode> {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, AbstractRemoteNode.this::notifyResult);
		}

		@Override
		public void onClose(Session session, CloseReason closeReason) {
			super.onClose(session, closeReason);

			try {
				// we close the remote since it is bound to a service that seems to be getting closed
				close();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				LOGGER.warning("remote: interrupted while closing " + getClass().getName() + ": " + e.getMessage());
			}
			catch (NodeException e) {
				LOGGER.warning("remote: cannot close " + getClass().getName() + ": " + e.getMessage());
			}
		}

		protected abstract Session deployAt(URI uri) throws DeploymentException, IOException;
	}

	private void callOnCloseHandlersAndCloseSessions() throws NodeException, InterruptedException {
		try {
			manager.close();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		}
		catch (Exception e) {
			throw new NodeException(e);
		}
		finally {
			closeSessions(sessions.values().toArray(Session[]::new), 0);
		}
	}

	private void closeSessions(Session[] sessions, int pos) throws NodeException {
		if (pos < sessions.length) {
			try {
				sessions[pos].close();
			}
			catch (IOException e) {
				LOGGER.warning("remote: cannot close session: " + e.getMessage());
				throw new NodeException(e);
			}
		}
	}
}
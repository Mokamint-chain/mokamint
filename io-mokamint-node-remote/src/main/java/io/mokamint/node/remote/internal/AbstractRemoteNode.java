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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.hotmoka.websockets.client.AbstractClientEndpoint;
import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.messages.api.ExceptionMessage;
import io.mokamint.node.remote.RemoteNode;
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
	private final Map<String, Session> sessions = new HashMap<>();

	/**
	 * The code to execute when this node gets closed.
	 */
	private final CopyOnWriteArrayList<CloseHandler> onCloseHandlers = new CopyOnWriteArrayList<>();

	/**
	 * True if and only if this node has been closed already.
	 */
	private final AtomicBoolean isClosed = new AtomicBoolean();

	private final static Logger LOGGER = Logger.getLogger(AbstractRemoteNode.class.getName());

	/**
	 * Opens and yields a new remote node for the public or restricted API of a node.
	 * 
	 * @return the new remote node
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	protected AbstractRemoteNode() throws DeploymentException, IOException {}

	@Override
	public final void addOnClosedHandler(CloseHandler what) {
		onCloseHandlers.add(what);
	}

	@Override
	public final void removeOnCloseHandler(CloseHandler what) {
		onCloseHandlers.add(what);
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
		var session = endpoint.get().deployAt(uri.resolve(path));

		synchronized (sessions) {
			sessions.put(path, session);
		}
	}

	protected final Session getSession(String key) {
		synchronized (sessions) {
			return sessions.get(key);
		}
	}

	/**
	 * Determines if the given exception message deals with an exception that all
	 * methods of a node are expected to throw. These are
	 * {@code java.lang.TimeoutException}, {@code java.lang.InterruptedException}
	 * and {@link ClosedNodeException}.
	 * 
	 * @param message the message
	 * @return true if and only if that condiotion holds
	 */
	protected final boolean processStandardExceptions(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return TimeoutException.class.isAssignableFrom(clazz) ||
			InterruptedException.class.isAssignableFrom(clazz) ||
			ClosedNodeException.class.isAssignableFrom(clazz);
	}

	@Override
	public void close() throws IOException, InterruptedException {
		if (!isClosed.getAndSet(true)) {
			IOException ioException = null;
			InterruptedException interruptedException = null;
			
			for (var handler: onCloseHandlers) {
				try {
					handler.close();
				}
				catch (InterruptedException e) {
					interruptedException = e;
				}
				catch (IOException e) {
					ioException = e;
				}
			}

			Collection<Session> sessions;

			synchronized (this.sessions) {
				sessions = this.sessions.values();
			}

			for (var session: sessions) {
				try {
					session.close();
				}
				catch (IOException e) {
					LOGGER.log(Level.WARNING, "remote: cannot close the sessions", ioException);
					ioException = e;
				}
			}

			if (interruptedException != null)
				throw interruptedException;
			else if (ioException != null)
				throw ioException;
		}
	}

	/**
	 * Ensures that this node is currently open.
	 * 
	 * @throws ClosedNodeException if this node is closed already
	 */
	protected final void ensureIsOpen() throws ClosedNodeException {
		if (isClosed.get())
			throw new ClosedNodeException("the node has been closed");
	}

	protected abstract void notifyResult(RpcMessage message);

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
			catch (IOException | InterruptedException e) {
				LOGGER.log(Level.SEVERE, "remote: cannot close " + getClass().getName(), e);
			}
		}

		protected abstract Session deployAt(URI uri) throws DeploymentException, IOException;
	}
}
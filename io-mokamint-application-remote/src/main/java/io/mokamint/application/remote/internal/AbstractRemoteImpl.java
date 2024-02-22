/*
Copyright 2024 Fausto Spoto

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

package io.mokamint.application.remote.internal;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.closeables.CloseHandlersManagers;
import io.hotmoka.closeables.api.OnCloseHandler;
import io.hotmoka.closeables.api.OnCloseHandlersContainer;
import io.hotmoka.closeables.api.OnCloseHandlersManager;
import io.hotmoka.websockets.beans.api.ExceptionMessage;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.hotmoka.websockets.client.AbstractClientEndpoint;
import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.hotmoka.websockets.client.RPCMessageQueuesContainer;
import io.hotmoka.websockets.client.RPCMessageQueuesContainers;
import io.mokamint.application.ClosedApplicationException;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * A partial implementation of a remote object that presents a programmatic interface
 * to a service for the API of another object of the same class.
 */
@ThreadSafe
public abstract class AbstractRemoteImpl<E extends Exception> extends AbstractWebSocketClient implements OnCloseHandlersContainer {

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

	private final static Logger LOGGER = Logger.getLogger(AbstractRemoteImpl.class.getName());

	/**
	 * Creates and opens a new remote application for the API of another application
	 * whose web service is already published.
	 * 
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 */
	protected AbstractRemoteImpl(long timeout) {
		this.queues = RPCMessageQueuesContainers.of(timeout);
	}

	@Override
	public void close() throws E, InterruptedException {
		if (!isClosed.getAndSet(true)) {
			try {
				super.close();
			}
			catch (InterruptedException e) {
				throw e;
			}
			catch (Exception e) {
				throw mkException(e);
			}
			finally {
				callOnCloseHandlersAndCloseSessions();
			}
		}
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
	 * Yields an exception to throw if {@link #ensureIsOpen()} is called
	 * and the remote was already closed.
	 * 
	 * @return the exception
	 */
	protected abstract E mkExceptionIfClosed();

	/**
	 * Yields an exception to throw if the remote behaves incorrectly for the given cause.
	 * 
	 * @param cause the cause
	 * @return the exception
	 */
	protected abstract E mkException(Exception cause);

	/**
	 * Hook called when an exception is received as result for an RPC.
	 * 
	 * @param message the RPC message containing the exception
	 */
	protected void onException(ExceptionMessage message) {}

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

	/**
	 * Yields the session at the given path.
	 * 
	 * @param path the path
	 * @return the session
	 */
	protected final Session getSession(String path) {
		return sessions.get(path);
	}

	/**
	 * Ensures that this node is currently open.
	 * 
	 * @throws ClosedApplicationException if this application is already closed
	 */
	protected final void ensureIsOpen() throws E {
		if (isClosed.get())
			throw mkExceptionIfClosed();
	}

	/**
	 * Notifies the given message to the waiting queue for its identifier.
	 * 
	 * @param message the message to notify
	 */
	protected void notifyResult(RpcMessage message) {
		if (message instanceof ExceptionMessage em)
			onException(em);
		else if (message == null) {
			LOGGER.log(Level.SEVERE, "unexpected null message");
			return;
		}
		else {
			LOGGER.log(Level.SEVERE, "unexpected message of class " + message.getClass().getName());
			return;
		}

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

	protected abstract class Endpoint extends AbstractClientEndpoint<AbstractRemoteImpl<?>> {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, AbstractRemoteImpl.this::notifyResult);
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
			catch (Exception e) {
				LOGGER.warning("remote: cannot close " + getClass().getName() + ": " + e.getMessage());
			}
		}

		protected abstract Session deployAt(URI uri) throws DeploymentException, IOException;
	}

	private void callOnCloseHandlersAndCloseSessions() throws E, InterruptedException {
		try {
			manager.close();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		}
		catch (Exception e) {
			throw mkException(e);
		}
		finally {
			closeSessions(sessions.values().toArray(Session[]::new), 0);
		}
	}

	private void closeSessions(Session[] sessions, int pos) throws E {
		if (pos < sessions.length) {
			try {
				sessions[pos].close();
			}
			catch (IOException e) {
				LOGGER.warning("remote: cannot close session: " + e.getMessage());
				throw mkException(e);
			}
		}
	}
}
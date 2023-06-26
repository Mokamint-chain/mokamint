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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.RpcMessage;
import io.hotmoka.websockets.client.AbstractClientEndpoint;
import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.mokamint.node.messages.ExceptionMessage;
import io.mokamint.node.messages.VoidMessage;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * Shared code among the implementations of remote nodes that presents a programmatic interface
 * to a service for the public or restricted API of a Mokamint node.
 */
@ThreadSafe
public abstract class AbstractRemoteNode extends AbstractWebSocketClient {
	private final long timeout;
	private final AtomicInteger nextId = new AtomicInteger();
	private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, BlockingQueue<RpcMessage>> queues = new ConcurrentHashMap<>();

	private final static Logger LOGGER = Logger.getLogger(AbstractRemoteNode.class.getName());

	/**
	 * Opens and yields a new remote node for the public or restricted API of a node.
	 * 
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 * @return the new remote node
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	protected AbstractRemoteNode(long timeout) throws DeploymentException, IOException {
		this.timeout = timeout;
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

	protected Session getSession(String key) {
		return sessions.get(key);
	}

	@Override
	public synchronized void close() throws IOException {
		IOException exception = null;

		for (var session: sessions.values()) {
			try {
				session.close();
			}
			catch (IOException e) {
				exception = e;
			}
		}

		if (exception != null) {
			LOGGER.log(Level.WARNING, "cannot close the sessions", exception);
			throw exception;
		}
	}

	protected final String nextId() {
		String id = String.valueOf(nextId.getAndIncrement());
		queues.put(id, new ArrayBlockingQueue<>(10));
		return id;
	}

	protected final <T> T waitForResult(String id, Function<RpcMessage, T> processSuccess, Predicate<ExceptionMessage> processException) throws Exception {
		final long startTime = System.currentTimeMillis();

		do {
			try {
				RpcMessage message = queues.get(id).poll(timeout - (System.currentTimeMillis() - startTime), TimeUnit.MILLISECONDS);
				if (message == null) {
					queues.remove(id);
					throw new TimeoutException();
				}

				var result = processSuccess.apply(message);
				if (result != null) {
					queues.remove(id);
					return result;
				}

				if (message instanceof ExceptionMessage) {
					var erm = (ExceptionMessage) message;

					if (processException.test(erm)) {
						queues.remove(id);
						Exception exc;
						try {
							exc = erm.getExceptionClass().getConstructor(String.class).newInstance(erm.getMessage());
						}
						catch (Exception e) {
							LOGGER.log(Level.SEVERE, "cannot instantiate the exception type", e);
							continue;
						}

						throw exc;
					}

					LOGGER.warning("received unexpected exception of type " + ((ExceptionMessage) message).getExceptionClass().getName());
				}
				else
					LOGGER.warning("received unexpected message of type " + message.getClass().getName());
			}
			catch (InterruptedException e) {
				queues.remove(id);
				throw e;
			}
		}
		while (System.currentTimeMillis() - startTime < timeout);

		queues.remove(id);
		throw new TimeoutException();
	}

	protected final VoidMessage processVoidSuccess(RpcMessage message) {
		return message instanceof VoidMessage ? (VoidMessage) message : null;
	}

	protected final boolean processStandardExceptions(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return TimeoutException.class.isAssignableFrom(clazz) || InterruptedException.class.isAssignableFrom(clazz);
	}

	private void notifyResult(RpcMessage message) {
		if (message != null) {
			var queue = queues.get(message.getId());
			if (queue != null) {
				if (!queue.offer(message))
					LOGGER.log(Level.SEVERE, "could not enqueue a message since the queue was full");
			}
			else
				LOGGER.log(Level.SEVERE, "received a message of type " + message.getClass().getName() + " but its id " + message.getId() + " has no corresponding waiting queue");
		}
	}

	protected final RuntimeException unexpectedException(Exception e) {
		LOGGER.log(Level.SEVERE, "unexpected exception", e);
		return new RuntimeException("unexpected exception", e);
	}

	protected abstract class Endpoint extends AbstractClientEndpoint<AbstractRemoteNode> {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, AbstractRemoteNode.this::notifyResult);
		}

		protected abstract Session deployAt(URI uri) throws DeploymentException, IOException;
	}
}
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
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.RpcMessage;
import io.hotmoka.websockets.client.AbstractClientEndpoint;
import io.hotmoka.websockets.client.AbstractWebSocketClient;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * Shared code among the implementations of remote nodes that presents a programmatic interface
 * to a service for the public or restricted API of a Mokamint node.
 */
@ThreadSafe
public abstract class AbstractRemoteNode extends AbstractWebSocketClient {

	/**
	 * A map from path into the session listening to that path.
	 */
	private final Map<String, Session> sessions = new HashMap<>();

	private final static Logger LOGGER = Logger.getLogger(AbstractRemoteNode.class.getName());

	/**
	 * Opens and yields a new remote node for the public or restricted API of a node.
	 * 
	 * @return the new remote node
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	protected AbstractRemoteNode() throws DeploymentException, IOException {}

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

	@Override
	public void close() throws IOException {
		IOException exception = null;

		Collection<Session> sessions;

		synchronized (this.sessions) {
			sessions = this.sessions.values();
		}

		for (var session: sessions)
			try {
				session.close();
			}
			catch (IOException e) {
				LOGGER.log(Level.WARNING, "cannot close the sessions", exception);
				exception = e;
			}

		if (exception != null)
			throw exception;
	}

	protected abstract void notifyResult(RpcMessage message);

	protected abstract class Endpoint extends AbstractClientEndpoint<AbstractRemoteNode> {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, AbstractRemoteNode.this::notifyResult);
		}

		protected abstract Session deployAt(URI uri) throws DeploymentException, IOException;
	}
}
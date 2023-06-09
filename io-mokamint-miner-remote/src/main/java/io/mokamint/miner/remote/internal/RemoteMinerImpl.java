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

package io.mokamint.miner.remote.internal;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.miner.api.Miner;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * The implementation of a remote miner. It publishes an endpoint at a URL,
 * where mining services can connect and provide their deadlines.
 */
@ThreadSafe
public class RemoteMinerImpl extends AbstractWebSocketServer implements Miner {

	/**
	 * The port of localhost, where this service is listening.
	 */
	private final int port;

	private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

	private final ListOfMiningRequests requests = new ListOfMiningRequests(10);

	private final static Logger LOGGER = Logger.getLogger(RemoteMinerImpl.class.getName());

	public RemoteMinerImpl(int port) throws DeploymentException, IOException {
		this.port = port;
		startContainer("", port, RemoteMinerEndpoint.config(this));
    	LOGGER.info("published a remote miner at ws://localhost:" + port);
	}

	@Override
	public void requestDeadline(DeadlineDescription description, Consumer<Deadline> onDeadlineComputed) {
		requests.add(description, onDeadlineComputed);

		LOGGER.info("requesting " + description + " to " + sessions.size() + " open sessions");

		sessions.stream()
			.filter(Session::isOpen)
			.map(Session::getAsyncRemote)
			.forEach(remote -> remote.sendObject(description));
	}

	@Override
	public void close() {
		stopContainer();
		LOGGER.info("closed the remote miner at ws://localhost:" + port);
	}

	private void addSession(Session session) {
		sessions.add(session);
		LOGGER.info("miner service " + session.getId() + ": bound");

		// we inform the newly arrived about work that it can already start doing
		var remote = session.getAsyncRemote();
		requests.forAllDescriptions(remote::sendObject);
	}

	private void removeSession(Session session) {
		sessions.remove(session);
		LOGGER.info("miner service " + session.getId() + ": unbound");
	}

	void processDeadline(Deadline deadline) {
		LOGGER.info("notifying deadline: " + deadline);
		requests.runAllActionsFor(deadline);
	}

	/**
	 * An endpoint that connects to miner services.
	 */
	public static class RemoteMinerEndpoint extends AbstractServerEndpoint<RemoteMinerImpl> {

		@SuppressWarnings("resource")
		@Override
	    public void onOpen(Session session, EndpointConfig config) {
	    	getServer().addSession(session);
	    	session.addMessageHandler((MessageHandler.Whole<Deadline>) getServer()::processDeadline);
	    }

	    @Override
		public void onClose(Session session, CloseReason closeReason) {
	    	getServer().removeSession(session);
	    }

		static ServerEndpointConfig config(RemoteMinerImpl server) {
			return simpleConfig(server, RemoteMinerEndpoint.class, "/", Deadlines.Decoder.class, DeadlineDescriptions.Encoder.class);
		}
	}
}
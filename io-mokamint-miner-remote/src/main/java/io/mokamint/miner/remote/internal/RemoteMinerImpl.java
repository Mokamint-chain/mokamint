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
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.miner.api.Miner;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

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

	@GuardedBy("itself")
	private final Set<Session> sessions = new HashSet<>();

	private final ListOfMiningRequests requests = new ListOfMiningRequests(10);

	private final static Logger LOGGER = Logger.getLogger(RemoteMinerImpl.class.getName());

	public RemoteMinerImpl(int port) throws DeploymentException, IOException {
		this.port = port;
		var configurator = new MyConfigurator();
    	var container = getContainer();
    	container.addEndpoint(RemoteMinerEndpoint.config(configurator));
    	container.start("", port);
    	LOGGER.info("published a remote miner at ws://localhost:" + port);
	}

	@Override
	public void requestDeadline(DeadlineDescription description, Consumer<Deadline> onDeadlineComputed) {
		requests.add(description, onDeadlineComputed);

		Set<Session> copy;
		synchronized (sessions) {
			copy = new HashSet<>(sessions);
		}

		LOGGER.info("requesting " + description + " to " + copy.size() + " open sessions");

		copy.stream()
			.filter(Session::isOpen)
			.map(Session::getAsyncRemote)
			.forEach(remote -> remote.sendObject(description));
	}

	@Override
	public void close() {
		super.close();
		LOGGER.info("closed the remote miner at ws://localhost:" + port);
	}

	void addSession(Session session) {
		synchronized (sessions) {
			sessions.add(session);
		}

		LOGGER.info("miner service " + session.getId() + ": bound");

		// we inform the newly arrived about work that it can already start doing
		var remote = session.getAsyncRemote();
		requests.forAllDescriptions(remote::sendObject);
	}

	void removeSession(Session session) {
		synchronized (sessions) {
			sessions.remove(session);
		}

		LOGGER.info("miner service " + session.getId() + ": unbound");
	}

	void processDeadline(Deadline deadline) {
		LOGGER.info("notifying deadline: " + deadline);
		requests.runAllActionsFor(deadline);
	}

	private class MyConfigurator extends Configurator {

    	@Override
    	public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
            var result = super.getEndpointInstance(endpointClass);
            if (result instanceof RemoteMinerEndpoint)
            	((RemoteMinerEndpoint) result).setServer(RemoteMinerImpl.this); // we inject the server

            return result;
        }
    }
}
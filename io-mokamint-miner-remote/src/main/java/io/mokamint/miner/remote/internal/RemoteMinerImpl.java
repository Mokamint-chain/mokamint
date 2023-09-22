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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.miner.api.Miner;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import io.mokamint.nonce.api.DeadlineValidityCheck;
import io.mokamint.nonce.api.IllegalDeadlineException;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * The implementation of a remote miner. It publishes an endpoint at a URL,
 * where mining services can connect and provide their deadlines.
 */
@ThreadSafe
public class RemoteMinerImpl extends AbstractWebSocketServer implements Miner {

	/**
	 * The unique identifier of the miner.
	 */
	private final UUID uuid = UUID.randomUUID();

	/**
	 * The port of localhost, where this service is listening.
	 */
	private final int port;

	/**
	 * An algorithm to check if deadlines are valid for the node we are working for.
	 */
	private final DeadlineValidityCheck check;

	private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

	private final ListOfMiningRequests requests = new ListOfMiningRequests(10);

	/**
	 * The prefix reported in the log messages.
	 */
	private final String logPrefix = "remote miner " + uuid + ": ";

	private final static Logger LOGGER = Logger.getLogger(RemoteMinerImpl.class.getName());

	/**
	 * Creates a remote miner.
	 * 
	 * @param port the port where the remote miner will receive the deadlines
	 * @param chainId the chain identifier of the blockchain for which the deadlines will be used
	 * @param nodePublicKey the public key of the node for which the deadlines are computed
	 * @throws DeploymentException if the miner cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public RemoteMinerImpl(int port, DeadlineValidityCheck check) throws DeploymentException, IOException {
		this.port = port;
		this.check = check;

		startContainer("", port, RemoteMinerEndpoint.config(this));
    	LOGGER.info(logPrefix + "published at ws://localhost:" + port);
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public void requestDeadline(DeadlineDescription description, Consumer<Deadline> onDeadlineComputed) {
		requests.add(description, onDeadlineComputed);

		LOGGER.info(logPrefix + "requesting " + description + " to " + sessions.size() + " open sessions");

		sessions.stream()
			.filter(Session::isOpen)
			.forEach(session -> sendDescription(session, description));
	}

	private void sendDescription(Session session, DeadlineDescription description) {
		try {
			sendObjectAsync(session, description);
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to miner service " + session.getId(), e);
		}
	}

	@Override
	public void close() throws IOException {
		sessions.forEach(session -> close(session, new CloseReason(CloseCodes.GOING_AWAY, "The remote miner has been turned off.")));

		try {
			stopContainer();
		}
		catch (RuntimeException e) {
			throw new IOException(e);
		}

		LOGGER.info(logPrefix + "unpublished from ws://localhost:" + port);
	}

	@Override
	public String toString() {
		int sessionsCount = sessions.size();
		String openSessions = sessionsCount == 1 ? "1 open session" : (sessionsCount + " open sessions");

		return "a remote miner published at port " + port + ", with " + openSessions;
	}

	private void addSession(Session session) {
		sessions.add(session);
		LOGGER.info(logPrefix + "bound miner service " + session.getId());

		// we inform the newly arrived about work that it can already start doing
		requests.forAllDescriptions(description -> sendDescription(session, description));
	}

	private void removeSession(Session session) {
		sessions.remove(session);
		LOGGER.info(logPrefix + "unbound miner service " + session.getId());
	}

	private void processDeadline(Deadline deadline, Session session) {
		// we avoid sending back illegal deadlines, since otherwise we take the risk
		// of being banned by the node we are working for
		try {
			check.check(deadline);
		}
		catch (IllegalDeadlineException e) {
			LOGGER.warning(logPrefix + "removing session " + session.getId() + " since it sent an illegal deadline: " + e.getMessage());
			removeSession(session);
			close(session, new CloseReason(CloseCodes.CANNOT_ACCEPT, e.getMessage()));
			return;
		}

		LOGGER.info(logPrefix + "notifying deadline: " + deadline);
		requests.runAllActionsFor(deadline);
	}

	private void close(Session session, CloseReason reason) {
		try {
			session.close(reason);
		}
		catch (IOException | IllegalStateException e) {
			LOGGER.warning(logPrefix + "cannot close session " + session.getId() + ": " + e.getMessage());
		}
	}

	/**
	 * An endpoint that connects to miner services.
	 */
	public static class RemoteMinerEndpoint extends AbstractServerEndpoint<RemoteMinerImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
	    	server.addSession(session);
	    	addMessageHandler(session, (Deadline deadline) -> server.processDeadline(deadline, session));
	    }

	    @Override
		public void onClose(Session session, CloseReason closeReason) {
	    	getServer().removeSession(session);
	    }

		private static ServerEndpointConfig config(RemoteMinerImpl server) {
			return simpleConfig(server, RemoteMinerEndpoint.class, "/", Deadlines.Decoder.class, DeadlineDescriptions.Encoder.class);
		}
	}
}
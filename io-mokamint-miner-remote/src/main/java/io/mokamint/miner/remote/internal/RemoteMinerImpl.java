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
import java.security.PublicKey;
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
import jakarta.websocket.CloseReason;
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
	 * The chain identifier of the blockchain for which the deadlines will be used.
	 */
	private final String chainId;

	/**
	 * The public key of the node for which the deadlines are computed.
	 */
	private final PublicKey nodePublicKey;

	private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

	private final ListOfMiningRequests requests = new ListOfMiningRequests(10);

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
	public RemoteMinerImpl(int port, String chainId, PublicKey nodePublicKey) throws DeploymentException, IOException {
		this.port = port;
		this.chainId = chainId;
		this.nodePublicKey = nodePublicKey;

		startContainer("", port, RemoteMinerEndpoint.config(this));
    	LOGGER.info("published a remote miner at ws://localhost:" + port);
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public void requestDeadline(DeadlineDescription description, Consumer<Deadline> onDeadlineComputed) {
		requests.add(description, onDeadlineComputed);

		LOGGER.info("requesting " + description + " to " + sessions.size() + " open sessions");

		sessions.stream()
			.filter(Session::isOpen)
			.forEach(session -> sendDescription(session, description));
	}

	private void sendDescription(Session session, DeadlineDescription description) {
		try {
			sendObjectAsync(session, description);
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot send to miner service " + session.getId(), e);
		}
	}

	@Override
	public void close() {
		stopContainer();
		LOGGER.info("closed the remote miner at ws://localhost:" + port);
	}

	@Override
	public String toString() {
		return "a remote miner published at port " + port + ", with " + sessions.size() + " open sessions";
	}

	private void addSession(Session session) {
		sessions.add(session);
		LOGGER.info("miner service " + session.getId() + ": bound");

		// we inform the newly arrived about work that it can already start doing
		requests.forAllDescriptions(description -> sendDescription(session, description));
	}

	private void removeSession(Session session) {
		sessions.remove(session);
		LOGGER.info("miner service " + session.getId() + ": unbound");
	}

	private void processDeadline(Deadline deadline) {
		// TODO
		// we avoid sending back illegal deadlines, since otherwise we take the risk
		// of being banned by the node we are working for
		LOGGER.info("notifying deadline: " + deadline);
		requests.runAllActionsFor(deadline);
	}

	/**
	 * An endpoint that connects to miner services.
	 */
	public static class RemoteMinerEndpoint extends AbstractServerEndpoint<RemoteMinerImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
	    	server.addSession(session);
	    	addMessageHandler(session, server::processDeadline);
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
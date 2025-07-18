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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.hotmoka.websockets.server.AbstractRPCWebSocketServer;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.mokamint.miner.api.ClosedMinerException;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.messages.GetMiningSpecificationMessages;
import io.mokamint.miner.messages.GetMiningSpecificationResultMessages;
import io.mokamint.miner.messages.api.GetMiningSpecificationMessage;
import io.mokamint.miner.remote.api.DeadlineValidityCheck;
import io.mokamint.miner.remote.api.DeadlineValidityCheckException;
import io.mokamint.miner.remote.api.IllegalDeadlineException;
import io.mokamint.miner.remote.api.RemoteMiner;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * The implementation of a remote miner. It publishes an endpoint at a URL,
 * where mining services can connect and provide their deadlines.
 */
@ThreadSafe
public class RemoteMinerImpl extends AbstractRPCWebSocketServer implements RemoteMiner {

	/**
	 * The unique identifier of the miner.
	 */
	private final UUID uuid = UUID.randomUUID();

	/**
	 * The port of localhost, where this service is listening.
	 */
	private final int port;

	/**
	 * The mining specification of this miner; all deadlines provided by the
	 * connected services must comply with this specification.
	 */
	private final MiningSpecification miningSpecification;

	/**
	 * An algorithm to check if deadlines are valid for the node we are working for.
	 */
	private final DeadlineValidityCheck check;

	/**
	 * The sessions of mining endpoint.
	 */
	private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

	/**
	 * The current list of mining requests.
	 */
	private final ListOfMiningRequests requests = new ListOfMiningRequests(10);

	/**
	 * The prefix reported in the log messages.
	 */
	private final String logPrefix;

	private final static Logger LOGGER = Logger.getLogger(RemoteMinerImpl.class.getName());

	/**
	 * Creates a remote miner.
	 * 
	 * @param port the port where the remote miner will receive the deadlines
	 * @param miningSpecification the specification of the mining parameters; all services
	 *                            that connect to this remote must provided deadlines
	 *                            that comply with this specification
	 * @param check the check to determine if a deadline is valid
	 * @throws FailedDeploymentException if the miner cannot be deployed
	 */
	public RemoteMinerImpl(int port, MiningSpecification miningSpecification, DeadlineValidityCheck check) throws FailedDeploymentException {
		this.port = port;
		this.miningSpecification = miningSpecification;
		this.check = Objects.requireNonNull(check);
		this.logPrefix = "remote miner listening at port " + port + ": ";

		startContainer("", port, GetMiningSpecificationEndpoint.config(this), MiningEndpoint.config(this));

		LOGGER.info(logPrefix + "published at ws://localhost:" + port);
	}

	@Override
    protected void processRequest(Session session, RpcMessage message) throws IOException {
		var id = message.getId();

    	if (message instanceof GetMiningSpecificationMessage)
    		sendObjectAsync(session, GetMiningSpecificationResultMessages.of(miningSpecification, id));
    	else
    		LOGGER.severe("Unexpected message of type " + message.getClass().getName());
    }

	protected void onGetMiningSpecification(GetMiningSpecificationMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_MINING_SPECIFICATION_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public void requestDeadline(Challenge challenge, Consumer<Deadline> onDeadlineComputed) throws ClosedMinerException {
		ensureIsOpen(ClosedMinerException::new);
		requests.add(challenge, onDeadlineComputed);
		LOGGER.info(logPrefix + "requesting " + challenge + " to " + sessions.size() + " open sessions");
		sessions.stream()
			.filter(Session::isOpen)
			.forEach(session -> sendChallenge(session, challenge));
	}

	@Override
	public MiningSpecification getMiningSpecification() throws ClosedMinerException {
		ensureIsOpen(ClosedMinerException::new);
		return miningSpecification;
	}

	private void sendChallenge(Session session, Challenge challenge) {
		try {
			sendObjectAsync(session, challenge);
		}
		catch (IOException e) {
			LOGGER.warning(logPrefix + "cannot send to session " + session.getId() + ": " + e.getMessage());
		}
	}

	@Override
	protected void closeResources() {
		try {
			sessions.forEach(session -> close(session, new CloseReason(CloseCodes.GOING_AWAY, "The remote miner has been turned off.")));
			LOGGER.info(logPrefix + "unpublished from ws://localhost:" + port);
		}
		finally {
			super.closeResources();
		}
	}

	@Override
	public String toString() {
		int sessionsCount = sessions.size();
		String openSessions = sessionsCount == 1 ? "1 open session" : (sessionsCount + " open sessions");
		return "a remote miner published at ws://localhost:" + port + ", with " + openSessions;
	}

	private void addSession(Session session) {
		sessions.add(session);
		LOGGER.info(logPrefix + "bound miner service " + session.getId());
		// we inform the newly arrived about work that it can already start doing
		requests.forAllChallenges(challenge -> sendChallenge(session, challenge));
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
			LOGGER.info(logPrefix + "notifying deadline: " + deadline);
			requests.runAllActionsFor(deadline);
		}
		catch (IllegalDeadlineException e) {
			LOGGER.warning(logPrefix + "removing session " + session.getId() + " since it sent an illegal deadline: " + e.getMessage());
			removeSession(session);
			close(session, new CloseReason(CloseCodes.CANNOT_ACCEPT, e.getMessage()));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		catch (DeadlineValidityCheckException e) {
			LOGGER.warning(logPrefix + "the check of the validity of " + deadline + " failed: " + e.getMessage());
		}
	}

	private void close(Session session, CloseReason reason) {
		try {
			session.close(reason);
		}
		catch (IOException | IllegalStateException e) {
			LOGGER.warning(logPrefix + "cannot close session " + session.getId() + ": " + e.getMessage());
		}
	}

	public static class GetMiningSpecificationEndpoint extends AbstractServerEndpoint<RemoteMinerImpl> {
	
		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (GetMiningSpecificationMessage message) -> server.onGetMiningSpecification(message, session));
	    }
	
		private static ServerEndpointConfig config(RemoteMinerImpl server) {
			return simpleConfig(server, GetMiningSpecificationEndpoint.class, GET_MINING_SPECIFICATION_ENDPOINT,
				GetMiningSpecificationMessages.Decoder.class, GetMiningSpecificationResultMessages.Encoder.class);
		}
	}

	/**
	 * An endpoint that connects to miner services and receives deadlines.
	 * Those deadlines gets verified and, if correct, sent to their requester
	 * by running the associated action.
	 */
	public static class MiningEndpoint extends AbstractServerEndpoint<RemoteMinerImpl> {

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
			return simpleConfig(server, MiningEndpoint.class, MINING_ENDPOINT, Deadlines.Decoder.class, Challenges.Encoder.class);
		}
	}
}
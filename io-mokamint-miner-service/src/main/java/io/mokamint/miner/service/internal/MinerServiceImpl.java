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

package io.mokamint.miner.service.internal;

import static io.mokamint.miner.remote.api.RemoteMiner.GET_MINING_SPECIFICATION_ENDPOINT;
import static io.mokamint.miner.remote.api.RemoteMiner.MINING_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.websockets.beans.ExceptionMessages;
import io.hotmoka.websockets.client.AbstractRemote;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.api.MinerException;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.messages.GetMiningSpecificationMessages;
import io.mokamint.miner.messages.GetMiningSpecificationResultMessages;
import io.mokamint.miner.messages.api.GetMiningSpecificationMessage;
import io.mokamint.miner.messages.api.GetMiningSpecificationResultMessage;
import io.mokamint.miner.service.api.MinerService;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * Implementation of a client that connects to a remote miner.
 * It is an adapter of a miner into a web service client.
 */
public class MinerServiceImpl extends AbstractRemote<MinerException> implements MinerService {

	/**
	 * The adapted miner.
	 */
	private final Miner miner;

	/**
	 * The session used to receive challenges and send back deadlines to the remote miner.
	 */
	private final Session session;

	/**
	 * The prefix used in the log messages;
	 */
	private final String logPrefix;

	private final static Logger LOGGER = Logger.getLogger(MinerServiceImpl.class.getName());

	/**
	 * Creates an miner service by adapting the given miner.
	 * 
	 * @param miner the adapted miner
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @throws MinerException if the service cannot be deployed
	 */
	public MinerServiceImpl(Miner miner, URI uri) throws MinerException {
		super(30_000); // TODO

		this.miner = miner;
		this.logPrefix = "miner service working for " + uri + ": ";

		try {
			addSession(MINING_ENDPOINT, uri, MiningEndpoint::new);
			addSession(GET_MINING_SPECIFICATION_ENDPOINT, uri, GetMiningSpecificationEndpoint::new);
		}
		catch (IOException | DeploymentException e) {
			throw new MinerException(e);
		}

		this.session = getSession(MINING_ENDPOINT);

		LOGGER.info(logPrefix + "bound to " + uri);
	}

	@Override
	public MiningSpecification getMiningSpecification() throws MinerException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendGetMiningSpecification(id);
		return waitForResult(id, GetMiningSpecificationResultMessage.class, TimeoutException.class, MinerException.class);
	}

	/**
	 * Sends a {@link GetMiningSpecificationMessage} to the remote miner.
	 * 
	 * @param id the identifier of the message
	 * @throws MinerException if the message could not be sent
	 */
	protected void sendGetMiningSpecification(String id) throws MinerException {
		sendObjectAsync(getSession(GET_MINING_SPECIFICATION_ENDPOINT), GetMiningSpecificationMessages.of(id), MinerException::new);
	}

	/**
	 * Hook called when a {@link GetMiningSpecificationResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onMiningSpecificationResult(GetMiningSpecificationResultMessage message) {}

	private class GetMiningSpecificationEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetMiningSpecificationResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetMiningSpecificationMessages.Encoder.class);		
		}
	}

	@Override
	protected MinerException mkExceptionIfClosed() {
		return new MinerException("The miner service is closed");
	}

	@Override
	protected MinerException mkException(Exception cause) {
		return cause instanceof MinerException ne ? ne : new MinerException(cause);
	}

	@Override
	protected void closeResources(CloseReason reason) throws MinerException {
		super.closeResources(reason);
		LOGGER.info(logPrefix + "closed with reason: " + reason);
	}

	/**
	 * The endpoint calls this when a new deadline request arrives.
	 * It forwards the request to the miner.
	 * 
	 * @param description the description of the requested deadline
	 */
	private void requestDeadline(Challenge description) {
		try {
			ensureIsOpen();
			LOGGER.info(logPrefix + "received deadline request: " + description);
			miner.requestDeadline(description, this::onDeadlineComputed);
		}
		catch (MinerException e) {
			LOGGER.warning(logPrefix + "ignoring deadline request: " + description + " since this miner service is already closed: " + e.getMessage());
		}
	}

	/**
	 * Called by {@link #miner} when it finds a deadline. It forwards it to the remote miner.
	 * 
	 * @param deadline the deadline that the miner has just computed
	 */
	private void onDeadlineComputed(Deadline deadline) {
		try {
			ensureIsOpen();
			LOGGER.info(logPrefix + "sending " + deadline);
			sendObjectAsync(session, deadline);
		}
		catch (IOException e) {
			LOGGER.warning(logPrefix + "cannot send the deadline to the session: " + e.getMessage());
		}
		catch (MinerException e) {
			LOGGER.warning(logPrefix + "ignoring deadline " + deadline + " since this miner service is already closed: " + e.getMessage());
		}
	}

	private class MiningEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, Challenges.Decoder.class, Deadlines.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, MinerServiceImpl.this::requestDeadline);
		}
	}
}
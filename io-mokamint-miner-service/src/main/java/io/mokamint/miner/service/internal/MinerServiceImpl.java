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
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.websockets.api.FailedDeploymentException;
import io.hotmoka.websockets.beans.api.ExceptionMessage;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.hotmoka.websockets.client.AbstractRemote;
import io.mokamint.miner.api.ClosedMinerException;
import io.mokamint.miner.api.Miner;
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
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * Implementation of a client that connects to a remote miner.
 * It is an adapter of a miner into a web service client.
 */
public class MinerServiceImpl extends AbstractRemote implements MinerService {

	/**
	 * The adapted miner. This might be missing, in which case the service is just a proxy for calling the
	 * methods of the remote miner, but won't provide any deadline to that remote.
	 */
	private final Optional<Miner> miner;

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
	 * Creates a miner service by adapting the given miner.
	 * 
	 * @param miner the adapted miner; if this is missing, the service won't provide deadlines but
	 *              will anyway connect to the 
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @param timeout the time (in milliseconds) allowed for a call to the remote miner;
	 *                beyond that threshold, a timeout exception is thrown
	 * @throws FailedDeploymentException if the service cannot be deployed
	 */
	public MinerServiceImpl(Miner miner, URI uri, int timeout) throws FailedDeploymentException {
		super(timeout);

		this.miner = Optional.of(miner);
		// TODO: check that miner has the same mining specification as the remote
		this.logPrefix = "miner service working for " + uri + ": ";

		addSession(MINING_ENDPOINT, uri, MiningEndpoint::new);
		addSession(GET_MINING_SPECIFICATION_ENDPOINT, uri, GetMiningSpecificationEndpoint::new);

		this.session = getSession(MINING_ENDPOINT);

		LOGGER.info(logPrefix + "bound");
	}

	/**
	 * Creates a miner service without a miner. It won't provide deadlines to the connected
	 * remote miner, by allows one to call the methods of the remote miner anyway.
	 * 
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @param timeout the time (in milliseconds) allowed for a call to the remote miner;
	 *                beyond that threshold, a timeout exception is thrown
	 * @throws FailedDeploymentException if the service cannot be deployed
	 */
	public MinerServiceImpl(URI uri, int timeout) throws FailedDeploymentException {
		super(timeout);

		this.miner = Optional.empty();
		this.logPrefix = "miner service connected to " + uri + ": ";

		addSession(MINING_ENDPOINT, uri, MiningEndpoint::new);
		addSession(GET_MINING_SPECIFICATION_ENDPOINT, uri, GetMiningSpecificationEndpoint::new);

		this.session = getSession(MINING_ENDPOINT);

		LOGGER.info(logPrefix + "bound");
	}

	@Override
	public MiningSpecification getMiningSpecification() throws ClosedMinerException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedMinerException::new);
		var id = nextId();
		sendGetMiningSpecification(id);
		return waitForResult(id, GetMiningSpecificationResultMessage.class);
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		if (message instanceof GetMiningSpecificationResultMessage gmsrm)
			onGetMiningSpecificationResult(gmsrm);
		else if (message != null && !(message instanceof ExceptionMessage)) {
			LOGGER.warning("unexpected message of class " + message.getClass().getName());
			return;
		}

		super.notifyResult(message);
	}

	/**
	 * Sends the given message to the given endpoint. If it fails, it just logs
	 * the exception and continues.
	 * 
	 * @param endpoint the endpoint
	 * @param message the message
	 */
	private void sendObjectAsync(String endpoint, RpcMessage message) {
		try {
			sendObjectAsync(getSession(endpoint), message);
		}
		catch (IOException e) {
			LOGGER.warning("cannot send to " + endpoint + ": " + e.getMessage());
		}
	}

	/**
	 * Sends a {@link GetMiningSpecificationMessage} to the remote miner.
	 * 
	 * @param id the identifier of the message
	 */
	protected void sendGetMiningSpecification(String id) {
		sendObjectAsync(GET_MINING_SPECIFICATION_ENDPOINT, GetMiningSpecificationMessages.of(id));
	}

	/**
	 * Hook called when a {@link GetMiningSpecificationResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onGetMiningSpecificationResult(GetMiningSpecificationResultMessage message) {}

	private class GetMiningSpecificationEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException {
			return deployAt(uri, GetMiningSpecificationResultMessages.Decoder.class, GetMiningSpecificationMessages.Encoder.class);		
		}
	}

	@Override
	protected void closeResources(CloseReason reason) {
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
			ensureIsOpen(ClosedMinerException::new);
			LOGGER.info(logPrefix + "received challenge: " + description);
			if (miner.isPresent())
				miner.get().requestDeadline(description, this::onDeadlineComputed);
		}
		catch (ClosedMinerException e) {
			LOGGER.warning(logPrefix + "ignoring challenge: " + description + " since this miner service is already closed: " + e.getMessage());
		}
	}

	/**
	 * Called by {@link #miner} when it finds a deadline. It forwards it to the remote miner.
	 * 
	 * @param deadline the deadline that the miner has just computed
	 */
	private void onDeadlineComputed(Deadline deadline) {
		try {
			ensureIsOpen(ClosedMinerException::new);
			LOGGER.info(logPrefix + "sending " + deadline);
			sendObjectAsync(session, deadline, IOException::new);
		}
		catch (IOException e) {
			LOGGER.warning(logPrefix + "cannot send the deadline to the session: " + e.getMessage());
		}
		catch (ClosedMinerException e) {
			LOGGER.warning(logPrefix + "ignoring deadline " + deadline + " since this miner service is already closed: " + e.getMessage());
		}
	}

	private class MiningEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException {
			return deployAt(uri, Challenges.Decoder.class, Deadlines.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, MinerServiceImpl.this::requestDeadline);
		}
	}
}
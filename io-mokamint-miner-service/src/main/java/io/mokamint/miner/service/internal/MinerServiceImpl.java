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

import static io.mokamint.miner.remote.api.RemoteMiner.GET_BALANCE_ENDPOINT;
import static io.mokamint.miner.remote.api.RemoteMiner.GET_MINING_SPECIFICATION_ENDPOINT;
import static io.mokamint.miner.remote.api.RemoteMiner.MINING_ENDPOINT;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.hotmoka.websockets.beans.api.ExceptionMessage;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.hotmoka.websockets.client.AbstractRemote;
import io.mokamint.miner.api.ClosedMinerException;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.messages.GetBalanceMessages;
import io.mokamint.miner.messages.GetBalanceResultMessages;
import io.mokamint.miner.messages.GetMiningSpecificationMessages;
import io.mokamint.miner.messages.GetMiningSpecificationResultMessages;
import io.mokamint.miner.messages.api.GetBalanceResultMessage;
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
	 * The prefix used in the log messages;
	 */
	private final String logPrefix;

	private final static Logger LOGGER = Logger.getLogger(MinerServiceImpl.class.getName());

	/**
	 * Creates a miner service by adapting the given miner.
	 * 
	 * @param miner the adapted miner; if this is missing, the service won't provide deadlines but
	 *              will anyway connect to the remote miner
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @param timeout the time (in milliseconds) allowed for a call to the remote miner;
	 *                beyond that threshold, a timeout exception is thrown
	 * @throws FailedDeploymentException if the service cannot be deployed
	 */
	public MinerServiceImpl(Optional<Miner> miner, URI uri, int timeout) throws FailedDeploymentException {
		super(timeout);

		this.miner = miner;
		this.logPrefix = "miner service working for " + uri + ": ";

		addSession(MINING_ENDPOINT, uri, MiningEndpoint::new);
		addSession(GET_BALANCE_ENDPOINT, uri, GetBalanceEndpoint::new);
		addSession(GET_MINING_SPECIFICATION_ENDPOINT, uri, GetMiningSpecificationEndpoint::new);

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
	public Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey publicKey) throws ClosedMinerException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedMinerException::new);
		var id = nextId();
		sendGetBalance(signature, publicKey, id);
		return waitForResult(id, GetBalanceResultMessage.class);
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		if (message instanceof GetMiningSpecificationResultMessage gmsrm)
			onGetMiningSpecificationResult(gmsrm);
		else if (message instanceof GetBalanceResultMessage gbrm)
			onGetBalanceResult(gbrm);
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
	 * Sends a {@link GetMiningSpecificationMessage} to the remote miner.
	 * 
	 * @param publicKey the public key whose balance is requested
	 * @param id the identifier of the message
	 */
	protected void sendGetBalance(SignatureAlgorithm signature, PublicKey publicKey, String id) {
		sendObjectAsync(GET_BALANCE_ENDPOINT, GetBalanceMessages.of(signature, publicKey, id));
	}

	/**
	 * Hook called when a {@link GetMiningSpecificationResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onGetMiningSpecificationResult(GetMiningSpecificationResultMessage message) {}

	/**
	 * Hook called when a {@link GetBalanceResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onGetBalanceResult(GetBalanceResultMessage message) {}

	private class GetMiningSpecificationEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException {
			return deployAt(uri, GetMiningSpecificationResultMessages.Decoder.class, GetMiningSpecificationMessages.Encoder.class);		
		}
	}

	private class GetBalanceEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException {
			return deployAt(uri, GetBalanceResultMessages.Decoder.class, GetBalanceMessages.Encoder.class);		
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
	 * @param session the session to use to send the deadline back
	 * @param challenge the challenge of the requested deadline
	 */
	private void requestDeadline(Session session, Challenge challenge) {
		try {
			ensureIsOpen(ClosedMinerException::new);
			LOGGER.info(logPrefix + "received challenge: " + challenge);
			if (miner.isPresent())
				miner.get().requestDeadline(challenge, deadline -> onDeadlineComputed(session, deadline));
		}
		catch (ClosedMinerException e) {
			LOGGER.warning(logPrefix + "ignoring challenge: " + challenge + " since this miner service is already closed: " + e.getMessage());
		}
	}

	/**
	 * Called by {@link #miner} when it finds a deadline. It forwards it to the remote miner.
	 * 
	 * @param session the session to use to send the deadline back to the requester
	 * @param deadline the deadline that the miner has just computed
	 */
	private void onDeadlineComputed(Session session, Deadline deadline) {
		// the session might be null if the construction of the service has not been completed yet,
		// but the first requests have already arrived to the mining endpoint
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
			addMessageHandler(session, (Challenge challenge) -> requestDeadline(session, challenge));
		}
	}
}
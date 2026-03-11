/*
Copyright 2024 Fausto Spoto

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

package io.mokamint.application.remote.internal;

import static io.mokamint.application.service.api.ApplicationService.ABORT_BLOCK_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.BEGIN_BLOCK_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.CHECK_DEADLINE_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.CHECK_REQUEST_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.COMMIT_BLOCK_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.EXECUTE_TRANSACTION_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.END_BLOCK_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.GET_BALANCE_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.GET_INFO_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.GET_INITIAL_STATE_ID_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.GET_PRIORITY_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.GET_REPRESENTATION_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.KEEP_FROM_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.PUBLISH_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.SET_HEAD_ENDPOINT;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.hotmoka.websockets.beans.api.ExceptionMessage;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.hotmoka.websockets.client.AbstractRemote;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.application.api.Info;
import io.mokamint.application.api.UnknownScopeIdException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.application.messages.AbortBlockMessages;
import io.mokamint.application.messages.AbortBlockResultMessages;
import io.mokamint.application.messages.BeginBlockMessages;
import io.mokamint.application.messages.BeginBlockResultMessages;
import io.mokamint.application.messages.CheckDeadlineMessages;
import io.mokamint.application.messages.CheckDeadlineResultMessages;
import io.mokamint.application.messages.CheckRequestMessages;
import io.mokamint.application.messages.CheckRequestResultMessages;
import io.mokamint.application.messages.CommitBlockMessages;
import io.mokamint.application.messages.CommitBlockResultMessages;
import io.mokamint.application.messages.ExecuteTransactionMessages;
import io.mokamint.application.messages.ExecuteTransactionResultMessages;
import io.mokamint.application.messages.EndBlockMessages;
import io.mokamint.application.messages.EndBlockResultMessages;
import io.mokamint.application.messages.GetBalanceMessages;
import io.mokamint.application.messages.GetBalanceResultMessages;
import io.mokamint.application.messages.GetInfoMessages;
import io.mokamint.application.messages.GetInfoResultMessages;
import io.mokamint.application.messages.GetInitialStateIdMessages;
import io.mokamint.application.messages.GetInitialStateIdResultMessages;
import io.mokamint.application.messages.GetPriorityMessages;
import io.mokamint.application.messages.GetPriorityResultMessages;
import io.mokamint.application.messages.GetRepresentationMessages;
import io.mokamint.application.messages.GetRepresentationResultMessages;
import io.mokamint.application.messages.KeepFromMessages;
import io.mokamint.application.messages.KeepFromResultMessages;
import io.mokamint.application.messages.PublishMessages;
import io.mokamint.application.messages.PublishResultMessages;
import io.mokamint.application.messages.SetHeadMessages;
import io.mokamint.application.messages.SetHeadResultMessages;
import io.mokamint.application.messages.api.AbortBlockMessage;
import io.mokamint.application.messages.api.AbortBlockResultMessage;
import io.mokamint.application.messages.api.BeginBlockMessage;
import io.mokamint.application.messages.api.BeginBlockResultMessage;
import io.mokamint.application.messages.api.CheckDeadlineMessage;
import io.mokamint.application.messages.api.CheckDeadlineResultMessage;
import io.mokamint.application.messages.api.CheckRequestMessage;
import io.mokamint.application.messages.api.CheckRequestResultMessage;
import io.mokamint.application.messages.api.CommitBlockMessage;
import io.mokamint.application.messages.api.CommitBlockResultMessage;
import io.mokamint.application.messages.api.ExecuteTransactionMessage;
import io.mokamint.application.messages.api.ExecuteTransactionResultMessage;
import io.mokamint.application.messages.api.EndBlockMessage;
import io.mokamint.application.messages.api.EndBlockResultMessage;
import io.mokamint.application.messages.api.GetBalanceMessage;
import io.mokamint.application.messages.api.GetBalanceResultMessage;
import io.mokamint.application.messages.api.GetInfoMessage;
import io.mokamint.application.messages.api.GetInfoResultMessage;
import io.mokamint.application.messages.api.GetInitialStateIdMessage;
import io.mokamint.application.messages.api.GetInitialStateIdResultMessage;
import io.mokamint.application.messages.api.GetPriorityMessage;
import io.mokamint.application.messages.api.GetPriorityResultMessage;
import io.mokamint.application.messages.api.GetRepresentationMessage;
import io.mokamint.application.messages.api.GetRepresentationResultMessage;
import io.mokamint.application.messages.api.KeepFromMessage;
import io.mokamint.application.messages.api.KeepFromResultMessage;
import io.mokamint.application.messages.api.PublishMessage;
import io.mokamint.application.messages.api.PublishResultMessage;
import io.mokamint.application.messages.api.SetHeadMessage;
import io.mokamint.application.messages.api.SetHeadResultMessage;
import io.mokamint.application.remote.api.RemoteApplication;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.Request;
import io.mokamint.node.api.RequestRejectedException;
import io.mokamint.nonce.api.Deadline;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

/**
 * An implementation of a remote node that presents a programmatic interface
 * to a service for the public API of a Mokamint node.
 */
@ThreadSafe
public class RemoteApplicationImpl extends AbstractRemote implements RemoteApplication {

	/**
	 * The prefix used in the log messages;
	 */
	private final String logPrefix;

	private final static Logger LOGGER = Logger.getLogger(RemoteApplicationImpl.class.getName());

	/**
	 * Opens and yields a new remote application for the API of another application
	 * already published as a web service.
	 * 
	 * @param uri the URI of the network service that will get bound to the remote application
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 * @throws FailedDeploymentException if the remote application could not be deployed
	 * @throws InterruptedException if the deployment of the service has been interrupted
	 */
	public RemoteApplicationImpl(URI uri, int timeout) throws FailedDeploymentException, InterruptedException {
		super(timeout);

		this.logPrefix = "application remote(" + uri + "): ";

		addSession(GET_BALANCE_ENDPOINT, uri, GetBalanceEndpoint::new);
		addSession(GET_INFO_ENDPOINT, uri, GetInfoEndpoint::new);
		addSession(CHECK_DEADLINE_ENDPOINT, uri, CheckPrologExtraEndpoint::new);
		addSession(CHECK_REQUEST_ENDPOINT, uri, CheckRequestEndpoint::new);
		addSession(GET_PRIORITY_ENDPOINT, uri, GetPriorityEndpoint::new);
		addSession(GET_REPRESENTATION_ENDPOINT, uri, GetRepresentationEndpoint::new);
		addSession(GET_INITIAL_STATE_ID_ENDPOINT, uri, GetInitialStateIdEndpoint::new);
		addSession(BEGIN_BLOCK_ENDPOINT, uri, BeginBlockEndpoint::new);
		addSession(EXECUTE_TRANSACTION_ENDPOINT, uri, ExecuteTransactionEndpoint::new);
		addSession(END_BLOCK_ENDPOINT, uri, EndBlockEndpoint::new);
		addSession(COMMIT_BLOCK_ENDPOINT, uri, CommitBlockEndpoint::new);
		addSession(ABORT_BLOCK_ENDPOINT, uri, AbortBlockEndpoint::new);
		addSession(KEEP_FROM_ENDPOINT, uri, KeepFromEndpoint::new);
		addSession(PUBLISH_ENDPOINT, uri, PublishEndpoint::new);
		addSession(SET_HEAD_ENDPOINT, uri, SetHeadEndpoint::new);
	}

	@Override
	protected void closeResources(CloseReason reason) {
		super.closeResources(reason);
		LOGGER.info(logPrefix + "closed with reason: " + reason);
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		switch (message) {
		case CheckDeadlineResultMessage cperm -> onCheckDeadlineResult(cperm);
		case CheckRequestResultMessage ctrm -> onCheckRequestResult(ctrm);
		case GetBalanceResultMessage gbrm -> onGetBalanceResult(gbrm);
		case GetInfoResultMessage girm -> onGetInfoResult(girm);
		case GetPriorityResultMessage gprm -> onGetPriorityResult(gprm);
		case GetRepresentationResultMessage grrm -> onGetRepresentationResult(grrm);
		case GetInitialStateIdResultMessage gisirm -> onGetInitialStateIdResult(gisirm);
		case BeginBlockResultMessage bbrm -> onBeginBlockResult(bbrm);
		case ExecuteTransactionResultMessage dtrm -> onExecuteTransactionResult(dtrm);
		case EndBlockResultMessage ebrm -> onEndBlockResult(ebrm);
		case CommitBlockResultMessage cbrm -> onCommitBlockResult(cbrm);
		case AbortBlockResultMessage abrm -> onAbortBlockResult(abrm);
		case KeepFromResultMessage kfrm -> onKeepFromResult(kfrm);
		case PublishResultMessage prm -> onPublishResult(prm);
		case SetHeadResultMessage shrm -> onSetHeadResult(shrm);
		default -> {
			if (message != null && !(message instanceof ExceptionMessage)) {
				LOGGER.warning("unexpected message of class " + message.getClass().getName());
				return;
			}
		}
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

	@Override
	public Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey publicKey) throws ClosedApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendGetBalance(signature, publicKey, id);
		return waitForResult(id, GetBalanceResultMessage.class);
	}

	/**
	 * Sends a {@link GetBalanceMessage} to the application service.
	 * 
	 * @param extra the extra bytes in the message
	 * @param id the identifier of the message
	 */
	protected void sendGetBalance(SignatureAlgorithm signature, PublicKey publicKey, String id) {
		sendObjectAsync(GET_BALANCE_ENDPOINT, GetBalanceMessages.of(signature, publicKey, id));
	}

	/**
	 * Hook called when a {@link GetBalanceResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onGetBalanceResult(GetBalanceResultMessage message) {}

	private class GetBalanceEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetBalanceResultMessages.Decoder.class, GetBalanceMessages.Encoder.class);
		}
	}

	@Override
	public void setHead(byte[] stateId) throws UnknownStateException, ClosedApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendSetHead(stateId, id);
		waitForResult(id, SetHeadResultMessage.class, UnknownStateException.class);
	}

	/**
	 * Sends a {@link SetHeadMessage} to the application service.
	 * 
	 * @param stateId the identifier of the state set as head
	 * @param id the identifier of the message
	 */
	protected void sendSetHead(byte[] stateId, String id) {
		sendObjectAsync(SET_HEAD_ENDPOINT, SetHeadMessages.of(stateId, id));
	}

	/**
	 * Hook called when a {@link SetHeadResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onSetHeadResult(SetHeadResultMessage message) {}

	private class SetHeadEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, SetHeadResultMessages.Decoder.class, ExceptionMessages.Decoder.class, SetHeadMessages.Encoder.class);
		}
	}

	@Override
	public boolean checkDeadline(Deadline deadline) throws ClosedApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendCheckDeadline(deadline, id);
		return waitForResult(id, CheckDeadlineResultMessage.class);
	}

	/**
	 * Sends a {@link CheckDeadlineMessage} to the application service.
	 * 
	 * @param deadline the deadline in the message
	 * @param id the identifier of the message
	 */
	protected void sendCheckDeadline(Deadline deadline, String id) {
		sendObjectAsync(CHECK_DEADLINE_ENDPOINT, CheckDeadlineMessages.of(deadline, id));
	}

	/**
	 * Hook called when a {@link CheckDeadlineResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onCheckDeadlineResult(CheckDeadlineResultMessage message) {}

	private class CheckPrologExtraEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, CheckDeadlineResultMessages.Decoder.class, CheckDeadlineMessages.Encoder.class);
		}
	}

	@Override
	public void checkRequest(Request transaction) throws RequestRejectedException, ClosedApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendCheckTransaction(transaction, id);
		waitForResult(id, CheckRequestResultMessage.class, RequestRejectedException.class);
	}

	/**
	 * Sends a {@link CheckRequestMessage} to the application service.
	 * 
	 * @param transaction the transaction in the message
	 * @param id the identifier of the message
	 */
	protected void sendCheckTransaction(Request transaction, String id) {
		sendObjectAsync(CHECK_REQUEST_ENDPOINT, CheckRequestMessages.of(transaction, id));
	}

	/**
	 * Hook called when a {@link CheckRequestResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onCheckRequestResult(CheckRequestResultMessage message) {}

	private class CheckRequestEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, CheckRequestResultMessages.Decoder.class, ExceptionMessages.Decoder.class, CheckRequestMessages.Encoder.class);
		}
	}

	@Override
	public long getPriority(Request transaction) throws RequestRejectedException, ClosedApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendGetPriority(transaction, id);
		return waitForResult(id, GetPriorityResultMessage.class, RequestRejectedException.class);
	}

	/**
	 * Sends a {@link GetPriorityMessage} to the application service.
	 * 
	 * @param transaction the transaction in the message
	 * @param id the identifier of the message
	 */
	protected void sendGetPriority(Request transaction, String id) {
		sendObjectAsync(GET_PRIORITY_ENDPOINT, GetPriorityMessages.of(transaction, id));
	}

	/**
	 * Hook called when a {@link GetPriorityResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onGetPriorityResult(GetPriorityResultMessage message) {}

	private class GetPriorityEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetPriorityResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetPriorityMessages.Encoder.class);
		}
	}

	@Override
	public String getRepresentation(Request transaction) throws RequestRejectedException, ClosedApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendGetRepresentation(transaction, id);
		return waitForResult(id, GetRepresentationResultMessage.class, RequestRejectedException.class);
	}

	/**
	 * Sends a {@link GetRepresentationMessage} to the application service.
	 * 
	 * @param transaction the transaction in the message
	 * @param id the identifier of the message
	 */
	protected void sendGetRepresentation(Request transaction, String id) {
		sendObjectAsync(GET_REPRESENTATION_ENDPOINT, GetRepresentationMessages.of(transaction, id));
	}

	/**
	 * Hook called when a {@link GetRepresentationResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onGetRepresentationResult(GetRepresentationResultMessage message) {}

	private class GetRepresentationEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetRepresentationResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetRepresentationMessages.Encoder.class);
		}
	}

	@Override
	public byte[] getInitialStateId() throws ClosedApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendGetInitialStateId(id);
		return waitForResult(id, GetInitialStateIdResultMessage.class);
	}

	/**
	 * Sends a {@link GetInitialStateIdMessage} to the application service.
	 * 
	 * @param id the identifier of the message
	 */
	protected void sendGetInitialStateId(String id) {
		sendObjectAsync(GET_INITIAL_STATE_ID_ENDPOINT, GetInitialStateIdMessages.of(id));
	}

	/**
	 * Hook called when a {@link GetInitialStateIdResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onGetInitialStateIdResult(GetInitialStateIdResultMessage message) {}

	private class GetInitialStateIdEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetInitialStateIdResultMessages.Decoder.class, GetInitialStateIdMessages.Encoder.class);
		}
	}

	@Override
	public int beginBlock(long height, LocalDateTime when, byte[] stateId) throws UnknownStateException, ClosedApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendBeginBlock(height, when, stateId, id);
		return waitForResult(id, BeginBlockResultMessage.class, UnknownStateException.class);
	}

	/**
	 * Sends a {@link BeginBlockMessage} to the application service.
	 * 
	 * @param height the height of the block whose transactions are being executed
	 * @param when the time at the beginning of the execution of the transactions in the block
	 * @param stateId the identifier of the state of the application at the beginning of the execution of
	 *                the transactions in the block
	 * @param id the identifier of the message
	 */
	protected void sendBeginBlock(long height, LocalDateTime when, byte[] stateId, String id) {
		sendObjectAsync(BEGIN_BLOCK_ENDPOINT, BeginBlockMessages.of(height, when, stateId, id));
	}

	/**
	 * Hook called when a {@link BeginBlockResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onBeginBlockResult(BeginBlockResultMessage message) {}

	private class BeginBlockEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, BeginBlockResultMessages.Decoder.class, ExceptionMessages.Decoder.class, BeginBlockMessages.Encoder.class);
		}
	}

	@Override
	public void executeTransaction(int groupId, Request transaction) throws RequestRejectedException, UnknownScopeIdException, ClosedApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendExecuteTransaction(groupId, transaction, id);
		waitForResult(id, ExecuteTransactionResultMessage.class, RequestRejectedException.class, UnknownScopeIdException.class);
	}

	/**
	 * Sends a {@link ExecuteTransactionMessage} to the application service.
	 * 
	 * @param groupId the group identifier in the message
	 * @param transaction the transaction in the message
	 * @param id the identifier of the message
	 */
	protected void sendExecuteTransaction(int groupId, Request transaction, String id) {
		sendObjectAsync(EXECUTE_TRANSACTION_ENDPOINT, ExecuteTransactionMessages.of(groupId, transaction, id));
	}

	/**
	 * Hook called when a {@link ExecuteTransactionResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onExecuteTransactionResult(ExecuteTransactionResultMessage message) {}

	private class ExecuteTransactionEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, ExecuteTransactionResultMessages.Decoder.class, ExceptionMessages.Decoder.class, ExecuteTransactionMessages.Encoder.class);
		}
	}

	@Override
	public byte[] endBlock(int groupId, Deadline deadline) throws ClosedApplicationException, UnknownScopeIdException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendEndBlock(groupId, deadline, id);
		return waitForResult(id, EndBlockResultMessage.class, UnknownScopeIdException.class);
	}

	/**
	 * Sends an {@link EndBlockMessage} to the application service.
	 * 
	 * @param groupId the identifier of the group of transactions in the message
	 * @param deadline the deadline in the message
	 * @param id the identifier of the message
	 */
	protected void sendEndBlock(int groupId, Deadline deadline, String id) {
		sendObjectAsync(END_BLOCK_ENDPOINT, EndBlockMessages.of(groupId, deadline, id));
	}

	/**
	 * Hook called when an {@link EndBlockResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onEndBlockResult(EndBlockResultMessage message) {}

	private class EndBlockEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, EndBlockResultMessages.Decoder.class, ExceptionMessages.Decoder.class, EndBlockMessages.Encoder.class);
		}
	}

	@Override
	public void commitBlock(int groupId) throws ClosedApplicationException, UnknownScopeIdException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendCommitBlock(groupId, id);
		waitForResult(id, CommitBlockResultMessage.class, UnknownScopeIdException.class);
	}

	/**
	 * Sends a {@link CommitBlockMessage} to the application service.
	 * 
	 * @param groupId the group identifier in the message
	 * @param id the identifier of the message
	 */
	protected void sendCommitBlock(int groupId, String id) {
		sendObjectAsync(COMMIT_BLOCK_ENDPOINT, CommitBlockMessages.of(groupId, id));
	}

	/**
	 * Hook called when a {@link CommitBlockResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onCommitBlockResult(CommitBlockResultMessage message) {}

	private class CommitBlockEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, CommitBlockResultMessages.Decoder.class, ExceptionMessages.Decoder.class, CommitBlockMessages.Encoder.class);
		}
	}

	@Override
	public void abortBlock(int groupId) throws ClosedApplicationException, UnknownScopeIdException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendAbortBlock(groupId, id);
		waitForResult(id, AbortBlockResultMessage.class, UnknownScopeIdException.class);
	}

	/**
	 * Sends an {@link AbortBlockMessage} to the application service.
	 * 
	 * @param groupId the group identifier in the message
	 * @param id the identifier of the message
	 */
	protected void sendAbortBlock(int groupId, String id) {
		sendObjectAsync(ABORT_BLOCK_ENDPOINT, AbortBlockMessages.of(groupId, id));
	}

	/**
	 * Hook called when an {@link AbortBlockResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onAbortBlockResult(AbortBlockResultMessage message) {}

	private class AbortBlockEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, AbortBlockResultMessages.Decoder.class, ExceptionMessages.Decoder.class, AbortBlockMessages.Encoder.class);
		}
	}

	@Override
	public void keepFrom(LocalDateTime start) throws ClosedApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendKeepFrom(start, id);
		waitForResult(id, KeepFromResultMessage.class);
	}

	/**
	 * Sends a {@link KeepFromMessage} to the application service.
	 * 
	 * @param start the limit time in the message, before which states can be garbage-collected
	 * @param id the identifier of the message
	 */
	protected void sendKeepFrom(LocalDateTime start, String id) {
		sendObjectAsync(KEEP_FROM_ENDPOINT, KeepFromMessages.of(start, id));
	}

	/**
	 * Hook called when a {@link KeepFromResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onKeepFromResult(KeepFromResultMessage message) {}

	private class KeepFromEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, KeepFromResultMessages.Decoder.class, KeepFromMessages.Encoder.class);
		}
	}

	@Override
	public void publish(Block block) throws ClosedApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendPublish(block, id);
		waitForResult(id, PublishResultMessage.class);
	}

	/**
	 * Sends a {@link PublishMessage} to the application service.
	 * 
	 * @param block the block to publish
	 * @param id the identifier of the message
	 */
	protected void sendPublish(Block block, String id) {
		sendObjectAsync(PUBLISH_ENDPOINT, PublishMessages.of(block, id));
	}

	/**
	 * Hook called when a {@link PublishResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onPublishResult(PublishResultMessage message) {}

	private class PublishEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, PublishResultMessages.Decoder.class, PublishMessages.Encoder.class);
		}
	}

	@Override
	public Info getInfo() throws ClosedApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen(ClosedApplicationException::new);
		var id = nextId();
		sendGetInfo(id);
		return waitForResult(id, GetInfoResultMessage.class);
	}

	/**
	 * Sends a {@link GetInfoMessage} to the application service.
	 * 
	 * @param id the identifier of the message
	 */
	protected void sendGetInfo(String id) {
		sendObjectAsync(GET_INFO_ENDPOINT, GetInfoMessages.of(id));
	}

	/**
	 * Hook called when a {@link GetInfoResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onGetInfoResult(GetInfoResultMessage message) {}

	private class GetInfoEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws FailedDeploymentException, InterruptedException {
			return deployAt(uri, GetInfoResultMessages.Decoder.class, GetInfoMessages.Encoder.class);
		}
	}
}
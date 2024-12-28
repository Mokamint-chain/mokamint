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
import static io.mokamint.application.service.api.ApplicationService.CHECK_PROLOG_EXTRA_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.CHECK_TRANSACTION_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.COMMIT_BLOCK_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.DELIVER_TRANSACTION_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.END_BLOCK_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.GET_INITIAL_STATE_ID_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.GET_PRIORITY_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.GET_REPRESENTATION_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.KEEP_FROM_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.hotmoka.websockets.beans.api.ExceptionMessage;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.hotmoka.websockets.client.AbstractRemote;
import io.mokamint.application.ClosedApplicationException;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.application.messages.AbortBlockMessages;
import io.mokamint.application.messages.AbortBlockResultMessages;
import io.mokamint.application.messages.BeginBlockMessages;
import io.mokamint.application.messages.BeginBlockResultMessages;
import io.mokamint.application.messages.CheckPrologExtraMessages;
import io.mokamint.application.messages.CheckPrologExtraResultMessages;
import io.mokamint.application.messages.CheckTransactionMessages;
import io.mokamint.application.messages.CheckTransactionResultMessages;
import io.mokamint.application.messages.CommitBlockMessages;
import io.mokamint.application.messages.CommitBlockResultMessages;
import io.mokamint.application.messages.DeliverTransactionMessages;
import io.mokamint.application.messages.DeliverTransactionResultMessages;
import io.mokamint.application.messages.EndBlockMessages;
import io.mokamint.application.messages.EndBlockResultMessages;
import io.mokamint.application.messages.GetInitialStateIdMessages;
import io.mokamint.application.messages.GetInitialStateIdResultMessages;
import io.mokamint.application.messages.GetPriorityMessages;
import io.mokamint.application.messages.GetPriorityResultMessages;
import io.mokamint.application.messages.GetRepresentationMessages;
import io.mokamint.application.messages.GetRepresentationResultMessages;
import io.mokamint.application.messages.KeepFromMessages;
import io.mokamint.application.messages.KeepFromResultMessages;
import io.mokamint.application.messages.api.AbortBlockMessage;
import io.mokamint.application.messages.api.AbortBlockResultMessage;
import io.mokamint.application.messages.api.BeginBlockMessage;
import io.mokamint.application.messages.api.BeginBlockResultMessage;
import io.mokamint.application.messages.api.CheckPrologExtraMessage;
import io.mokamint.application.messages.api.CheckPrologExtraResultMessage;
import io.mokamint.application.messages.api.CheckTransactionMessage;
import io.mokamint.application.messages.api.CheckTransactionResultMessage;
import io.mokamint.application.messages.api.CommitBlockMessage;
import io.mokamint.application.messages.api.CommitBlockResultMessage;
import io.mokamint.application.messages.api.DeliverTransactionMessage;
import io.mokamint.application.messages.api.DeliverTransactionResultMessage;
import io.mokamint.application.messages.api.EndBlockMessage;
import io.mokamint.application.messages.api.EndBlockResultMessage;
import io.mokamint.application.messages.api.GetInitialStateIdMessage;
import io.mokamint.application.messages.api.GetInitialStateIdResultMessage;
import io.mokamint.application.messages.api.GetPriorityMessage;
import io.mokamint.application.messages.api.GetPriorityResultMessage;
import io.mokamint.application.messages.api.GetRepresentationMessage;
import io.mokamint.application.messages.api.GetRepresentationResultMessage;
import io.mokamint.application.messages.api.KeepFromMessage;
import io.mokamint.application.messages.api.KeepFromResultMessage;
import io.mokamint.application.remote.api.RemoteApplication;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionRejectedException;
import io.mokamint.nonce.api.Deadline;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

/**
 * An implementation of a remote node that presents a programmatic interface
 * to a service for the public API of a Mokamint node.
 */
@ThreadSafe
public class RemoteApplicationImpl extends AbstractRemote<ApplicationException> implements RemoteApplication {

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
	 * @throws ApplicationException if the remote application could not be deployed
	 */
	public RemoteApplicationImpl(URI uri, int timeout) throws ApplicationException {
		super(timeout);

		this.logPrefix = "application remote(" + uri + "): ";

		try {
			addSession(CHECK_PROLOG_EXTRA_ENDPOINT, uri, CheckPrologExtraEndpoint::new);
			addSession(CHECK_TRANSACTION_ENDPOINT, uri, CheckTransactionEndpoint::new);
			addSession(GET_PRIORITY_ENDPOINT, uri, GetPriorityEndpoint::new);
			addSession(GET_REPRESENTATION_ENDPOINT, uri, GetRepresentationEndpoint::new);
			addSession(GET_INITIAL_STATE_ID_ENDPOINT, uri, GetInitialStateIdEndpoint::new);
			addSession(BEGIN_BLOCK_ENDPOINT, uri, BeginBlockEndpoint::new);
			addSession(DELIVER_TRANSACTION_ENDPOINT, uri, DeliverTransactionEndpoint::new);
			addSession(END_BLOCK_ENDPOINT, uri, EndBlockEndpoint::new);
			addSession(COMMIT_BLOCK_ENDPOINT, uri, CommitBlockEndpoint::new);
			addSession(ABORT_BLOCK_ENDPOINT, uri, AbortBlockEndpoint::new);
			addSession(KEEP_FROM_ENDPOINT, uri, KeepFromEndpoint::new);
		}
		catch (IOException | DeploymentException e) {
			throw new ApplicationException(e);
		}
	}

	@Override
	protected void closeResources(CloseReason reason) throws ApplicationException {
		super.closeResources(reason);
		LOGGER.info(logPrefix + "closed with reason: " + reason);
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		if (message instanceof CheckPrologExtraResultMessage cperm)
			onCheckPrologExtraResult(cperm);
		else if (message instanceof CheckTransactionResultMessage ctrm)
			onCheckTransactionResult(ctrm);
		else if (message instanceof GetPriorityResultMessage gprm)
			onGetPriorityResult(gprm);
		else if (message instanceof GetRepresentationResultMessage grrm)
			onGetRepresentationResult(grrm);
		else if (message instanceof GetInitialStateIdResultMessage gisirm)
			onGetInitialStateIdResult(gisirm);
		else if (message instanceof BeginBlockResultMessage bbrm)
			onBeginBlockResult(bbrm);
		else if (message instanceof DeliverTransactionResultMessage dtrm)
			onDeliverTransactionResult(dtrm);
		else if (message instanceof EndBlockResultMessage ebrm)
			onEndBlockResult(ebrm);
		else if (message instanceof CommitBlockResultMessage cbrm)
			onCommitBlockResult(cbrm);
		else if (message instanceof AbortBlockResultMessage abrm)
			onAbortBlockResult(abrm);
		else if (message instanceof KeepFromResultMessage kfrm)
			onKeepFromResult(kfrm);
		else if (message != null && !(message instanceof ExceptionMessage)) {
			LOGGER.warning("unexpected message of class " + message.getClass().getName());
			return;
		}

		super.notifyResult(message);
	}

	@Override
	protected ClosedApplicationException mkExceptionIfClosed() {
		return new ClosedApplicationException();
	}

	@Override
	protected ApplicationException mkException(Exception cause) {
		return cause instanceof ApplicationException ae ? ae : new ApplicationException(cause);
	}

	@Override
	public boolean checkPrologExtra(byte[] extra) throws ApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendCheckPrologExtra(extra, id);
		return waitForResult(id, CheckPrologExtraResultMessage.class, TimeoutException.class, InterruptedException.class, ApplicationException.class);
	}

	/**
	 * Sends a {@link CheckPrologExtraMessage} to the application service.
	 * 
	 * @param extra the extra bytes in the message
	 * @param id the identifier of the message
	 * @throws ApplicationException if the application could not send the message
	 */
	protected void sendCheckPrologExtra(byte[] extra, String id) throws ApplicationException {
		sendObjectAsync(getSession(CHECK_PROLOG_EXTRA_ENDPOINT), CheckPrologExtraMessages.of(extra, id), ApplicationException::new);
	}

	/**
	 * Hook called when a {@link CheckPrologExtraResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onCheckPrologExtraResult(CheckPrologExtraResultMessage message) {}

	private class CheckPrologExtraEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, CheckPrologExtraResultMessages.Decoder.class, ExceptionMessages.Decoder.class, CheckPrologExtraMessages.Encoder.class);
		}
	}

	@Override
	public void checkTransaction(Transaction transaction) throws TransactionRejectedException, ApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendCheckTransaction(transaction, id);
		waitForResult(id, CheckTransactionResultMessage.class, TransactionRejectedException.class, TimeoutException.class, InterruptedException.class, ApplicationException.class);
	}

	/**
	 * Sends a {@link CheckTransactionMessage} to the application service.
	 * 
	 * @param transaction the transaction in the message
	 * @param id the identifier of the message
	 * @throws ApplicationException if the application could not send the message
	 */
	protected void sendCheckTransaction(Transaction transaction, String id) throws ApplicationException {
		try {
			sendObjectAsync(getSession(CHECK_TRANSACTION_ENDPOINT), CheckTransactionMessages.of(transaction, id));
		}
		catch (IOException e) {
			throw new ApplicationException(e);
		}
	}

	/**
	 * Hook called when a {@link CheckTransactionResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onCheckTransactionResult(CheckTransactionResultMessage message) {}

	private class CheckTransactionEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, CheckTransactionResultMessages.Decoder.class, ExceptionMessages.Decoder.class, CheckTransactionMessages.Encoder.class);
		}
	}

	@Override
	public long getPriority(Transaction transaction) throws TransactionRejectedException, ApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendGetPriority(transaction, id);
		return waitForResult(id, GetPriorityResultMessage.class, TransactionRejectedException.class, TimeoutException.class, InterruptedException.class, ApplicationException.class);
	}

	/**
	 * Sends a {@link GetPriorityMessage} to the application service.
	 * 
	 * @param transaction the transaction in the message
	 * @param id the identifier of the message
	 * @throws ApplicationException if the application could not send the message
	 */
	protected void sendGetPriority(Transaction transaction, String id) throws ApplicationException {
		sendObjectAsync(getSession(GET_PRIORITY_ENDPOINT), GetPriorityMessages.of(transaction, id), ApplicationException::new);
	}

	/**
	 * Hook called when a {@link GetPriorityResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onGetPriorityResult(GetPriorityResultMessage message) {}

	private class GetPriorityEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetPriorityResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetPriorityMessages.Encoder.class);
		}
	}

	@Override
	public String getRepresentation(Transaction transaction) throws TransactionRejectedException, ApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendGetRepresentation(transaction, id);
		return waitForResult(id, GetRepresentationResultMessage.class, TransactionRejectedException.class, TimeoutException.class, InterruptedException.class, ApplicationException.class);
	}

	/**
	 * Sends a {@link GetRepresentationMessage} to the application service.
	 * 
	 * @param transaction the transaction in the message
	 * @param id the identifier of the message
	 * @throws ApplicationException if the application could not send the message
	 */
	protected void sendGetRepresentation(Transaction transaction, String id) throws ApplicationException {
		sendObjectAsync(getSession(GET_REPRESENTATION_ENDPOINT), GetRepresentationMessages.of(transaction, id), ApplicationException::new);
	}

	/**
	 * Hook called when a {@link GetRepresentationResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onGetRepresentationResult(GetRepresentationResultMessage message) {}

	private class GetRepresentationEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetRepresentationResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetRepresentationMessages.Encoder.class);
		}
	}

	@Override
	public byte[] getInitialStateId() throws ApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendGetInitialStateId(id);
		return waitForResult(id, GetInitialStateIdResultMessage.class, TimeoutException.class, InterruptedException.class, ApplicationException.class);
	}

	/**
	 * Sends a {@link GetInitialStateIdMessage} to the application service.
	 * 
	 * @param id the identifier of the message
	 * @throws ApplicationException if the application could not send the message
	 */
	protected void sendGetInitialStateId(String id) throws ApplicationException {
		sendObjectAsync(getSession(GET_INITIAL_STATE_ID_ENDPOINT), GetInitialStateIdMessages.of(id), ApplicationException::new);
	}

	/**
	 * Hook called when a {@link GetInitialStateIdResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onGetInitialStateIdResult(GetInitialStateIdResultMessage message) {}

	private class GetInitialStateIdEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetInitialStateIdResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetInitialStateIdMessages.Encoder.class);
		}
	}

	@Override
	public int beginBlock(long height, LocalDateTime when, byte[] stateId) throws UnknownStateException, ApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendBeginBlock(height, when, stateId, id);
		return waitForResult(id, BeginBlockResultMessage.class, UnknownStateException.class, TimeoutException.class, InterruptedException.class, ApplicationException.class);
	}

	/**
	 * Sends a {@link BeginBlockMessage} to the application service.
	 * 
	 * @param height the height of the block whose transactions are being executed
	 * @param when the time at the beginning of the execution of the transactions in the block
	 * @param stateId the identifier of the state of the application at the beginning of the execution of
	 *                the transactions in the block
	 * @param id the identifier of the message
	 * @throws ApplicationException if the application could not send the message
	 */
	protected void sendBeginBlock(long height, LocalDateTime when, byte[] stateId, String id) throws ApplicationException {
		sendObjectAsync(getSession(BEGIN_BLOCK_ENDPOINT), BeginBlockMessages.of(height, when, stateId, id), ApplicationException::new);
	}

	/**
	 * Hook called when a {@link BeginBlockResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onBeginBlockResult(BeginBlockResultMessage message) {}

	private class BeginBlockEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, BeginBlockResultMessages.Decoder.class, ExceptionMessages.Decoder.class, BeginBlockMessages.Encoder.class);
		}
	}

	@Override
	public void deliverTransaction(int groupId, Transaction transaction) throws TransactionRejectedException, UnknownGroupIdException, ApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendDeliverTransaction(groupId, transaction, id);
		waitForResult(id, DeliverTransactionResultMessage.class, TransactionRejectedException.class, UnknownGroupIdException.class, TimeoutException.class, InterruptedException.class, ApplicationException.class);
	}

	/**
	 * Sends a {@link DeliverTransactionMessage} to the application service.
	 * 
	 * @param groupId the group identifier in the message
	 * @param transaction the transaction in the message
	 * @param id the identifier of the message
	 * @throws ApplicationException if the application could not send the message
	 */
	protected void sendDeliverTransaction(int groupId, Transaction transaction, String id) throws ApplicationException {
		sendObjectAsync(getSession(DELIVER_TRANSACTION_ENDPOINT), DeliverTransactionMessages.of(groupId, transaction, id), ApplicationException::new);
	}

	/**
	 * Hook called when a {@link DeliverTransactionResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onDeliverTransactionResult(DeliverTransactionResultMessage message) {}

	private class DeliverTransactionEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, DeliverTransactionResultMessages.Decoder.class, ExceptionMessages.Decoder.class, DeliverTransactionMessages.Encoder.class);
		}
	}

	@Override
	public byte[] endBlock(int groupId, Deadline deadline) throws ApplicationException, UnknownGroupIdException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendEndBlock(groupId, deadline, id);
		return waitForResult(id, EndBlockResultMessage.class, UnknownGroupIdException.class, TimeoutException.class, InterruptedException.class, ApplicationException.class);
	}

	/**
	 * Sends an {@link EndBlockMessage} to the application service.
	 * 
	 * @param groupId the identifier of the group of transactions in the message
	 * @param deadline the deadline in the message
	 * @param id the identifier of the message
	 * @throws ApplicationException if the application could not send the message
	 */
	protected void sendEndBlock(int groupId, Deadline deadline, String id) throws ApplicationException {
		sendObjectAsync(getSession(END_BLOCK_ENDPOINT), EndBlockMessages.of(groupId, deadline, id), ApplicationException::new);
	}

	/**
	 * Hook called when an {@link EndBlockResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onEndBlockResult(EndBlockResultMessage message) {}

	private class EndBlockEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, EndBlockResultMessages.Decoder.class, ExceptionMessages.Decoder.class, EndBlockMessages.Encoder.class);
		}
	}

	@Override
	public void commitBlock(int groupId) throws ApplicationException, UnknownGroupIdException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendCommitBlock(groupId, id);
		waitForResult(id, CommitBlockResultMessage.class, UnknownGroupIdException.class, TimeoutException.class, InterruptedException.class, ApplicationException.class);
	}

	/**
	 * Sends a {@link CommitBlockMessage} to the application service.
	 * 
	 * @param groupId the group identifier in the message
	 * @param id the identifier of the message
	 * @throws ApplicationException if the application could not send the message
	 */
	protected void sendCommitBlock(int groupId, String id) throws ApplicationException {
		sendObjectAsync(getSession(COMMIT_BLOCK_ENDPOINT), CommitBlockMessages.of(groupId, id), ApplicationException::new);
	}

	/**
	 * Hook called when a {@link CommitBlockResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onCommitBlockResult(CommitBlockResultMessage message) {}

	private class CommitBlockEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, CommitBlockResultMessages.Decoder.class, ExceptionMessages.Decoder.class, CommitBlockMessages.Encoder.class);
		}
	}

	@Override
	public void abortBlock(int groupId) throws ApplicationException, UnknownGroupIdException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendAbortBlock(groupId, id);
		waitForResult(id, AbortBlockResultMessage.class, UnknownGroupIdException.class, TimeoutException.class, InterruptedException.class, ApplicationException.class);
	}

	/**
	 * Sends an {@link AbortBlockMessage} to the application service.
	 * 
	 * @param groupId the group identifier in the message
	 * @param id the identifier of the message
	 * @throws ApplicationException if the application could not send the message
	 */
	protected void sendAbortBlock(int groupId, String id) throws ApplicationException {
		sendObjectAsync(getSession(ABORT_BLOCK_ENDPOINT), AbortBlockMessages.of(groupId, id), ApplicationException::new);
	}

	/**
	 * Hook called when an {@link AbortBlockResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onAbortBlockResult(AbortBlockResultMessage message) {}

	private class AbortBlockEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, AbortBlockResultMessages.Decoder.class, ExceptionMessages.Decoder.class, AbortBlockMessages.Encoder.class);
		}
	}

	@Override
	public void keepFrom(LocalDateTime start) throws ApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendKeepFrom(start, id);
		waitForResult(id, KeepFromResultMessage.class, TimeoutException.class, InterruptedException.class, ApplicationException.class);
	}

	/**
	 * Sends a {@link KeepFromMessage} to the application service.
	 * 
	 * @param start the limit time in the message, before which states can be garbage-collected
	 * @param id the identifier of the message
	 * @throws ApplicationException if the application could not send the message
	 */
	protected void sendKeepFrom(LocalDateTime start, String id) throws ApplicationException {
		sendObjectAsync(getSession(KEEP_FROM_ENDPOINT), KeepFromMessages.of(start, id), ApplicationException::new);
	}

	/**
	 * Hook called when a {@link KeepFromResultMessage} has been received.
	 * 
	 * @param message the message
	 */
	protected void onKeepFromResult(KeepFromResultMessage message) {}

	private class KeepFromEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, KeepFromResultMessages.Decoder.class, ExceptionMessages.Decoder.class, KeepFromMessages.Encoder.class);
		}
	}
}
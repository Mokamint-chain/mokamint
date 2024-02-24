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

import static io.mokamint.application.service.api.ApplicationService.CHECK_PROLOG_EXTRA_ENDPOINT;
import static io.mokamint.application.service.api.ApplicationService.CHECK_TRANSACTION_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
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
import io.mokamint.application.messages.CheckPrologExtraMessages;
import io.mokamint.application.messages.CheckPrologExtraResultMessages;
import io.mokamint.application.messages.CheckTransactionMessages;
import io.mokamint.application.messages.CheckTransactionResultMessages;
import io.mokamint.application.messages.api.CheckPrologExtraMessage;
import io.mokamint.application.messages.api.CheckPrologExtraResultMessage;
import io.mokamint.application.messages.api.CheckTransactionMessage;
import io.mokamint.application.messages.api.CheckTransactionResultMessage;
import io.mokamint.application.remote.api.RemoteApplication;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.nonce.api.Deadline;
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
	 * @throws DeploymentException if the remote application could not be deployed
	 * @throws IOException if the remote application could not be created
	 */
	public RemoteApplicationImpl(URI uri, long timeout) throws DeploymentException, IOException {
		super(timeout);

		this.logPrefix = "application remote(" + uri + "): ";

		addSession(CHECK_PROLOG_EXTRA_ENDPOINT, uri, CheckPrologExtraEndpoint::new);
		addSession(CHECK_TRANSACTION_ENDPOINT, uri, CheckTransactionEndpoint::new);
	}

	@Override
	public void close() throws ApplicationException, InterruptedException {
		super.close();
	}

	private RuntimeException unexpectedException(Exception e) {
		LOGGER.log(Level.SEVERE, logPrefix + "application remote: unexpected exception", e);
		return new RuntimeException("Unexpected exception", e);
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		if (message instanceof CheckPrologExtraResultMessage cperm)
			onCheckPrologExtraResult(cperm.get());
		else if (message instanceof CheckTransactionResultMessage ctrm)
			onCheckTransactionResult();
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
		if (cause instanceof ApplicationException ae)
			return ae;
		else
			return new ApplicationException(cause);
	}

	/**
	 * Determines if the given exception message deals with an exception that all
	 * methods of a node are expected to throw. These are
	 * {@code java.lang.TimeoutException}, {@code java.lang.InterruptedException}
	 * and {@link ClosedApplicationException}.
	 * 
	 * @param message the message
	 * @return true if and only if that condition holds
	 */
	private boolean processStandardExceptions(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return TimeoutException.class.isAssignableFrom(clazz) ||
			InterruptedException.class.isAssignableFrom(clazz) ||
			ApplicationException.class.isAssignableFrom(clazz);
	}

	@Override
	public boolean checkPrologExtra(byte[] extra) throws ApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendCheckPrologExtra(extra, id);
		try {
			return waitForResult(id, this::processCheckPrologExtraSuccess, this::processStandardExceptions);
		}
		catch (RuntimeException | TimeoutException | InterruptedException | ApplicationException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	/**
	 * Sends a {@link CheckPrologExtraMessage} to the application service.
	 * 
	 * @param extra the extra bytes in the message
	 * @param id the identifier of the message
	 * @throws ApplicationException if the application could not send the message
	 */
	protected void sendCheckPrologExtra(byte[] extra, String id) throws ApplicationException {
		try {
			sendObjectAsync(getSession(CHECK_PROLOG_EXTRA_ENDPOINT), CheckPrologExtraMessages.of(extra, id));
		}
		catch (IOException e) {
			throw new ApplicationException(e);
		}
	}

	private Boolean processCheckPrologExtraSuccess(RpcMessage message) {
		return message instanceof CheckPrologExtraResultMessage cperm ? cperm.get() : null;
	}

	/**
	 * Hook called when a {@link CheckPrologExtraResultMessage} has been received.
	 * 
	 * @param result the content of the message
	 */
	protected void onCheckPrologExtraResult(boolean result) {}

	private class CheckPrologExtraEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, CheckPrologExtraResultMessages.Decoder.class, ExceptionMessages.Decoder.class, CheckPrologExtraMessages.Encoder.class);
		}
	}

	@Override
	public void checkTransaction(Transaction transaction) throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException {
		ensureIsOpen();
		var id = nextId();
		sendCheckTransaction(transaction, id);
		try {
			waitForResult(id, this::processCheckTransactionSuccess, this::processCheckTransactionExceptions);
		}
		catch (RuntimeException | RejectedTransactionException | TimeoutException | InterruptedException | ApplicationException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
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

	private Boolean processCheckTransactionSuccess(RpcMessage message) {
		return message instanceof CheckTransactionResultMessage ctrm ? Boolean.TRUE : null;
	}

	private boolean processCheckTransactionExceptions(ExceptionMessage message) {
		var clazz = message.getExceptionClass();
		return RejectedTransactionException.class.isAssignableFrom(clazz) ||
			processStandardExceptions(message);
	}

	/**
	 * Hook called when a {@link CheckTransactionResultMessage} has been received.
	 */
	protected void onCheckTransactionResult() {}

	private class CheckTransactionEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, CheckTransactionResultMessages.Decoder.class, ExceptionMessages.Decoder.class, CheckTransactionMessages.Encoder.class);
		}
	}

	@Override
	public long getPriority(Transaction transaction)
			throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getRepresentation(Transaction transaction)
			throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public byte[] getInitialStateId() throws ApplicationException, TimeoutException, InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int beginBlock(long height, LocalDateTime when, byte[] stateId)
			throws UnknownStateException, ApplicationException, TimeoutException, InterruptedException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void deliverTransaction(int groupId, Transaction transaction) throws RejectedTransactionException,
			UnknownGroupIdException, ApplicationException, TimeoutException, InterruptedException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public byte[] endBlock(int groupId, Deadline deadline)
			throws ApplicationException, UnknownGroupIdException, TimeoutException, InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void commitBlock(int groupId)
			throws ApplicationException, UnknownGroupIdException, TimeoutException, InterruptedException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void abortBlock(int groupId)
			throws ApplicationException, UnknownGroupIdException, TimeoutException, InterruptedException {
		// TODO Auto-generated method stub
		
	}
}
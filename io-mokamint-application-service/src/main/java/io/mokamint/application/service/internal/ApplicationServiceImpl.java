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

package io.mokamint.application.service.internal;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.closeables.api.OnCloseHandler;
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.messages.CheckPrologExtraMessages;
import io.mokamint.application.messages.CheckPrologExtraResultMessages;
import io.mokamint.application.messages.CheckTransactionMessages;
import io.mokamint.application.messages.CheckTransactionResultMessages;
import io.mokamint.application.messages.api.CheckPrologExtraMessage;
import io.mokamint.application.messages.api.CheckTransactionMessage;
import io.mokamint.application.service.api.ApplicationService;
import io.mokamint.node.api.RejectedTransactionException;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * The implementation of an application service. It publishes endpoints at a URL,
 * where clients can connect to query the API of a Mokamint application.
 */
@ThreadSafe
public class ApplicationServiceImpl extends AbstractWebSocketServer implements ApplicationService {

	/**
	 * The application whose API is published.
	 */
	private final Application application;

	/**
	 * True if and only if this service has been closed already.
	 */
	private final AtomicBoolean isClosed = new AtomicBoolean();

	/**
	 * We need this intermediate definition since two instances of a method reference
	 * are not the same, nor equals.
	 */
	private final OnCloseHandler this_close = this::close;

	/**
	 * The prefix used in the log messages;
	 */
	private final String logPrefix;

	private final static Logger LOGGER = Logger.getLogger(ApplicationServiceImpl.class.getName());

	/**
	 * Creates a new service for the given application, at the given network port.
	 * 
	 * @param application the application
	 * @param port the port
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public ApplicationServiceImpl(Application application, int port) throws DeploymentException, IOException {
		this.application = application;
		this.logPrefix = "application service(ws://localhost:" + port + "): ";

		// if the application gets closed, then this service will be closed as well
		application.addOnCloseHandler(this_close);

		startContainer("", port,
			CheckPrologExtraEndpoint.config(this), CheckTransactionEndpoint.config(this)
		);

		LOGGER.info(logPrefix + "published");
	}

	@Override
	public void close() {
		if (!isClosed.getAndSet(true)) {
			application.removeOnCloseHandler(this_close);
			stopContainer();
			LOGGER.info(logPrefix + "closed");
		}
	}

	/**
	 * Sends an exception message to the given session.
	 * 
	 * @param session the session
	 * @param e the exception used to build the message
	 * @param id the identifier of the message to send
	 * @throws IOException if there was an I/O problem
	 */
	private void sendExceptionAsync(Session session, Exception e, String id) throws IOException {
		sendObjectAsync(session, ExceptionMessages.of(e, id));
	}

	protected void onCheckPrologExtra(CheckPrologExtraMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + CHECK_PROLOG_EXTRA_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, CheckPrologExtraResultMessages.of(application.checkPrologExtra(message.getExtra()), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ApplicationException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class CheckPrologExtraEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (CheckPrologExtraMessage message) -> getServer().onCheckPrologExtra(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, CheckPrologExtraEndpoint.class, CHECK_PROLOG_EXTRA_ENDPOINT,
				CheckPrologExtraMessages.Decoder.class, CheckPrologExtraResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onCheckTransaction(CheckTransactionMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + CHECK_TRANSACTION_ENDPOINT + " request");

		try {
			try {
				application.checkTransaction(message.getTransaction());
				sendObjectAsync(session, CheckTransactionResultMessages.of(message.getId()));
			}
			catch (RejectedTransactionException | TimeoutException | InterruptedException | ApplicationException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class CheckTransactionEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (CheckTransactionMessage message) -> getServer().onCheckTransaction(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, CheckTransactionEndpoint.class, CHECK_TRANSACTION_ENDPOINT,
				CheckTransactionMessages.Decoder.class, CheckTransactionResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}
}
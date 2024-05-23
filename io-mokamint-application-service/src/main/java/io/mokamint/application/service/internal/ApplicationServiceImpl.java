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
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.closeables.api.OnCloseHandler;
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.application.api.Application;
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
import io.mokamint.application.messages.api.AbortBlockMessage;
import io.mokamint.application.messages.api.BeginBlockMessage;
import io.mokamint.application.messages.api.CheckPrologExtraMessage;
import io.mokamint.application.messages.api.CheckTransactionMessage;
import io.mokamint.application.messages.api.CommitBlockMessage;
import io.mokamint.application.messages.api.DeliverTransactionMessage;
import io.mokamint.application.messages.api.EndBlockMessage;
import io.mokamint.application.messages.api.GetInitialStateIdMessage;
import io.mokamint.application.messages.api.GetPriorityMessage;
import io.mokamint.application.messages.api.GetRepresentationMessage;
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

		startContainer("", port,
			CheckPrologExtraEndpoint.config(this), CheckTransactionEndpoint.config(this),
			GetPriorityEndpoint.config(this), GetRepresentationEndpoint.config(this),
			GetInitialStateIdEndpoint.config(this), BeginBlockEndpoint.config(this),
			DeliverTransactionEndpoint.config(this), EndBlockEndpoint.config(this),
			CommitBlockEndpoint.config(this), AbortBlockEndpoint.config(this)
		);

		// if the application gets closed, then this service will be closed as well
		application.addOnCloseHandler(this_close);

		LOGGER.info(logPrefix + "published");
	}

	@Override
	protected void closeResources() {
		super.closeResources();
		application.removeOnCloseHandler(this_close);
		LOGGER.info(logPrefix + "closed");
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

	protected void onGetPriority(GetPriorityMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_PRIORITY_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetPriorityResultMessages.of(application.getPriority(message.getTransaction()), message.getId()));
			}
			catch (RejectedTransactionException | TimeoutException | InterruptedException | ApplicationException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class GetPriorityEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetPriorityMessage message) -> getServer().onGetPriority(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, GetPriorityEndpoint.class, GET_PRIORITY_ENDPOINT,
				GetPriorityMessages.Decoder.class, GetPriorityResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetRepresentation(GetRepresentationMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_REPRESENTATION_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetRepresentationResultMessages.of(application.getRepresentation(message.getTransaction()), message.getId()));
			}
			catch (RejectedTransactionException | TimeoutException | InterruptedException | ApplicationException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class GetRepresentationEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetRepresentationMessage message) -> getServer().onGetRepresentation(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, GetRepresentationEndpoint.class, GET_REPRESENTATION_ENDPOINT,
				GetRepresentationMessages.Decoder.class, GetRepresentationResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetInitialStateId(GetInitialStateIdMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_INITIAL_STATE_ID_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetInitialStateIdResultMessages.of(application.getInitialStateId(), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ApplicationException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class GetInitialStateIdEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetInitialStateIdMessage message) -> getServer().onGetInitialStateId(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, GetInitialStateIdEndpoint.class, GET_INITIAL_STATE_ID_ENDPOINT,
				GetInitialStateIdMessages.Decoder.class, GetInitialStateIdResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onBeginBlock(BeginBlockMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + BEGIN_BLOCK_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, BeginBlockResultMessages.of(application.beginBlock(message.getHeight(), message.getWhen(), message.getStateId()), message.getId()));
			}
			catch (UnknownStateException | TimeoutException | InterruptedException | ApplicationException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class BeginBlockEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (BeginBlockMessage message) -> getServer().onBeginBlock(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, BeginBlockEndpoint.class, BEGIN_BLOCK_ENDPOINT,
				BeginBlockMessages.Decoder.class, BeginBlockResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onDeliverTransaction(DeliverTransactionMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + DELIVER_TRANSACTION_ENDPOINT + " request");

		try {
			try {
				application.deliverTransaction(message.getGroupId(), message.getTransaction());
				sendObjectAsync(session, DeliverTransactionResultMessages.of(message.getId()));
			}
			catch (UnknownGroupIdException | RejectedTransactionException | TimeoutException | InterruptedException | ApplicationException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class DeliverTransactionEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (DeliverTransactionMessage message) -> getServer().onDeliverTransaction(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, DeliverTransactionEndpoint.class, DELIVER_TRANSACTION_ENDPOINT,
				DeliverTransactionMessages.Decoder.class, DeliverTransactionResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onEndBlock(EndBlockMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + END_BLOCK_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, EndBlockResultMessages.of(application.endBlock(message.getGroupId(), message.getDeadline()), message.getId()));
			}
			catch (UnknownGroupIdException | TimeoutException | InterruptedException | ApplicationException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class EndBlockEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (EndBlockMessage message) -> getServer().onEndBlock(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, EndBlockEndpoint.class, END_BLOCK_ENDPOINT,
				EndBlockMessages.Decoder.class, EndBlockResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onCommitBlock(CommitBlockMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + COMMIT_BLOCK_ENDPOINT + " request");

		try {
			try {
				application.commitBlock(message.getGroupId());
				sendObjectAsync(session, CommitBlockResultMessages.of(message.getId()));
			}
			catch (UnknownGroupIdException | TimeoutException | InterruptedException | ApplicationException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class CommitBlockEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (CommitBlockMessage message) -> getServer().onCommitBlock(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, CommitBlockEndpoint.class, COMMIT_BLOCK_ENDPOINT,
				CommitBlockMessages.Decoder.class, CommitBlockResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onAbortBlock(AbortBlockMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + ABORT_BLOCK_ENDPOINT + " request");

		try {
			try {
				application.abortBlock(message.getGroupId());
				sendObjectAsync(session, AbortBlockResultMessages.of(message.getId()));
			}
			catch (UnknownGroupIdException | TimeoutException | InterruptedException | ApplicationException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class AbortBlockEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (AbortBlockMessage message) -> getServer().onAbortBlock(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, AbortBlockEndpoint.class, ABORT_BLOCK_ENDPOINT,
				AbortBlockMessages.Decoder.class, AbortBlockResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}
}
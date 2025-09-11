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
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.closeables.api.OnCloseHandler;
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.hotmoka.websockets.server.AbstractRPCWebSocketServer;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ClosedApplicationException;
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
import io.mokamint.application.messages.GetBalanceMessages;
import io.mokamint.application.messages.GetBalanceResultMessages;
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
import io.mokamint.application.messages.api.AbortBlockMessage;
import io.mokamint.application.messages.api.BeginBlockMessage;
import io.mokamint.application.messages.api.CheckPrologExtraMessage;
import io.mokamint.application.messages.api.CheckTransactionMessage;
import io.mokamint.application.messages.api.CommitBlockMessage;
import io.mokamint.application.messages.api.DeliverTransactionMessage;
import io.mokamint.application.messages.api.EndBlockMessage;
import io.mokamint.application.messages.api.GetBalanceMessage;
import io.mokamint.application.messages.api.GetInitialStateIdMessage;
import io.mokamint.application.messages.api.GetPriorityMessage;
import io.mokamint.application.messages.api.GetRepresentationMessage;
import io.mokamint.application.messages.api.KeepFromMessage;
import io.mokamint.application.messages.api.PublishMessage;
import io.mokamint.application.service.api.ApplicationService;
import io.mokamint.node.api.TransactionRejectedException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * The implementation of an application service. It publishes endpoints at a URL,
 * where clients can connect to query the API of a Mokamint application.
 */
@ThreadSafe
public class ApplicationServiceImpl extends AbstractRPCWebSocketServer implements ApplicationService {

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
	 * @throws FailedDeploymentException if the service cannot be deployed
	 */
	public ApplicationServiceImpl(Application application, int port) throws FailedDeploymentException {
		this.application = application;
		this.logPrefix = "application service(ws://localhost:" + port + "): ";

		startContainer("", port,
				CheckPrologExtraEndpoint.config(this), CheckTransactionEndpoint.config(this),
				GetBalanceEndpoint.config(this),
				GetPriorityEndpoint.config(this), GetRepresentationEndpoint.config(this),
				GetInitialStateIdEndpoint.config(this), BeginBlockEndpoint.config(this),
				DeliverTransactionEndpoint.config(this), EndBlockEndpoint.config(this),
				CommitBlockEndpoint.config(this), AbortBlockEndpoint.config(this),
				KeepFromEndpoint.config(this), PublishEndpoint.config(this)
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

	@Override
	protected void processRequest(Session session, RpcMessage message) throws IOException, InterruptedException, TimeoutException {
		var id = message.getId();

		try {
			switch (message) {
			case CheckPrologExtraMessage cpem -> sendObjectAsync(session, CheckPrologExtraResultMessages.of(application.checkPrologExtra(cpem.getExtra()), id));
			case CheckTransactionMessage ctm -> {
				application.checkTransaction(ctm.getTransaction());
				sendObjectAsync(session, CheckTransactionResultMessages.of(id));
			}
			case GetBalanceMessage gbm -> sendObjectAsync(session, GetBalanceResultMessages.of(application.getBalance(gbm.getSignature(), gbm.getPublicKey()), id));
			case GetPriorityMessage gpm -> sendObjectAsync(session, GetPriorityResultMessages.of(application.getPriority(gpm.getTransaction()), id));
			case GetRepresentationMessage grm -> sendObjectAsync(session, GetRepresentationResultMessages.of(application.getRepresentation(grm.getTransaction()), id));
			case GetInitialStateIdMessage gism -> sendObjectAsync(session, GetInitialStateIdResultMessages.of(application.getInitialStateId(), id));
			case BeginBlockMessage bbm -> sendObjectAsync(session, BeginBlockResultMessages.of(application.beginBlock(bbm.getHeight(), bbm.getWhen(), bbm.getStateId()), id));
			case DeliverTransactionMessage dtm -> {
				application.deliverTransaction(dtm.getGroupId(), dtm.getTransaction());
				sendObjectAsync(session, DeliverTransactionResultMessages.of(id));
			}
			case EndBlockMessage ebm -> sendObjectAsync(session, EndBlockResultMessages.of(application.endBlock(ebm.getGroupId(), ebm.getDeadline()), id));
			case CommitBlockMessage cbm -> {
				application.commitBlock(cbm.getGroupId());
				sendObjectAsync(session, CommitBlockResultMessages.of(id));
			}
			case AbortBlockMessage abm -> {
				application.abortBlock(abm.getGroupId());
				sendObjectAsync(session, AbortBlockResultMessages.of(id));
			}
			case KeepFromMessage kfm -> {
				application.keepFrom(kfm.getStart());
				sendObjectAsync(session, KeepFromResultMessages.of(id));
			}
			case PublishMessage pm -> {
				application.publish(pm.getBlock());
				sendObjectAsync(session, PublishResultMessages.of(id));
			}
			default -> LOGGER.warning(logPrefix + "unexpected message of type " + message.getClass().getName());
			}
		}
		catch (TransactionRejectedException | UnknownStateException | UnknownGroupIdException e) {
			sendObjectAsync(session, ExceptionMessages.of(e, id));
		}
		catch (ClosedApplicationException e) {
			LOGGER.warning(logPrefix + "request processing failed since the serviced application has been closed: " + e.getMessage());
		}
	}

	protected void onGetBalance(GetBalanceMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_BALANCE_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	public static class GetBalanceEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (GetBalanceMessage message) -> server.onGetBalance(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, GetBalanceEndpoint.class, GET_BALANCE_ENDPOINT, GetBalanceMessages.Decoder.class, GetBalanceResultMessages.Encoder.class);
		}
	}

	protected void onCheckPrologExtra(CheckPrologExtraMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + CHECK_PROLOG_EXTRA_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	public static class CheckPrologExtraEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (CheckPrologExtraMessage message) -> server.onCheckPrologExtra(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, CheckPrologExtraEndpoint.class, CHECK_PROLOG_EXTRA_ENDPOINT, CheckPrologExtraMessages.Decoder.class, CheckPrologExtraResultMessages.Encoder.class);
		}
	}

	protected void onCheckTransaction(CheckTransactionMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + CHECK_TRANSACTION_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	public static class CheckTransactionEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (CheckTransactionMessage message) -> server.onCheckTransaction(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, CheckTransactionEndpoint.class, CHECK_TRANSACTION_ENDPOINT,
				CheckTransactionMessages.Decoder.class, CheckTransactionResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetPriority(GetPriorityMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_PRIORITY_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	public static class GetPriorityEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (GetPriorityMessage message) -> server.onGetPriority(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, GetPriorityEndpoint.class, GET_PRIORITY_ENDPOINT,
				GetPriorityMessages.Decoder.class, GetPriorityResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetRepresentation(GetRepresentationMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_REPRESENTATION_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	public static class GetRepresentationEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (GetRepresentationMessage message) -> server.onGetRepresentation(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, GetRepresentationEndpoint.class, GET_REPRESENTATION_ENDPOINT,
				GetRepresentationMessages.Decoder.class, GetRepresentationResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetInitialStateId(GetInitialStateIdMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_INITIAL_STATE_ID_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	public static class GetInitialStateIdEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (GetInitialStateIdMessage message) -> server.onGetInitialStateId(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, GetInitialStateIdEndpoint.class, GET_INITIAL_STATE_ID_ENDPOINT, GetInitialStateIdMessages.Decoder.class, GetInitialStateIdResultMessages.Encoder.class);
		}
	}

	protected void onBeginBlock(BeginBlockMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + BEGIN_BLOCK_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	public static class BeginBlockEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (BeginBlockMessage message) -> server.onBeginBlock(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, BeginBlockEndpoint.class, BEGIN_BLOCK_ENDPOINT,
				BeginBlockMessages.Decoder.class, BeginBlockResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onDeliverTransaction(DeliverTransactionMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + DELIVER_TRANSACTION_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	public static class DeliverTransactionEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (DeliverTransactionMessage message) -> server.onDeliverTransaction(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, DeliverTransactionEndpoint.class, DELIVER_TRANSACTION_ENDPOINT,
				DeliverTransactionMessages.Decoder.class, DeliverTransactionResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onEndBlock(EndBlockMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + END_BLOCK_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	public static class EndBlockEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (EndBlockMessage message) -> server.onEndBlock(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, EndBlockEndpoint.class, END_BLOCK_ENDPOINT,
				EndBlockMessages.Decoder.class, EndBlockResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onCommitBlock(CommitBlockMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + COMMIT_BLOCK_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	public static class CommitBlockEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (CommitBlockMessage message) -> server.onCommitBlock(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, CommitBlockEndpoint.class, COMMIT_BLOCK_ENDPOINT,
				CommitBlockMessages.Decoder.class, CommitBlockResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onAbortBlock(AbortBlockMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + ABORT_BLOCK_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	public static class AbortBlockEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (AbortBlockMessage message) -> server.onAbortBlock(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, AbortBlockEndpoint.class, ABORT_BLOCK_ENDPOINT,
				AbortBlockMessages.Decoder.class, AbortBlockResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onKeepFrom(KeepFromMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + KEEP_FROM_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	public static class KeepFromEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (KeepFromMessage message) -> server.onKeepFrom(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, KeepFromEndpoint.class, KEEP_FROM_ENDPOINT, KeepFromMessages.Decoder.class, KeepFromResultMessages.Encoder.class);
		}
	}

	protected void onPublish(PublishMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + PUBLISH_ENDPOINT + " request");
		scheduleRequest(session, message);
	};

	public static class PublishEndpoint extends AbstractServerEndpoint<ApplicationServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			addMessageHandler(session, (PublishMessage message) -> server.onPublish(message, session));
	    }

		private static ServerEndpointConfig config(ApplicationServiceImpl server) {
			return simpleConfig(server, PublishEndpoint.class, PUBLISH_ENDPOINT, PublishMessages.Decoder.class, PublishResultMessages.Encoder.class);
		}
	}
}
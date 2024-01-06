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

package io.mokamint.node.service.internal;

import static io.hotmoka.exceptions.CheckSupplier.check;
import static io.hotmoka.exceptions.UncheckFunction.uncheck;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.node.SanitizedStrings;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.Node.CloseHandler;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.Whispered;
import io.mokamint.node.api.WhisperedBlock;
import io.mokamint.node.api.WhisperedPeer;
import io.mokamint.node.api.WhisperedTransaction;
import io.mokamint.node.api.Whisperer;
import io.mokamint.node.messages.AddTransactionMessages;
import io.mokamint.node.messages.AddTransactionResultMessages;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.GetBlockDescriptionMessages;
import io.mokamint.node.messages.GetBlockDescriptionResultMessages;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoMessages;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetChainPortionMessages;
import io.mokamint.node.messages.GetChainPortionResultMessages;
import io.mokamint.node.messages.GetConfigMessages;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetInfoMessages;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.GetMempoolInfoMessages;
import io.mokamint.node.messages.GetMempoolInfoResultMessages;
import io.mokamint.node.messages.GetMempoolPortionMessages;
import io.mokamint.node.messages.GetMempoolPortionResultMessages;
import io.mokamint.node.messages.GetMinerInfosMessages;
import io.mokamint.node.messages.GetMinerInfosResultMessages;
import io.mokamint.node.messages.GetPeerInfosMessages;
import io.mokamint.node.messages.GetPeerInfosResultMessages;
import io.mokamint.node.messages.GetTaskInfosMessages;
import io.mokamint.node.messages.GetTaskInfosResultMessages;
import io.mokamint.node.messages.GetTransactionRepresentationMessages;
import io.mokamint.node.messages.GetTransactionRepresentationResultMessages;
import io.mokamint.node.messages.WhisperBlockMessages;
import io.mokamint.node.messages.WhisperPeerMessages;
import io.mokamint.node.messages.WhisperTransactionMessages;
import io.mokamint.node.messages.WhisperedMemories;
import io.mokamint.node.messages.api.AddTransactionMessage;
import io.mokamint.node.messages.api.GetBlockDescriptionMessage;
import io.mokamint.node.messages.api.GetBlockMessage;
import io.mokamint.node.messages.api.GetChainInfoMessage;
import io.mokamint.node.messages.api.GetChainPortionMessage;
import io.mokamint.node.messages.api.GetConfigMessage;
import io.mokamint.node.messages.api.GetInfoMessage;
import io.mokamint.node.messages.api.GetMempoolInfoMessage;
import io.mokamint.node.messages.api.GetMempoolPortionMessage;
import io.mokamint.node.messages.api.GetMinerInfosMessage;
import io.mokamint.node.messages.api.GetPeerInfosMessage;
import io.mokamint.node.messages.api.GetTaskInfosMessage;
import io.mokamint.node.messages.api.GetTransactionRepresentationMessage;
import io.mokamint.node.messages.api.WhisperBlockMessage;
import io.mokamint.node.messages.api.WhisperPeerMessage;
import io.mokamint.node.messages.api.WhisperTransactionMessage;
import io.mokamint.node.messages.api.WhisperingMemory;
import io.mokamint.node.service.api.PublicNodeService;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * The implementation of a public node service. It publishes endpoints at a URL,
 * where clients can connect to query the public API of a Mokamint node.
 */
@ThreadSafe
public class PublicNodeServiceImpl extends AbstractWebSocketServer implements PublicNodeService {

	/**
	 * The node whose API is published.
	 */
	private final PublicNode node;

	/**
	 * The configuration of {@link #node}.
	 */
	private final ConsensusConfig<?,?> config;

	/**
	 * The hasher for the transactions.
	 */
	private final Hasher<Transaction> hasherForTransactions;

	/**
	 * The public URI of the machine where this service is running. If this is missing,
	 * the URI of the machine will not be suggested as a peer for the connected remotes.
	 */
	private final Optional<URI> uri;

	/**
	 * The sessions connected to the {@link WhisperPeerEndpoint}.
	 */
	private final Set<Session> whisperPeerSessions = ConcurrentHashMap.newKeySet();

	/**
	 * The sessions connected to the {@link WhisperBlockEndpoint}.
	 */
	private final Set<Session> whisperBlockSessions = ConcurrentHashMap.newKeySet();

	/**
	 * The sessions connected to the {@link WhisperTransactionEndpoint}.
	 */
	private final Set<Session> whisperTransactionSessions = ConcurrentHashMap.newKeySet();

	/**
	 * We need this intermediate definition since two instances of a method reference
	 * are not the same, nor equals.
	 */
	private final CloseHandler this_close = this::close;

	/**
	 * A memory of the last whispered messages,
	 * This is used to avoid whispering already whispered messages again.
	 */
	private final WhisperingMemory alreadyWhispered;

	/**
	 * True if and only if this service has been closed already.
	 */
	private final AtomicBoolean isClosed = new AtomicBoolean();

	/**
	 * The prefix used in the log messages;
	 */
	private final String logPrefix;

	private final Predicate<Whisperer> isThis = Predicate.isEqual(this);

	private final static Logger LOGGER = Logger.getLogger(PublicNodeServiceImpl.class.getName());

	/**
	 * Creates a new service for the given node, at the given network port.
	 * 
	 * @param node the node
	 * @param port the port
	 * @param peerBroadcastInterval the time interval, in milliseconds, between successive
	 *                              broadcasts of the public IP of the service. Every such internal,
	 *                              the service will whisper its IP to its connected peers,
	 *                              in order to publish its willingness to become a peer
	 * @param whisperedMessagesSize the size of the memory used to avoid whispering the same
	 *                              message again; higher numbers reduce the circulation of
	 *                              spurious messages
	 * @param uri the public URI of the machine where this service is running
	 *            (including {@code ws://} and the port number, if any);
	 *            if missing, the service will try to determine the public IP of the machine and
	 *            will use it as its URI, adding {@code port} as port number; note that
	 *            the port in {@code uri} and {@code port} might be different, since the
	 *            former is the port of the service as seen from the public Internet, while
	 *            the latter is the port of the service in the local machine where it runs;
	 *            these two might differ if the service runs inside a docker container
	 *            that maps ports
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public PublicNodeServiceImpl(PublicNode node, int port, long peerBroadcastInterval, int whisperedMessagesSize, Optional<URI> uri) throws DeploymentException, IOException {
		this.node = node;
		this.logPrefix = "public service(ws://localhost:" + port + "): ";

		try {
			this.config = node.getConfig();
		}
		catch (TimeoutException | InterruptedException | ClosedNodeException e) {
			throw new IOException(e);
		}

		this.hasherForTransactions = config.getHashingForTransactions().getHasher(Transaction::toByteArray);
		this.alreadyWhispered = WhisperedMemories.of(whisperedMessagesSize);
		this.uri = check(DeploymentException.class, () -> uri.or(() -> determinePublicURI().map(uncheck(u -> addPort(u, port)))));

		// if the node gets closed, then this service will be closed as well
		node.addOnClosedHandler(this_close);

		startContainer("", port,
			GetInfoEndpoint.config(this), GetPeerInfosEndpoint.config(this), GetMinerInfosEndpoint.config(this),
			GetTaskInfosEndpoint.config(this), GetBlockEndpoint.config(this), GetBlockDescriptionEndpoint.config(this),
			GetConfigEndpoint.config(this), GetChainInfoEndpoint.config(this), GetChainPortionEndpoint.config(this),
			GetMempoolInfoEndpoint.config(this), GetMempoolPortionEndpoint.config(this), GetTransactionRepresentationEndpoint.config(this),
			AddTransactionEndpoint.config(this), WhisperPeerEndpoint.config(this), WhisperBlockEndpoint.config(this), WhisperTransactionEndpoint.config(this));

		// if the node receives a whispering, it will be forwarded to this service as well
		node.bindWhisperer(this);

		if (uri.isEmpty())
			LOGGER.info(logPrefix + "published");
		else
			LOGGER.info(logPrefix + "published with public URI: " + uri.get());
	}

	@Override
	public void close() throws InterruptedException {
		if (!isClosed.getAndSet(true)) {
			node.removeOnCloseHandler(this_close);
			node.unbindWhisperer(this);
			stopContainer();
			LOGGER.info(logPrefix + "closed");
		}
	}

	@Override
	public Optional<URI> getURI() {
		return uri;
	}

	@Override
	public void whisper(Whispered whispered, Predicate<Whisperer> seen, String description) {
		whisper(whispered, seen, null, description);
	}

	private void whisper(Whispered whispered, Predicate<Whisperer> seen, Session excluded, String description) {
		if (seen.test(this) || !alreadyWhispered.add(whispered))
			return;
	
		LOGGER.info(logPrefix + "got whispered " + description);

		Set<Session> sessions;
		if (whispered instanceof WhisperedPeer)
			sessions = whisperPeerSessions;
		else if (whispered instanceof WhisperedBlock)
			sessions = whisperBlockSessions;
		else if (whispered instanceof WhisperedTransaction)
			sessions = whisperTransactionSessions;
		else {
			LOGGER.log(Level.SEVERE, "unexpected whispered object of class " + whispered.getClass().getName());
			sessions = Collections.emptySet();
		}

		sessions.stream()
			.filter(Session::isOpen)
			.filter(session -> session != excluded)
			.forEach(s -> whisperToSession(s, whispered, description));
	
		node.whisper(whispered, seen.or(isThis), description);
	}

	private void whisperToSession(Session session, Whispered whispered, String description) {
		try {
			sendObjectAsync(session, whispered);
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot whisper " + description + " to session: it might be closed: " + e.getMessage());
		}
	}

	private static URI addPort(URI uri, int port) throws DeploymentException {
		try {
			return new URI(uri.toString() + ":" + port);
		}
		catch (URISyntaxException e) {
			throw new DeploymentException("The public URI of the machine seems incorrect", e);
		}
	}

	/**
	 * Tries to determine the public URI of the machine where this service is running.
	 * It is the public IP of the machine, if determinable, with {@code ws://} as prefix.
	 * 
	 * @return the public IP address of the machine, if it could be determined
	 */
	private Optional<URI> determinePublicURI() {
		LOGGER.info(logPrefix + "trying to determine the public IP of the local machine");
	
		String[] urls = {
				"http://checkip.amazonaws.com/",
				"https://ipv4.icanhazip.com/",
				"http://myexternalip.com/raw",
				"http://ipecho.net/plain"
		};
	
		for (var url: urls) {
			try (var br = new BufferedReader(new InputStreamReader(new URL(url).openStream()))) {
				String ip = br.readLine();
				LOGGER.info(logPrefix + url + " provided " + ip + " as the IP of the local machine");
				return Optional.of(new URI("ws://" + ip));
			}
			catch (IOException | URISyntaxException e) {
				LOGGER.log(Level.WARNING, logPrefix + url + " failed to provide an IP for the local machine: " + e.getMessage());
			}
		}
	
		LOGGER.warning(logPrefix + "cannot determine the IP of the local machine: its IP won't be propagated to its peers");
	
		return Optional.empty();
	}

	/**
	 * Sends an exception message to the given session.
	 * 
	 * @param session the session
	 * @param e the exception used to build the message
	 * @param id the identifier of the message to send
	 * @throws IOException if there was an I/O error
	 */
	private void sendExceptionAsync(Session session, Exception e, String id) throws IOException {
		sendObjectAsync(session, ExceptionMessages.of(e, id));
	}

	protected void onGetInfo(GetInfoMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_INFO_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetInfoResultMessages.of(node.getInfo(), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class GetInfoEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetInfoMessage message) -> getServer().onGetInfo(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetInfoEndpoint.class, GET_INFO_ENDPOINT,
					GetInfoMessages.Decoder.class, GetInfoResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetPeerInfos(GetPeerInfosMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_PEER_INFOS_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetPeerInfosResultMessages.of(node.getPeerInfos(), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class GetPeerInfosEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetPeerInfosMessage message) -> getServer().onGetPeerInfos(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetPeerInfosEndpoint.class, GET_PEER_INFOS_ENDPOINT,
					GetPeerInfosMessages.Decoder.class, GetPeerInfosResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetMinerInfos(GetMinerInfosMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_MINER_INFOS_ENDPOINT + " request");
	
		try {
			try {
				sendObjectAsync(session, GetMinerInfosResultMessages.of(node.getMinerInfos(), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	}

	public static class GetMinerInfosEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetMinerInfosMessage message) -> getServer().onGetMinerInfos(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetMinerInfosEndpoint.class, GET_MINER_INFOS_ENDPOINT,
					GetMinerInfosMessages.Decoder.class, GetMinerInfosResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetTaskInfos(GetTaskInfosMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_TASK_INFOS_ENDPOINT + " request");
	
		try {
			try {
				sendObjectAsync(session, GetTaskInfosResultMessages.of(node.getTaskInfos(), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	}

	public static class GetTaskInfosEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetTaskInfosMessage message) -> getServer().onGetTaskInfos(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetTaskInfosEndpoint.class, GET_TASK_INFOS_ENDPOINT,
				GetTaskInfosMessages.Decoder.class, GetTaskInfosResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetTransactionRepresentation(GetTransactionRepresentationMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_TRANSACTION_REPRESENTATION_ENDPOINT + " request");
	
		try {
			try {
				sendObjectAsync(session, GetTransactionRepresentationResultMessages.of(node.getTransactionRepresentation(message.getHash()), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException | NoSuchAlgorithmException | RejectedTransactionException | DatabaseException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	}

	public static class GetTransactionRepresentationEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetTransactionRepresentationMessage message) -> getServer().onGetTransactionRepresentation(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetTransactionRepresentationEndpoint.class, GET_TRANSACTION_REPRESENTATION_ENDPOINT,
				GetTransactionRepresentationMessages.Decoder.class, GetTransactionRepresentationResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetBlock(GetBlockMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_BLOCK_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetBlockResultMessages.of(node.getBlock(message.getHash()), message.getId()));
			}
			catch (DatabaseException | NoSuchAlgorithmException | TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class GetBlockEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetBlockMessage message) -> getServer().onGetBlock(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetBlockEndpoint.class, GET_BLOCK_ENDPOINT,
					GetBlockMessages.Decoder.class, GetBlockResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetBlockDescription(GetBlockDescriptionMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_BLOCK_DESCRIPTION_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetBlockDescriptionResultMessages.of(node.getBlockDescription(message.getHash()), message.getId()));
			}
			catch (DatabaseException | NoSuchAlgorithmException | TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class GetBlockDescriptionEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetBlockDescriptionMessage message) -> getServer().onGetBlockDescription(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetBlockDescriptionEndpoint.class, GET_BLOCK_DESCRIPTION_ENDPOINT,
					GetBlockDescriptionMessages.Decoder.class, GetBlockDescriptionResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetConfig(GetConfigMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_CONFIG_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetConfigResultMessages.of(node.getConfig(), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class GetConfigEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetConfigMessage message) -> getServer().onGetConfig(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetConfigEndpoint.class, GET_CONFIG_ENDPOINT,
					GetConfigMessages.Decoder.class, GetConfigResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetChainInfo(GetChainInfoMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_CHAIN_INFO_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetChainInfoResultMessages.of(node.getChainInfo(), message.getId()));
			}
			catch (TimeoutException | InterruptedException | DatabaseException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class GetChainInfoEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetChainInfoMessage message) -> getServer().onGetChainInfo(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetChainInfoEndpoint.class, GET_CHAIN_INFO_ENDPOINT,
					GetChainInfoMessages.Decoder.class, GetChainInfoResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetChainPortion(GetChainPortionMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_CHAIN_PORTION_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetChainPortionResultMessages.of(node.getChainPortion(message.getStart(), message.getCount()), message.getId()));
			}
			catch (TimeoutException | InterruptedException | DatabaseException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class GetChainPortionEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetChainPortionMessage message) -> getServer().onGetChainPortion(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetChainPortionEndpoint.class, GET_CHAIN_PORTION_ENDPOINT,
					GetChainPortionMessages.Decoder.class, GetChainPortionResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onAddTransaction(AddTransactionMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + ADD_TRANSACTION_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, AddTransactionResultMessages.of(node.add(message.getTransaction()), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException | RejectedTransactionException | DatabaseException | NoSuchAlgorithmException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class AddTransactionEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (AddTransactionMessage message) -> getServer().onAddTransaction(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, AddTransactionEndpoint.class, ADD_TRANSACTION_ENDPOINT,
					AddTransactionMessages.Decoder.class, AddTransactionResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetMempoolInfo(GetMempoolInfoMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_MEMPOOL_INFO_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetMempoolInfoResultMessages.of(node.getMempoolInfo(), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	};

	public static class GetMempoolInfoEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetMempoolInfoMessage message) -> getServer().onGetMempoolInfo(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetMempoolInfoEndpoint.class, GET_MEMPOOL_INFO_ENDPOINT,
				GetMempoolInfoMessages.Decoder.class, GetMempoolInfoResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetMempoolPortion(GetMempoolPortionMessage message, Session session) {
		LOGGER.info(logPrefix + "received a " + GET_MEMPOOL_PORTION_ENDPOINT + " request");
	
		try {
			try {
				sendObjectAsync(session, GetMempoolPortionResultMessages.of(node.getMempoolPortion(message.getStart(), message.getCount()), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot send to session: it might be closed: " + e.getMessage());
		}
	}

	public static class GetMempoolPortionEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetMempoolPortionMessage message) -> getServer().onGetMempoolPortion(message, session));
	    }

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, GetMempoolPortionEndpoint.class, GET_MEMPOOL_PORTION_ENDPOINT,
				GetMempoolPortionMessages.Decoder.class, GetMempoolPortionResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	public static class WhisperPeerEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			server.whisperPeerSessions.add(session);
			addMessageHandler(session, (WhisperPeerMessage message) -> server.whisper(message, _whisperer -> false, session, "peer " + SanitizedStrings.of(message.getPeer())));
		}

		@SuppressWarnings("resource")
		@Override
		public void onClose(Session session, CloseReason closeReason) {
			getServer().whisperPeerSessions.remove(session);
		}

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, WhisperPeerEndpoint.class, WHISPER_PEER_ENDPOINT, WhisperPeerMessages.Encoder.class, WhisperPeerMessages.Decoder.class);
		}
	}

	public static class WhisperBlockEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			server.whisperBlockSessions.add(session);
			addMessageHandler(session, (WhisperBlockMessage message) -> server.whisper(message, _whisperer -> false, session, "block " + message.getBlock().getHexHash(server.config.getHashingForBlocks())));
	    }

		@SuppressWarnings("resource")
		@Override
		public void onClose(Session session, CloseReason closeReason) {
			getServer().whisperBlockSessions.remove(session);
		}

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, WhisperBlockEndpoint.class, WHISPER_BLOCK_ENDPOINT, WhisperBlockMessages.Encoder.class, WhisperBlockMessages.Decoder.class);
		}
	}

	public static class WhisperTransactionEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			var server = getServer();
			server.whisperTransactionSessions.add(session);
			addMessageHandler(session, (WhisperTransactionMessage message) -> server.whisper(message, _whisperer -> false, session, "transaction " + Hex.toHexString(server.hasherForTransactions.hash(message.getTransaction()))));
	    }

		@SuppressWarnings("resource")
		@Override
		public void onClose(Session session, CloseReason closeReason) {
			getServer().whisperTransactionSessions.remove(session);
		}

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, WhisperTransactionEndpoint.class, WHISPER_TRANSACTION_ENDPOINT, WhisperTransactionMessages.Encoder.class, WhisperTransactionMessages.Decoder.class);
		}
	}
}
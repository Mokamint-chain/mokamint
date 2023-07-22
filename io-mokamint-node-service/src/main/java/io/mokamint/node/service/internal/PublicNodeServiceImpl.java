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
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.node.NodeInternals.CloseHandler;
import io.mokamint.node.Peers;
import io.mokamint.node.PublicNodeInternals;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.GetBlockMessage;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoMessage;
import io.mokamint.node.messages.GetChainInfoMessages;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetConfigMessage;
import io.mokamint.node.messages.GetConfigMessages;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetInfoMessage;
import io.mokamint.node.messages.GetInfoMessages;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.GetPeerInfosMessage;
import io.mokamint.node.messages.GetPeerInfosMessages;
import io.mokamint.node.messages.GetPeerInfosResultMessages;
import io.mokamint.node.messages.WhisperPeersMessage;
import io.mokamint.node.messages.WhisperPeersMessages;
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
	private final PublicNodeInternals node;

	/**
	 * The port of localhost, where this service is published.
	 */
	private final int port;

	/**
	 * The public URI of the machine where this service is running. If this is missing,
	 * the URI of the machine will not be suggested as a peer for the connected remotes.
	 */
	private final Optional<URI> uri;

	/**
	 * A service used to schedule periodic tasks.
	 */
	private final ScheduledExecutorService periodicTasks = Executors.newScheduledThreadPool(1);

	/**
	 * The sessions connected to the {@link WhisperPeersEndpoint}.
	 */
	private final Set<Session> whisperPeersSessions = ConcurrentHashMap.newKeySet();

	/**
	 * We need this intermediate definition since two instances of a method reference
	 * are not the same, nor equals.
	 */
	private final Consumer<Stream<Peer>> this_whisperPeersToAllConnectedRemotes = this::whisperPeersToAllConnectedRemotes;

	/**
	 * We need this intermediate definition since two instances of a method reference
	 * are not the same, nor equals.
	 */
	private final CloseHandler this_close = this::close;

	private final static Logger LOGGER = Logger.getLogger(PublicNodeServiceImpl.class.getName());

	/**
	 * Creates a new service for the given node, at the given network port.
	 * 
	 * @param node the node
	 * @param port the port
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
	public PublicNodeServiceImpl(PublicNodeInternals node, int port, Optional<URI> uri) throws DeploymentException, IOException {
		this.node = node;
		this.port = port;
		this.uri = check(DeploymentException.class, () -> uri.or(() -> determinePublicURI().map(uncheck(this::addPort))));

		// if the node gets closed, then this service will be closed as well
		node.addOnClosedHandler(this_close);
		// if the node has some peers to whisper, this service will propagate them to all connected remotes
		node.addOnWhisperPeersToServicesHandler(this_whisperPeersToAllConnectedRemotes);

		startContainer("", port,
			GetInfoEndpoint.config(this), GetPeerInfosEndpoint.config(this), GetBlockEndpoint.config(this),
			GetConfigEndpoint.config(this), GetChainInfoEndpoint.config(this), WhisperPeersEndpoint.config(this));

		periodicTasks.scheduleWithFixedDelay(this::whisperItself, 0L, 2000, TimeUnit.MILLISECONDS);

		if (uri.isEmpty())
			LOGGER.info("published a public node service at ws://localhost:" + port);
		else
			LOGGER.info("published a public node service at ws://localhost:" + port + " and public URI: " + uri.get());
	}

	@Override
	public void close() throws InterruptedException {
		periodicTasks.shutdownNow();
		node.removeOnCloseHandler(this_close);
		node.removeOnWhisperPeersToServicesHandler(this_whisperPeersToAllConnectedRemotes);
		stopContainer();
		periodicTasks.awaitTermination(10, TimeUnit.SECONDS);
		LOGGER.info("closed the public node service at ws://localhost:" + port);
	}

	public void whisperItself() {
		if (uri.isEmpty())
			LOGGER.warning("not whispering the service since its public URI is unknown");

		var itself = Peers.of(uri.get());
		node.whisperItselfToPeers(itself);
	}

	/**
	 * Whisper some peers to all remotes connected to this service.
	 * 
	 * @param peers the peers to whisper
	 */
	protected void whisperPeersToAllConnectedRemotes(Stream<Peer> peers) {
		if (uri.isPresent())
			peers = Stream.concat(peers, Stream.of(Peers.of(uri.get()))).distinct();
	
		var peersAsArray = peers.toArray(Peer[]::new);
	
		LOGGER.info("whispering peers " + Arrays.toString(peersAsArray) + " to " + whisperPeersSessions.size() + " sessions");

		whisperPeersSessions.stream()
			.filter(Session::isOpen)
			.forEach(openSession -> whisperPeersToSession(openSession, Stream.of(peersAsArray)));
	}

	private URI addPort(URI uri) throws DeploymentException {
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
		LOGGER.info("trying to determine the public IP of this machine");
	
		String[] urls = {
				"http://checkip.amazonaws.com/",
				"https://ipv4.icanhazip.com/",
				"http://myexternalip.com/raw",
				"http://ipecho.net/plain"
		};
	
		for (var url: urls) {
			try (var br = new BufferedReader(new InputStreamReader(new URL(url).openStream()))) {
				String ip = br.readLine();
				LOGGER.info(url + " provided " + ip + " as the IP of the local machine");
				return Optional.of(new URI("ws://" + ip));
			}
			catch (IOException | URISyntaxException e) {
				LOGGER.log(Level.WARNING, url + " failed to provide the IP of the local machine", e);
			}
		}
	
		LOGGER.warning("cannot determine the IP of the local machine: its IP won't be propagated to its peers");
	
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

	private void whisperPeersToSession(Session session, Stream<Peer> peers) {
		try {
			sendObjectAsync(session, WhisperPeersMessages.of(peers));
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot whisper peers to session: it might be closed", e);
		}
	}

	protected void onGetInfo(GetInfoMessage message, Session session) {
		LOGGER.info("received a " + GET_INFO_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetInfoResultMessages.of(node.getInfo(), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot send to session: it might be closed", e);
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
		LOGGER.info("received a " + GET_PEER_INFOS_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetPeerInfosResultMessages.of(node.getPeerInfos(), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot send to session: it might be closed", e);
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

	protected void onGetBlock(GetBlockMessage message, Session session) {
		LOGGER.info("received a " + GET_BLOCK_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetBlockResultMessages.of(node.getBlock(message.getHash()), message.getId()));
			}
			catch (DatabaseException | NoSuchAlgorithmException | TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot send to session: it might be closed", e);
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

	protected void onGetConfig(GetConfigMessage message, Session session) {
		LOGGER.info("received a " + GET_CONFIG_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetConfigResultMessages.of(node.getConfig(), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot send to session: it might be closed", e);
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
		LOGGER.info("received a " + GET_CHAIN_INFO_ENDPOINT + " request");

		try {
			try {
				sendObjectAsync(session, GetChainInfoResultMessages.of(node.getChainInfo(), message.getId()));
			}
			catch (TimeoutException | InterruptedException | NoSuchAlgorithmException | DatabaseException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot send to session: it might be closed", e);
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

	public static class WhisperPeersEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		@SuppressWarnings("resource")
		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			getServer().whisperPeersSessions.add(session);
			addMessageHandler(session, (WhisperPeersMessage message) -> getServer().node.whisperToPeers(message.getPeers()));
	    }

		@SuppressWarnings("resource")
		@Override
		public void onClose(Session session, CloseReason closeReason) {
			getServer().whisperPeersSessions.remove(session);
		}

		private static ServerEndpointConfig config(PublicNodeServiceImpl server) {
			return simpleConfig(server, WhisperPeersEndpoint.class, WHISPER_PEERS_ENDPOINT, WhisperPeersMessages.Encoder.class, WhisperPeersMessages.Decoder.class);
		}
	}
}
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
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.node.Peers;
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
import io.mokamint.node.messages.GetPeersMessage;
import io.mokamint.node.messages.GetPeersMessages;
import io.mokamint.node.messages.GetPeersResultMessages;
import io.mokamint.node.messages.SuggestPeersMessages;
import io.mokamint.node.service.api.PublicNodeService;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Partial implementation of a public node service. It publishes endpoints at a URL,
 * where clients can connect to query the public API of a Mokamint node.
 */
@ThreadSafe
public abstract class AbstractPublicNodeServiceImpl extends AbstractWebSocketServer implements PublicNodeService {

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
	 * The sessions connected to the {@link SuggestPeersEndpoint}.
	 */
	private final Set<Session> suggestPeersSessions = ConcurrentHashMap.newKeySet();

	private final static Logger LOGGER = Logger.getLogger(AbstractPublicNodeServiceImpl.class.getName());

	/**
	 * Creates a new server, at the given network port.
	 * 
	 * @param port the port
	 * @param uri the public URI of the machine where this service is running; if missing,
	 *            the service will try to determine the public IP of the machine and use it as its URI
	 * @throws DeploymentException if the service cannot be deployed
	 */
	protected AbstractPublicNodeServiceImpl(int port, Optional<URI> uri) throws DeploymentException {
		this.port = port;
		this.uri = check(DeploymentException.class, () -> uri.or(this::determinePublicURI).map(uncheck(this::addPort)));
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
	 * Deploys the service.
	 * 
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	protected void deploy() throws DeploymentException, IOException {
		startContainer("", port,
				GetInfoEndpoint.config(this),
				GetPeersEndpoint.config(this),
				GetBlockEndpoint.config(this),
				GetConfigEndpoint.config(this),
				GetChainInfoEndpoint.config(this),
				SuggestPeersEndpoint.config(this));

		LOGGER.info("published a public node service at ws://localhost:" + port);
	}

	protected void sendPeersSuggestion(Stream<Peer> peers) {
		// we add our own URL as a suggestion
		if (uri.isPresent())
			peers = Stream.concat(peers, Stream.of(Peers.of(uri.get())));

		var peersAsArray = peers.toArray(Peer[]::new);

		LOGGER.info("broadcasting peers " + Arrays.toString(peersAsArray) + " to " + suggestPeersSessions.size() + " sessions");

		suggestPeersSessions.stream()
			.filter(Session::isOpen)
			.forEach(openSession -> sendObjectAsync(openSession, SuggestPeersMessages.of(Stream.of(peersAsArray))));
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

	@Override
	public void close() {
		stopContainer();
		LOGGER.info("closed the public node service at ws://localhost:" + port);
	};

	protected void onGetPeers(GetPeersMessage message, Session session) {
		LOGGER.info("received a " + GET_PEERS_ENDPOINT + " request");
	}

	public static class GetPeersEndpoint extends AbstractServerEndpoint<AbstractPublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetPeersMessage message) -> getServer().onGetPeers(message, session));
	    }

		private static ServerEndpointConfig config(AbstractPublicNodeServiceImpl server) {
			return simpleConfig(server, GetPeersEndpoint.class, GET_PEERS_ENDPOINT,
					GetPeersMessages.Decoder.class, GetPeersResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetBlock(GetBlockMessage message, Session session) {
		LOGGER.info("received a " + GET_BLOCK_ENDPOINT + " request");
	}

	public static class GetBlockEndpoint extends AbstractServerEndpoint<AbstractPublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetBlockMessage message) -> getServer().onGetBlock(message, session));
	    }

		private static ServerEndpointConfig config(AbstractPublicNodeServiceImpl server) {
			return simpleConfig(server, GetBlockEndpoint.class, GET_BLOCK_ENDPOINT,
					GetBlockMessages.Decoder.class, GetBlockResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetConfig(GetConfigMessage message, Session session) {
		LOGGER.info("received a " + GET_CONFIG_ENDPOINT + " request");
	}

	public static class GetConfigEndpoint extends AbstractServerEndpoint<AbstractPublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetConfigMessage message) -> getServer().onGetConfig(message, session));
	    }

		private static ServerEndpointConfig config(AbstractPublicNodeServiceImpl server) {
			return simpleConfig(server, GetConfigEndpoint.class, GET_CONFIG_ENDPOINT,
					GetConfigMessages.Decoder.class, GetConfigResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetChainInfo(GetChainInfoMessage message, Session session) {
		LOGGER.info("received a " + GET_CHAIN_INFO_ENDPOINT + " request");
	}

	public static class GetChainInfoEndpoint extends AbstractServerEndpoint<AbstractPublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetChainInfoMessage message) -> getServer().onGetChainInfo(message, session));
	    }

		private static ServerEndpointConfig config(AbstractPublicNodeServiceImpl server) {
			return simpleConfig(server, GetChainInfoEndpoint.class, GET_CHAIN_INFO_ENDPOINT,
					GetChainInfoMessages.Decoder.class, GetChainInfoResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onGetInfo(GetInfoMessage message, Session session) {
		LOGGER.info("received a " + GET_INFO_ENDPOINT + " request");
	}

	public static class GetInfoEndpoint extends AbstractServerEndpoint<AbstractPublicNodeServiceImpl> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetInfoMessage message) -> getServer().onGetInfo(message, session));
	    }

		private static ServerEndpointConfig config(AbstractPublicNodeServiceImpl server) {
			return simpleConfig(server, GetInfoEndpoint.class, GET_INFO_ENDPOINT,
					GetInfoMessages.Decoder.class, GetInfoResultMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	public static class SuggestPeersEndpoint extends AbstractServerEndpoint<AbstractPublicNodeServiceImpl> {

		@SuppressWarnings("resource")
		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			getServer().suggestPeersSessions.add(session);
	    }

		@SuppressWarnings("resource")
		@Override
		public void onClose(Session session, CloseReason closeReason) {
			getServer().suggestPeersSessions.remove(session);
		}

		private static ServerEndpointConfig config(AbstractPublicNodeServiceImpl server) {
			return simpleConfig(server, SuggestPeersEndpoint.class, SUGGEST_PEERS_ENDPOINT, SuggestPeersMessages.Encoder.class);
		}
	}
}
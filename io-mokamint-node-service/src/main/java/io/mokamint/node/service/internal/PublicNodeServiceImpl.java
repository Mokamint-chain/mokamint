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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.PublicNodeInternals;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.GetBlockMessage;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoMessage;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetConfigMessage;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetInfoMessage;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.GetPeersMessage;
import io.mokamint.node.messages.GetPeersResultMessages;
import io.mokamint.node.messages.WhisperPeersMessages;
import io.mokamint.node.service.AbstractPublicNodeService;
import io.mokamint.node.service.internal.AbstractPublicNodeServiceImpl.WhisperPeersEndpoint;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * The implementation of a public node service. It publishes endpoints at a URL,
 * where clients can connect to query the public API of a Mokamint node.
 */
@ThreadSafe
public class PublicNodeServiceImpl extends AbstractPublicNodeService {

	/**
	 * The node whose API is published.
	 */
	private final PublicNodeInternals node;

	/**
	 * The public URI of the machine where this service is running. If this is missing,
	 * the URI of the machine will not be suggested as a peer for the connected remotes.
	 */
	private final Optional<URI> uri;

	/**
	 * We need this intermediate definition since two instances of a method reference
	 * are not the same, nor equals.
	 */
	private final Consumer<Stream<Peer>> this_whisperPeersToAllConnectedRemotes = this::whisperPeersToAllConnectedRemotes;

	/**
	 * We need this intermediate definition since two instances of a method reference
	 * are not the same, nor equals.
	 */
	private final Runnable this_close = this::close;

	private final static Logger LOGGER = Logger.getLogger(PublicNodeServiceImpl.class.getName());

	/**
	 * Creates a new service for the given node, at the given network port.
	 * 
	 * @param node the node
	 * @param port the port
	 * @param uri the public URI of the machine where this service is running; if missing,
	 *            the service will try to determine the public IP of the machine and use it as its URI
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public PublicNodeServiceImpl(PublicNodeInternals node, int port, Optional<URI> uri) throws DeploymentException, IOException {
		super(port, uri);

		this.node = node;
		this.uri = check(DeploymentException.class, () -> uri.or(this::determinePublicURI).map(uncheck(this::addPort)));

		// if the node gets closed, then this service will be closed as well
		node.addOnClosedHandler(this_close);
		// if the node has some peers to whisper, this service will propagate them to all connected remotes
		node.addOnWhisperPeersHandler(this_whisperPeersToAllConnectedRemotes);
		deploy();
	}

	@Override
	protected List<ServerEndpointConfig> mkEndpointsConfigs() {
		var result = super.mkEndpointsConfigs();
		result.add(WhisperPeersEndpoint.config(this));

		return result;
	}

	private URI addPort(URI uri) throws DeploymentException {
		try {
			return new URI(uri.toString() + ":" + getPort());
		}
		catch (URISyntaxException e) {
			throw new DeploymentException("The public URI of the machine seems incorrect", e);
		}
	}

	private void whisperPeersToSession(Session session, Stream<Peer> peers) {
		try {
			sendObjectAsync(session, WhisperPeersMessages.of(peers));
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot whisper peers to session: it might be closed", e);
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

	@Override
	public void close() {
		node.removeOnCloseHandler(this_close);
		node.removeOnWhisperPeersHandler(this_whisperPeersToAllConnectedRemotes);
		super.close();
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

	@Override
	protected void whisperPeersToWrappedNode(Stream<Peer> peers) {
		node.receiveWhisperedPeers(peers);
	}

	@Override
	protected void onGetInfo(GetInfoMessage message, Session session) {
		super.onGetInfo(message, session);

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

	@Override
	protected void onGetPeers(GetPeersMessage message, Session session) {
		super.onGetPeers(message, session);

		try {
			try {
				sendObjectAsync(session, GetPeersResultMessages.of(node.getPeerInfos(), message.getId()));
			}
			catch (TimeoutException | InterruptedException | ClosedNodeException e) {
				sendExceptionAsync(session, e, message.getId());
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot send to session: it might be closed", e);
		}
	};

	@Override
	protected void onGetBlock(GetBlockMessage message, Session session) {
		super.onGetBlock(message, session);

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

	@Override
	protected void onGetConfig(GetConfigMessage message, Session session) {
		super.onGetConfig(message, session);

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

	@Override
	protected void onGetChainInfo(GetChainInfoMessage message, Session session) {
		super.onGetChainInfo(message, session);

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
}
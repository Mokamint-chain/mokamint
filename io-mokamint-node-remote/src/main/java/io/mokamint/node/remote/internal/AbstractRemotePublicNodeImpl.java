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

package io.mokamint.node.remote.internal;

import static io.mokamint.node.service.api.PublicNodeService.GET_BLOCK_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_CHAIN_INFO_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_CONFIG_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_INFO_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.GET_PEERS_ENDPOINT;
import static io.mokamint.node.service.api.PublicNodeService.SUGGEST_PEERS_ENDPOINT;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.RpcMessage;
import io.mokamint.node.ListenerManager;
import io.mokamint.node.ListenerManagers;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.NodeListeners;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.ExceptionMessage;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessage;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoMessages;
import io.mokamint.node.messages.GetChainInfoResultMessage;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetConfigMessages;
import io.mokamint.node.messages.GetConfigResultMessage;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetInfoMessages;
import io.mokamint.node.messages.GetInfoResultMessage;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.GetPeersMessages;
import io.mokamint.node.messages.GetPeersResultMessage;
import io.mokamint.node.messages.GetPeersResultMessages;
import io.mokamint.node.messages.SuggestPeersMessage;
import io.mokamint.node.messages.SuggestPeersMessages;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * An implementation of a remote node that presents a programmatic interface
 * to a service for the public API of a Mokamint node.
 */
@ThreadSafe
public abstract class AbstractRemotePublicNodeImpl extends AbstractRemoteNode implements NodeListeners {

	/**
	 * The listeners called whenever a peer is added to this node.
	 */
	private final ListenerManager<Stream<Peer>> onPeersAddedListeners = ListenerManagers.mk();

	private final static Logger LOGGER = Logger.getLogger(AbstractRemotePublicNodeImpl.class.getName());

	/**
	 * Opens and yields a new remote node for the public API of a node.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	public AbstractRemotePublicNodeImpl(URI uri) throws DeploymentException, IOException {
		addSession(GET_PEERS_ENDPOINT, uri, GetPeersEndpoint::new);
		addSession(GET_BLOCK_ENDPOINT, uri, GetBlockEndpoint::new);
		addSession(GET_CONFIG_ENDPOINT, uri, GetConfigEndpoint::new);
		addSession(GET_CHAIN_INFO_ENDPOINT, uri, GetChainInfoEndpoint::new);
		addSession(GET_INFO_ENDPOINT, uri, GetInfoEndpoint::new);
		addSession(SUGGEST_PEERS_ENDPOINT, uri, SuggestPeersEndpoint::new);
	}

	@Override
	public void addOnPeerAddedListener(Consumer<Stream<Peer>> listener) {
		onPeersAddedListeners.addListener(listener);
	}

	@Override
	public void removeOnPeerAddedListener(Consumer<Stream<Peer>> listener) {
		onPeersAddedListeners.removeListener(listener);
	}

	@Override
	protected void notifyResult(RpcMessage message) {
		if (message instanceof GetInfoResultMessage girm)
			onGetInfoResult(girm.get());
		else if (message instanceof GetPeersResultMessage gprm)
			onGetPeersResult(gprm.get());
		else if (message instanceof GetBlockResultMessage gbrm)
			onGetBlockResult(gbrm.get());
		else if (message instanceof GetConfigResultMessage gcrm)
			onGetConfigResult(gcrm.get());
		else if (message instanceof GetChainInfoResultMessage gcirm)
			onGetChainInfoResult(gcirm.get());
		else if (message instanceof ExceptionMessage em)
			onException(em);
		else if (message == null)
			LOGGER.log(Level.SEVERE, "unexpected null message");
		else
			LOGGER.log(Level.SEVERE, "unexpected message of class " + message.getClass().getName());
	}

	protected void sendGetInfo(String id) {
		sendObjectAsync(getSession(GET_INFO_ENDPOINT), GetInfoMessages.of(id));
	}

	protected void sendGetPeers(String id) {
		sendObjectAsync(getSession(GET_PEERS_ENDPOINT), GetPeersMessages.of(id));
	}

	protected void sendGetBlock(byte[] hash, String id) {
		sendObjectAsync(getSession(GET_BLOCK_ENDPOINT), GetBlockMessages.of(hash, id));
	}

	protected void sendGetConfig(String id) {
		sendObjectAsync(getSession(GET_CONFIG_ENDPOINT), GetConfigMessages.of(id));
	}

	protected void sendGetChainInfo(String id) {
		sendObjectAsync(getSession(GET_CHAIN_INFO_ENDPOINT), GetChainInfoMessages.of(id));
	}

	/**
	 * Handlers that can be overridden in subclasses.
	 */
	protected void onGetPeersResult(Stream<Peer> peers) {}
	protected void onGetBlockResult(Optional<Block> block) {}
	protected void onGetConfigResult(ConsensusConfig config) {}
	protected void onGetChainInfoResult(ChainInfo info) {}
	protected void onGetInfoResult(NodeInfo info) {}
	protected void onException(ExceptionMessage message) {}

	/**
	 * Called when the bound service suggests to add some peers.
	 * 
	 * @param message the message containing the suggested peers
	 */
	protected void onSuggestPeers(SuggestPeersMessage message) {
		onPeersAddedListeners.getListeners().forEach(listener -> listener.accept(message.getPeers()));
	}

	private class GetPeersEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetPeersResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetPeersMessages.Encoder.class);
		}
	}

	private class GetBlockEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetBlockResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetBlockMessages.Encoder.class);
		}
	}

	private class GetConfigEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetConfigResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetConfigMessages.Encoder.class);
		}
	}

	private class GetChainInfoEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetChainInfoResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetChainInfoMessages.Encoder.class);
		}
	}

	private class GetInfoEndpoint extends Endpoint {

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetInfoResultMessages.Decoder.class, ExceptionMessages.Decoder.class, GetInfoMessages.Encoder.class);
		}
	}

	private class SuggestPeersEndpoint extends Endpoint {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, AbstractRemotePublicNodeImpl.this::onSuggestPeers);
		}

		@Override
		protected Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, SuggestPeersMessages.Decoder.class);
		}
	}
}
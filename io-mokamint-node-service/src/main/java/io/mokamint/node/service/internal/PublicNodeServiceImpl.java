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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.node.Blocks;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.GetBlockMessage;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetPeersMessage;
import io.mokamint.node.messages.GetPeersMessages;
import io.mokamint.node.service.api.PublicNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EncodeException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

/**
 * The implementation of a public node service. It publishes an endpoint at a URL,
 * where clients can connect to query the public API of a Mokamint node.
 */
@ThreadSafe
public class PublicNodeServiceImpl extends AbstractWebSocketServer implements PublicNodeService {

	/**
	 * The port of localhost, where this service is listening.
	 */
	private final int port;

	/**
	 * The node whose API is published.
	 */
	private final PublicNode node;

	private final static Logger LOGGER = Logger.getLogger(PublicNodeServiceImpl.class.getName());

	public PublicNodeServiceImpl(PublicNode node, int port) throws DeploymentException, IOException {
		this.port = port;
		this.node = node;
		var configurator = new MyConfigurator();
    	var container = getContainer();
    	container.addEndpoint(GetPeersEndpoint.config(configurator));
    	container.addEndpoint(GetBlockEndpoint.config(configurator));
    	container.start("", port);
    	LOGGER.info("published a public node service at ws://localhost:" + port);
    	try {
			System.out.println(new GetPeersMessages.Encoder().encode(GetPeersMessages.instance()));
			System.out.println(new GetBlockMessages.Encoder().encode(GetBlockMessages.of(new byte[] { 1, 2, 3, 4 })));
		} catch (EncodeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void close() {
		super.close();
		LOGGER.info("closed the public node service at ws://localhost:" + port);
	}

	private class MyConfigurator extends Configurator {

    	@Override
    	public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
            var result = super.getEndpointInstance(endpointClass);
            ((PublicNodeServiceEndpoint) result).setServer(PublicNodeServiceImpl.this); // we inject the server

            return result;
        }
    }

	/**
	 * An endpoint that connects to public node remotes.
	 */
	private abstract static class PublicNodeServiceEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {
		protected Session session;
		protected final PublicNode node;

		@SuppressWarnings("resource")
		protected PublicNodeServiceEndpoint() {
			this.node = getServer().node;
		}

		@Override
	    public void onError(Session session, Throwable throwable) {
			LOGGER.log(Level.SEVERE, "websocket error", throwable);
	    }

		@Override
		protected void setServer(PublicNodeServiceImpl server) {
			super.setServer(server);
		}

		protected <M> void addMessageHandler(Session session, Consumer<M> action) {
			this.session = session;
			session.addMessageHandler((MessageHandler.Whole<M>) action::accept);
		}
	}

	public static class GetPeersEndpoint extends PublicNodeServiceEndpoint {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, this::getPeers);
	    }

		private void getPeers(GetPeersMessage message) {
			session.getAsyncRemote().sendObject(node.getPeers().toArray(Peer[]::new));
		}

		private static ServerEndpointConfig config(Configurator configurator) {
			return ServerEndpointConfig.Builder.create(GetPeersEndpoint.class, "/get_peers")
				.decoders(List.of(GetPeersMessages.Decoder.class)) // it receives GetPeerMessage's
				.encoders(List.of(Peers.Encoder.class)) // it sends Peer's
				.configurator(configurator)
				.build();
		}
	}

	public static class GetBlockEndpoint extends PublicNodeServiceEndpoint {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, this::getBlock);
	    }

		private void getBlock(GetBlockMessage message) {
			try {
				session.getAsyncRemote().sendObject(node.getBlock(message.getHash()));
			}
			catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
			}
		}

		private static ServerEndpointConfig config(Configurator configurator) {
			return ServerEndpointConfig.Builder.create(GetBlockEndpoint.class, "/get_block")
				.decoders(List.of(GetBlockMessages.Decoder.class)) // it receives GetBlockMessage's
				.encoders(List.of(Blocks.Encoder.class)) // it sends Block's
				.configurator(configurator)
				.build();
		}
	}
}
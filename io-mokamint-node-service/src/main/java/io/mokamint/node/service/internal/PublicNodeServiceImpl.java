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
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.GetBlockMessage;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetPeersMessage;
import io.mokamint.node.messages.GetPeersMessages;
import io.mokamint.node.service.api.PublicNodeService;
import jakarta.websocket.DeploymentException;
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
    	container.addEndpoint(PublicNodeServiceEndpoint.config(configurator));
    	container.start("", port);
    	LOGGER.info("published a public node service at ws://localhost:" + port);
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
	private static class PublicNodeServiceEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

		private final static Logger LOGGER = Logger.getLogger(PublicNodeServiceEndpoint.class.getName());

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			session.addMessageHandler((MessageHandler.Whole<GetPeersMessage>) this::getPeers);
			session.addMessageHandler((MessageHandler.Whole<GetBlockMessage>) this::getBlock);
	    }

		@SuppressWarnings("resource")
		private void getPeers(GetPeersMessage message) {
			getServer().node.getPeers();
		}

		@SuppressWarnings("resource")
		private void getBlock(GetBlockMessage message) {
			try {
				getServer().node.getBlock(message.getHash());
			}
			catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
			}
		}

		@Override
	    public void onError(Session session, Throwable throwable) {
			LOGGER.log(Level.SEVERE, "websocket error", throwable);
	    }

		private static ServerEndpointConfig config(Configurator configurator) {
			return ServerEndpointConfig.Builder.create(PublicNodeServiceEndpoint.class, "/")
				//.encoders(List.of(DeadlineDescriptions.Encoder.class)) // it sends DeadlineDescription's
				.decoders(List.of(GetPeersMessages.Decoder.class, GetBlockMessages.Decoder.class)) // and receives node messages
				.configurator(configurator)
				.build();
		}

		@Override
		protected void setServer(PublicNodeServiceImpl server) {
			super.setServer(server);
		}
	}
}
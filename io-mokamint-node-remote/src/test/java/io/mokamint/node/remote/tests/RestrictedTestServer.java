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

package io.mokamint.node.remote.tests;

import java.io.IOException;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.node.messages.AddPeersMessage;
import io.mokamint.node.messages.AddPeersMessages;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.RemovePeersMessage;
import io.mokamint.node.messages.RemovePeersMessages;
import io.mokamint.node.messages.VoidMessages;
import io.mokamint.node.service.api.RestrictedNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Test server implementation.
 */
@ThreadSafe
public class RestrictedTestServer extends AbstractWebSocketServer {

	/**
	 * Creates a new server, at the given network port.
	 * 
	 * @param port the port
	 * @throws DeploymentException if the service cannot be deployed
	 * @throws IOException if an I/O error occurs
	 */
	public RestrictedTestServer(int port) throws DeploymentException, IOException {
		startContainer("", port, AddPeersEndpoint.config(this), RemoveBlockEndpoint.config(this));
	}

	public void close() {
		stopContainer();
	};

	protected void onAddPeers(AddPeersMessage message, Session session) {}

	public static class AddPeersEndpoint extends AbstractServerEndpoint<RestrictedTestServer> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (AddPeersMessage message) -> getServer().onAddPeers(message, session));
	    }

		private static ServerEndpointConfig config(RestrictedTestServer server) {
			return simpleConfig(server, AddPeersEndpoint.class, RestrictedNodeService.ADD_PEERS_ENDPOINT,
					AddPeersMessages.Decoder.class, VoidMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}

	protected void onRemovePeers(RemovePeersMessage message, Session session) {}

	public static class RemoveBlockEndpoint extends AbstractServerEndpoint<RestrictedTestServer> {

		@Override
	    public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (RemovePeersMessage message) -> getServer().onRemovePeers(message, session));
	    }

		private static ServerEndpointConfig config(RestrictedTestServer server) {
			return simpleConfig(server, RemoveBlockEndpoint.class, RestrictedNodeService.REMOVE_PEERS_ENDPOINT,
					RemovePeersMessages.Decoder.class, VoidMessages.Encoder.class, ExceptionMessages.Encoder.class);
		}
	}
}
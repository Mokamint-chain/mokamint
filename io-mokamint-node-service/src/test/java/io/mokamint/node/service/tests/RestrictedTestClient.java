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

package io.mokamint.node.service.tests;

import java.io.IOException;
import java.net.URI;
import java.util.stream.Stream;

import io.hotmoka.websockets.beans.RpcMessage;
import io.hotmoka.websockets.client.AbstractClientEndpoint;
import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.AddPeersMessages;
import io.mokamint.node.messages.ExceptionMessage;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.RemovePeersMessages;
import io.mokamint.node.messages.VoidMessage;
import io.mokamint.node.messages.VoidMessages;
import io.mokamint.node.service.api.RestrictedNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * The implementation of a test websocket client that connects to a restricted node service.
 */
public class RestrictedTestClient extends AbstractWebSocketClient {
	private final Session addPeersSession;
	private final Session removePeersSession;

	public RestrictedTestClient(URI uri) throws DeploymentException, IOException {
		this.addPeersSession = new AddPeersEndpoint().deployAt(uri.resolve(RestrictedNodeService.ADD_PEERS_ENDPOINT));
		this.removePeersSession = new RemovePeersEndpoint().deployAt(uri.resolve(RestrictedNodeService.REMOVE_PEERS_ENDPOINT));
	}

	@Override
	public void close() throws IOException {
		addPeersSession.close();
		removePeersSession.close();
	}

	/**
	 * Handlers that can be overridden in subclasses.
	 */
	protected void onAddPeersResult() {}
	protected void onRemovePeersResult() {}
	protected void onException(ExceptionMessage message) {}

	public void sendAddPeers(Stream<Peer> peers) {
		sendObjectAsync(addPeersSession, AddPeersMessages.of(peers, "id"));
	}

	public void sendRemovePeers(Stream<Peer> peers) {
		sendObjectAsync(removePeersSession, RemovePeersMessages.of(peers, "id"));
	}

	private void dealWithExceptions(RpcMessage message) {
		if (message instanceof ExceptionMessage)
			onException((ExceptionMessage) message);
	}

	private class AddPeersEndpoint extends AbstractClientEndpoint<RestrictedTestClient> {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, VoidMessages.Decoder.class, ExceptionMessages.Decoder.class, AddPeersMessages.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (RpcMessage message) -> {
				if (message instanceof VoidMessage)
					onAddPeersResult();
				else
					dealWithExceptions(message);
			});
		}
	}

	private class RemovePeersEndpoint extends AbstractClientEndpoint<RestrictedTestClient> {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, VoidMessages.Decoder.class, ExceptionMessages.Decoder.class, RemovePeersMessages.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (RpcMessage message) -> {
				if (message instanceof VoidMessage)
					onRemovePeersResult();
				else
					dealWithExceptions(message);
			});
		}
	}
}
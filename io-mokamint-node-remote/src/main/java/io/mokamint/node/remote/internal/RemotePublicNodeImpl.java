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

import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.websockets.beans.RpcMessage;
import io.hotmoka.websockets.client.AbstractClientEndpoint;
import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.ExceptionResultMessage;
import io.mokamint.node.messages.ExceptionResultMessages;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessage;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetPeersMessages;
import io.mokamint.node.messages.GetPeersResultMessage;
import io.mokamint.node.messages.GetPeersResultMessages;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

public class RemotePublicNodeImpl extends AbstractWebSocketClient implements PublicNode {

	private final Session getPeersSession;
	private final Session getBlockSession;

	private final static Logger LOGGER = Logger.getLogger(RemotePublicNodeImpl.class.getName());

	/**
	 * Opens and yields a new remote node for the public API.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @return the new remote node
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	public RemotePublicNodeImpl(URI uri) throws DeploymentException, IOException {
		this.getPeersSession = new GetPeersEndpoint().deployAt(uri.resolve("/get_peers"));
		this.getBlockSession = new GetBlockEndpoint().deployAt(uri.resolve("/get_block"));
	}

	@Override
	public void close() {
		try {
			getPeersSession.close();
		}
		catch (IOException e) {}

		try {
			getBlockSession.close();
		}
		catch (IOException e) {}
	}

	@Override
	public Optional<Block> getBlock(byte[] hash) throws NoSuchAlgorithmException {
		sendObjectAsync(getBlockSession, GetBlockMessages.of(hash, "id"));
		return Optional.empty();
	}

	@Override
	public Stream<Peer> getPeers() {
		sendObjectAsync(getPeersSession, GetPeersMessages.of("id"));
		return null;
	}

	private class GetPeersEndpoint extends AbstractClientEndpoint<RemotePublicNodeImpl> {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetPeersResultMessages.Decoder.class, GetPeersMessages.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (GetPeersResultMessage message) -> message.get());
		}
	}

	private class GetBlockEndpoint extends AbstractClientEndpoint<RemotePublicNodeImpl> {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetBlockResultMessages.Decoder.class, ExceptionResultMessages.Decoder.class, GetBlockMessages.Encoder.class);
		}

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, (RpcMessage message) -> {
				if (message instanceof GetBlockResultMessage)
					((GetBlockResultMessage) message).get();
				else if (message instanceof ExceptionResultMessage)
					{} //onException.accept((ExceptionResultMessage) message);
				else if (message == null)
					LOGGER.warning("received unexpected null message");
				else
					LOGGER.warning("received unexpected message of class " + message.getClass().getName());
			});
		}
	}
}
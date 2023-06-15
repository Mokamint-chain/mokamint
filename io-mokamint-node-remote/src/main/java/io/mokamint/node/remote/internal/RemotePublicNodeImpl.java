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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
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
	private final AtomicInteger nextId = new AtomicInteger(0);
	private final ConcurrentMap<String, BlockingQueue<RpcMessage>> queues = new ConcurrentHashMap<>();

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

	private String nextId() {
		String id = String.valueOf(nextId.getAndIncrement());
		queues.put(id, new ArrayBlockingQueue<>(10));
		return id;
	}

	@Override
	public Optional<Block> getBlock(byte[] hash) throws NoSuchAlgorithmException {
		var id = nextId();
		sendObjectAsync(getBlockSession, GetBlockMessages.of(hash, id));
		try {
			return waitForResult(id, this::processGetBlockSuccess, this::processGetBlockException);
		}
		catch (RuntimeException | NoSuchAlgorithmException e) { // TODO
			throw e;
		}
		catch (Exception e) {
			LOGGER.log(Level.SEVERE, "unexpected exception", e);
			throw new RuntimeException("unexpected exception", e);
		}
	}

	private Optional<Block> processGetBlockSuccess(RpcMessage message) {
		return message instanceof GetBlockResultMessage ? ((GetBlockResultMessage) message).get() : null;
	}

	private boolean processGetBlockException(ExceptionResultMessage message) {
		return NoSuchAlgorithmException.class.isAssignableFrom(message.getExceptionClass());
	}

	@Override
	public Stream<Peer> getPeers() {
		var id = nextId();
		sendObjectAsync(getPeersSession, GetPeersMessages.of("id"));
		try {
			return waitForResult(id, this::processGetPeersSuccess, this::processGetPeersException);
		}
		catch (RuntimeException e) { // TODO
			throw e;
		}
		catch (Exception e) {
			LOGGER.log(Level.SEVERE, "unexpected exception", e);
			throw new RuntimeException("unexpected exception", e);
		}
	}

	private Stream<Peer> processGetPeersSuccess(RpcMessage message) {
		return message instanceof GetPeersResultMessage ? ((GetPeersResultMessage) message).get() : null;
	}

	private boolean processGetPeersException(ExceptionResultMessage message) {
		return false;
	}

	private <T> T waitForResult(String id, Function<RpcMessage, T> processSuccess, Predicate<ExceptionResultMessage> processException) throws Exception {
		final long waitingTime = 1000L;
		final long startTime = System.currentTimeMillis();

		do {
			try {
				RpcMessage message = queues.get(id).poll(waitingTime - (System.currentTimeMillis() - startTime), TimeUnit.MILLISECONDS);
				if (message == null) {
					queues.remove(id);
					throw new RuntimeException("time-out");
				}

				var result = processSuccess.apply(message);
				if (result != null) {
					queues.remove(id);
					return result;
				}

				if (message instanceof ExceptionResultMessage) {
					var erm = (ExceptionResultMessage) message;

					if (processException.test(erm)) {
						queues.remove(id);
						Exception exc;
						try {
							exc = erm.getExceptionClass().getConstructor(String.class).newInstance(erm.getMessage());
						}
						catch (Exception e) {
							LOGGER.log(Level.SEVERE, "cannot instantiate the exception type", e);
							continue;
						}

						throw exc;
					}

					LOGGER.warning("received unexpected exception of type " + ((ExceptionResultMessage) message).getExceptionClass().getName());
				}
				else
					LOGGER.warning("received unexpected message of type " + message.getClass().getName());
			}
			catch (InterruptedException e) {
				queues.remove(id);
				throw new RuntimeException(e);
			}
		}
		while (System.currentTimeMillis() - startTime < waitingTime);

		queues.remove(id);
		throw new RuntimeException("time-out");
	}

	private void notifyResult(RpcMessage message) {
		if (message != null) {
			var queue = queues.get(message.getId());
			if (queue != null) {
				if (!queue.offer(message))
					LOGGER.log(Level.SEVERE, "could not enqueue a message since the queue was full");
			}
			else
				LOGGER.log(Level.SEVERE, "received a message of type " + message.getClass().getName() + " but its id " + message.getId() + " has no corresponding waiting queue");
		}
	}

	private abstract class Endpoint extends AbstractClientEndpoint<RemotePublicNodeImpl> {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, RemotePublicNodeImpl.this::notifyResult);
		}
	}

	private class GetPeersEndpoint extends Endpoint {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetPeersResultMessages.Decoder.class, GetPeersMessages.Encoder.class);
		}
	}

	private class GetBlockEndpoint extends Endpoint {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetBlockResultMessages.Decoder.class, ExceptionResultMessages.Decoder.class, GetBlockMessages.Encoder.class);
		}
	}
}
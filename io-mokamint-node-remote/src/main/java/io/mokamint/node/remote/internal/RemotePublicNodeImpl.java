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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.RpcMessage;
import io.hotmoka.websockets.client.AbstractClientEndpoint;
import io.hotmoka.websockets.client.AbstractWebSocketClient;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.ExceptionResultMessage;
import io.mokamint.node.messages.ExceptionResultMessages;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessage;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetConfigMessages;
import io.mokamint.node.messages.GetConfigResultMessage;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetPeersMessages;
import io.mokamint.node.messages.GetPeersResultMessage;
import io.mokamint.node.messages.GetPeersResultMessages;
import io.mokamint.node.remote.RemotePublicNode;
import io.mokamint.node.service.api.PublicNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

/**
 * An implementation of a remote node that presents a programmatic interface
 * to a service for a Mokamint node.
 */
@ThreadSafe
public class RemotePublicNodeImpl extends AbstractWebSocketClient implements RemotePublicNode {
	private final long timeout;
	private final Session[] sessions = new Session[3];
	private final AtomicInteger nextId = new AtomicInteger();
	private final ConcurrentMap<String, BlockingQueue<RpcMessage>> queues = new ConcurrentHashMap<>();

	private final static Logger LOGGER = Logger.getLogger(RemotePublicNodeImpl.class.getName());

	/**
	 * Opens and yields a new remote node for the public API.
	 * 
	 * @param uri the URI of the network service that gets bound to the remote node
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a time-out exception is thrown
	 * @return the new remote node
	 * @throws DeploymentException if the remote node endpoints could not be deployed
	 * @throws IOException if the remote node could not be created
	 */
	public RemotePublicNodeImpl(URI uri, long timeout) throws DeploymentException, IOException {
		this.timeout = timeout;
		sessions[0] = new GetPeersEndpoint().deployAt(uri.resolve(PublicNodeService.GET_PEERS_ENDPOINT));
		sessions[1] = new GetBlockEndpoint().deployAt(uri.resolve(PublicNodeService.GET_BLOCK_ENDPOINT));
		sessions[2] = new GetConfigEndpoint().deployAt(uri.resolve(PublicNodeService.GET_CONFIG_ENDPOINT));
	}

	@Override
	public void close() throws IOException {
		IOException exception = null;

		for (var session: sessions) {
			try {
				session.close();
			}
			catch (IOException e) {
				exception = e;
			}
		}

		if (exception != null) {
			LOGGER.log(Level.WARNING, "cannot close the sessions", exception);
			throw exception;
		}
	}

	private String nextId() {
		String id = String.valueOf(nextId.getAndIncrement());
		queues.put(id, new ArrayBlockingQueue<>(10));
		return id;
	}

	@Override
	public Stream<Peer> getPeers() throws TimeoutException, InterruptedException {
		var id = nextId();
		sendObjectAsync(sessions[0], GetPeersMessages.of(id));
		try {
			return waitForResult(id, this::processGetPeersSuccess, this::processGetPeersException);
		}
		catch (RuntimeException | TimeoutException | InterruptedException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private Stream<Peer> processGetPeersSuccess(RpcMessage message) {
		return message instanceof GetPeersResultMessage ? ((GetPeersResultMessage) message).get() : null;
	}

	private boolean processGetPeersException(ExceptionResultMessage message) {
		var clazz = message.getExceptionClass();
		return TimeoutException.class.isAssignableFrom(clazz) || InterruptedException.class.isAssignableFrom(clazz);
	}

	@Override
	public Optional<Block> getBlock(byte[] hash) throws NoSuchAlgorithmException, TimeoutException, InterruptedException {
		var id = nextId();
		sendObjectAsync(sessions[1], GetBlockMessages.of(hash, id));
		try {
			return waitForResult(id, this::processGetBlockSuccess, this::processGetBlockException);
		}
		catch (RuntimeException | NoSuchAlgorithmException | TimeoutException | InterruptedException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private Optional<Block> processGetBlockSuccess(RpcMessage message) {
		return message instanceof GetBlockResultMessage ? ((GetBlockResultMessage) message).get() : null;
	}

	private boolean processGetBlockException(ExceptionResultMessage message) {
		var clazz = message.getExceptionClass();
		return NoSuchAlgorithmException.class.isAssignableFrom(clazz) ||
			TimeoutException.class.isAssignableFrom(clazz) ||
			InterruptedException.class.isAssignableFrom(clazz);
	}

	@Override
	public ConsensusConfig getConfig() throws NoSuchAlgorithmException, TimeoutException, InterruptedException {
		var id = nextId();
		sendObjectAsync(sessions[2], GetConfigMessages.of(id));
		try {
			return waitForResult(id, this::processGetConfigSuccess, this::processGetConfigException);
		}
		catch (RuntimeException | NoSuchAlgorithmException | TimeoutException | InterruptedException e) {
			throw e;
		}
		catch (Exception e) {
			throw unexpectedException(e);
		}
	}

	private ConsensusConfig processGetConfigSuccess(RpcMessage message) {
		return message instanceof GetConfigResultMessage ? ((GetConfigResultMessage) message).get() : null;
	}

	private boolean processGetConfigException(ExceptionResultMessage message) {
		var clazz = message.getExceptionClass();
		return NoSuchAlgorithmException.class.isAssignableFrom(clazz) ||
			TimeoutException.class.isAssignableFrom(clazz) ||
			InterruptedException.class.isAssignableFrom(clazz);
	}

	private <T> T waitForResult(String id, Function<RpcMessage, T> processSuccess, Predicate<ExceptionResultMessage> processException) throws Exception {
		final long startTime = System.currentTimeMillis();

		do {
			try {
				RpcMessage message = queues.get(id).poll(timeout - (System.currentTimeMillis() - startTime), TimeUnit.MILLISECONDS);
				if (message == null) {
					queues.remove(id);
					throw new TimeoutException();
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
				throw e;
			}
		}
		while (System.currentTimeMillis() - startTime < timeout);

		queues.remove(id);
		throw new TimeoutException();
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

	private static RuntimeException unexpectedException(Exception e) {
		LOGGER.log(Level.SEVERE, "unexpected exception", e);
		return new RuntimeException("unexpected exception", e);
	}

	private abstract class Endpoint extends AbstractClientEndpoint<RemotePublicNodeImpl> {

		@Override
		public void onOpen(Session session, EndpointConfig config) {
			addMessageHandler(session, RemotePublicNodeImpl.this::notifyResult);
		}
	}

	private class GetPeersEndpoint extends Endpoint {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetPeersResultMessages.Decoder.class, ExceptionResultMessages.Decoder.class, GetPeersMessages.Encoder.class);
		}
	}

	private class GetBlockEndpoint extends Endpoint {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetBlockResultMessages.Decoder.class, ExceptionResultMessages.Decoder.class, GetBlockMessages.Encoder.class);
		}
	}

	private class GetConfigEndpoint extends Endpoint {

		private Session deployAt(URI uri) throws DeploymentException, IOException {
			return deployAt(uri, GetConfigResultMessages.Decoder.class, ExceptionResultMessages.Decoder.class, GetConfigMessages.Encoder.class);
		}
	}
}
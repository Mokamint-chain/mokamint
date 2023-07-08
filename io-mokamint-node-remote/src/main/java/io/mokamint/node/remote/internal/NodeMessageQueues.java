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

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.RpcMessage;
import io.mokamint.node.messages.ExceptionMessage;

/**
 * Utility keeping queues of messages for each message id. When a message for that id arrives,
 * it gets dispatched to the waiting threads for that id.
 */
@ThreadSafe
class NodeMessageQueues {
	private final long timeout;
	private final AtomicInteger nextId = new AtomicInteger();
	private final ConcurrentMap<String, BlockingQueue<RpcMessage>> queues = new ConcurrentHashMap<>();

	private final static Logger LOGGER = Logger.getLogger(NodeMessageQueues.class.getName());

	/**
	 * Creates a utility keeping queues of messages for each message id.
	 * 
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 */
	NodeMessageQueues(long timeout) {
		this.timeout = timeout;
	}

	final String nextId() {
		String id = String.valueOf(nextId.getAndIncrement());
		queues.put(id, new ArrayBlockingQueue<>(10));
		return id;
	}

	final <T> T waitForResult(String id, Function<RpcMessage, T> processSuccess, Predicate<ExceptionMessage> processException) throws Exception {
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

				if (message instanceof ExceptionMessage em) {
					if (processException.test(em)) {
						queues.remove(id);
						Exception exc;
						try {
							exc = em.getExceptionClass().getConstructor(String.class).newInstance(em.getMessage());
						}
						catch (Exception e) {
							LOGGER.log(Level.SEVERE, "cannot instantiate the exception type", e);
							continue;
						}

						throw exc;
					}

					LOGGER.warning("received unexpected exception of type " + ((ExceptionMessage) message).getExceptionClass().getName());
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

	void notifyResult(RpcMessage message) {
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
}
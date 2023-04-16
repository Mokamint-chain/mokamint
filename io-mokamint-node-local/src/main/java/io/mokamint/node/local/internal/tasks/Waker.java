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

package io.mokamint.node.local.internal.tasks;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A synchronization primitive that allows to await a waker.
 * The waker can be set many times. Each new waker set replaces the
 * previous one, that gets discarded.
 */
public class Waker {

	/**
	 * A service used to schedule the waking-up moment.
	 */
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	/**
	 * The current waker, if any.
	 */
	private ScheduledFuture<?> waker;

	/**
	 * A lock to synchronize access to {@link #waker}.
	 */
	private final Object lock = new Object();

	/**
	 * The latch used to wait.
	 */
	private final CountDownLatch latch = new CountDownLatch(1);

	/**
	 * Awaits until the waker rings.
	 * 
	 * @throws InterruptedException if the thread is interrupted while waiting
	 */
	public void await() throws InterruptedException {
		latch.await();
	}

	/**
	 * Awaits until the waker rings or the given delay is over.
	 * 
	 * @param timeout the maximum time to wait
	 * @param unit the time unit of {@code timeout}
	 * @throws InterruptedException if the thread is interrupted while waiting
	 * @throws TimeoutException if the given {@code timeout} expired but the waker didn't ring
	 */
	public void await(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
		if (!latch.await(timeout, unit))
			throw new TimeoutException("timeout expired");
	}

	/**
	 * Sets a waker at the given time distance from now. If the waker was already set,
	 * it gets replaced with the new timeout.
	 * 
	 * @param millisecondsToWait the timeout to wait for
	 */
	public void set(long millisecondsToWait) {
		synchronized (lock) {
			if (waker != null)
				waker.cancel(true);

			waker = executor.schedule(latch::countDown, millisecondsToWait, TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Shuts down the internal executor of this object.
	 */
	public void shutdownNow() {
		executor.shutdownNow();
	}
}
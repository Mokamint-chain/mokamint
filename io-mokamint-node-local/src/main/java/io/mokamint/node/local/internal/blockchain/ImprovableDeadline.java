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

package io.mokamint.node.local.internal.blockchain;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.nonce.api.Deadline;

/**
 * A wrapper of a deadline, that can progressively improved.
 */
@ThreadSafe
public class ImprovableDeadline {

	@GuardedBy("lock")
	private Deadline deadline;
	private final Object lock = new Object();

	/**
	 * The latch used to wait for the first deadline to arrive.
	 */
	private final CountDownLatch latch = new CountDownLatch(1);

	/**
	 * Determines if the given deadline is better than this.
	 * 
	 * @param other the given deadline
	 * @return true if and only if this is not set yet or {@code other} is smaller than this
	 */
	public boolean isWorseThan(Deadline other) {
		synchronized (lock) {
			return deadline == null || other.compareByValue(deadline) < 0;
		}
	}

	/**
	 * Waits until the first deadline arrives.
	 *
	 * @throws InterruptedException if the thread is interrupted while waiting
	 */
	public void await() throws InterruptedException {
		latch.await();
	}

	/**
	 * Wait until the first deadline arrive, or the timeout expires.
	 * 
	 * @param timeout the timeout
	 * @param unit the time unit of {@code timeout}
	 * @throws InterruptedException if the thread is interrupted while waiting
	 * @throws TimeoutException if the timeout expired and no deadline arrived before
	 */
	public void await(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
		if (!latch.await(timeout, unit))
			throw new TimeoutException();
	}

	/**
	 * Updates this deadline if the given deadline is better.
	 * 
	 * @param other the given deadline
	 * @return true if and only if this deadline has been updated
	 */
	public boolean updateIfWorseThan(Deadline other) {
		synchronized (lock) {
			if (isWorseThan(other)) {
				deadline = other;
				latch.countDown();
				return true;
			}
			else
				return false;
		}
	}

	/**
	 * Yields the deadline wrapped by this object, if any,
	 * 
	 * @return the deadline wrapped by this object
	 */
	public Optional<Deadline> get() {
		synchronized (lock) {
			return Optional.ofNullable(deadline);
		}
	}
}
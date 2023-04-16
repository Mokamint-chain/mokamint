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

import java.util.Optional;

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
	 * Updates this deadline if the given deadline is better.
	 * 
	 * @param other the given deadline
	 * @return true if and only if this deadline has been updated
	 */
	public boolean updateIfWorseThan(Deadline other) {
		synchronized (lock) {
			if (isWorseThan(other)) {
				deadline = other;
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
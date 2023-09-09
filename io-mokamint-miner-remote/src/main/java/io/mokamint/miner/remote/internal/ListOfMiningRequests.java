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

package io.mokamint.miner.remote.internal;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;

/**
 * A list of requests still potentially to serve.
 */
@ThreadSafe
public class ListOfMiningRequests {
	
	@GuardedBy("lock")
	private final Deque<DeadlineDescription> descriptions = new LinkedList<>();

	@GuardedBy("lock")
	private final Deque<Consumer<Deadline>> actions = new LinkedList<>();

	private final Object lock = new Object();

	private final int max;

	/**
	 * Creates a list of requests of the given maximal size.
	 * When more requests than {@code max} are added, the oldest requests get removed.
	 */
	public ListOfMiningRequests(int max) {
		if (max <= 0)
			throw new IllegalArgumentException("max must be positive");

		this.max = max;
	}

	/**
	 * Adds a mining request. If there are already {@link #max} requests, the oldest
	 * gets removed to make space for the new arrival.
	 * 
	 * @param description the description of the requested deadline
	 * @param action the action to perform when a corresponding deadline is found
	 */
	public void add(DeadlineDescription description, Consumer<Deadline> action) {
		synchronized (lock) {
			if (descriptions.size() == max) {
				descriptions.removeFirst();
				actions.removeFirst();
			}

			descriptions.addLast(description);
			actions.addLast(action);
		}
	}

	/**
	 * Performs all actions when a deadline is found.
	 * 
	 * @param deadline the deadline
	 */
	public void runAllActionsFor(Deadline deadline) {
		var filtered = new ArrayList<Consumer<Deadline>>();

		synchronized (lock) {
			Iterator<Consumer<Deadline>> it = actions.iterator();
			for (var description: descriptions) {
				Consumer<Deadline> action = it.next();
				if (description.equals(deadline))
					filtered.add(action);
			}
		}

		filtered.forEach(action -> action.accept(deadline));
	}

	/**
	 * Runs the given code for the descriptions of the deadline still waiting to be computed.
	 * 
	 * @param what the code to run
	 */
	public void forAllDescriptions(Consumer<DeadlineDescription> what) {
		List<DeadlineDescription> copy;

		synchronized (lock) {
			copy = new ArrayList<>(descriptions);
		}

		copy.forEach(what);
	}
}
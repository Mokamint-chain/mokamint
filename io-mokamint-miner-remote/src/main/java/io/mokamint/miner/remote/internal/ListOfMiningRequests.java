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
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.miner.api.Miner;
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
	private final Deque<BiConsumer<Deadline, Miner>> actions = new LinkedList<>();

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
	public void add(DeadlineDescription description, BiConsumer<Deadline, Miner> action) {
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
	 * Yields the actions to perform when a deadline with the given description arrives.
	 * 
	 * @param description the description of the deadline
	 * @return the actions
	 */
	public Stream<BiConsumer<Deadline, Miner>> actionsFor(DeadlineDescription description) {
		List<BiConsumer<Deadline, Miner>> filtered = new ArrayList<>();

		synchronized (lock) {
			Iterator<BiConsumer<Deadline, Miner>> it = actions.iterator();
			for (var d: descriptions) {
				BiConsumer<Deadline, Miner> action = it.next();
				if (description.equals(d))
					filtered.add(action);
			}
		}

		return filtered.stream();
	}
}
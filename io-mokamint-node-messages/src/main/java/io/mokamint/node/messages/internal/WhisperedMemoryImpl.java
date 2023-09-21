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

package io.mokamint.node.messages.internal;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.api.Whispered;
import io.mokamint.node.messages.api.WhisperingMemory;

/**
 * Implementation of a memory of messages, that remembers that last inserted messages.
 * In this way, it is possible to know if a message has been already seen.
 * The test is incomplete, in general, since this memory has limited size.
 */
@ThreadSafe
public class WhisperedMemoryImpl implements WhisperingMemory {

	/**
	 * The size of the memory (number of whispered things that can be stored).
	 */
	private final long size;

	/**
	 * The lock for accessing {@link #seen} and {@link #elements}.
	 */
	private final Object lock = new Object();

	/**
	 * The whispered things added to this container.
	 */
	@GuardedBy("lock")
	private final Set<Whispered> seen = new HashSet<>();

	/**
	 * The whispered things added to this container, in order of addition.
	 */
	@GuardedBy("lock")
	private final Deque<Whispered> elements = new LinkedList<>();

	/**
	 * Creates a memory of the given size.
	 * 
	 * @param size the size (maximal number of stored things)
	 * @throws IllegalArgumentException if {@code size} is negative
	 */
	public WhisperedMemoryImpl(long size) {
		if (size < 0)
			throw new IllegalArgumentException("size cannot be negative");

		this.size = size;
	}

	@Override
	public boolean add(Whispered whispered) {
		synchronized (lock) {
			if (seen.add(whispered)) {
				elements.add(whispered);
				if (seen.size() > size) {
					var toRemove = elements.removeFirst();
					seen.remove(toRemove);
				}

				return true;
			}
			else
				return false;
		}
	}
}
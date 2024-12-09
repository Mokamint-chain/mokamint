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

package io.mokamint.node.internal;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.api.Memory;

/**
 * Implementation of a memory of things, that remembers that last inserted things.
 * In this way, it is possible to know if something has been already seen.
 * The test is incomplete, in general, since this memory has limited size.
 * 
 * @param <T> the type of the things remembered by this memory
 */
@ThreadSafe
public class MemoryImpl<T> implements Memory<T> {

	/**
	 * The size of the memory (number of things that can be stored).
	 */
	private final long size;

	/**
	 * The lock for accessing {@link #seen} and {@link #elements}.
	 */
	private final Object lock = new Object();

	/**
	 * The things added to this memory.
	 */
	@GuardedBy("lock")
	private final Set<T> seen = new HashSet<>();

	/**
	 * The things added to this memory, in order of addition.
	 */
	@GuardedBy("lock")
	private final Deque<T> elements = new LinkedList<>();

	/**
	 * Creates a memory of the given maximal size, initially empty.
	 * 
	 * @param maximalSize the maximal size (maximal number of stored things)
	 */
	public MemoryImpl(int maximalSize) {
		if (maximalSize < 0)
			throw new IllegalArgumentException("maximalSize cannot be negative");

		this.size = maximalSize;
	}

	@Override
	public boolean add(T element) {
		synchronized (lock) {
			boolean reachedMax = seen.size() == size;

			if (seen.add(element)) {
				elements.add(element);

				if (reachedMax)
					seen.remove(elements.removeFirst());

				return size > 0;
			}
			else
				return false;
		}
	}

	@Override
	public boolean remove(T element) {
		synchronized (lock) {
			if (seen.remove(element)) {
				elements.remove(element);
				return true;
			}
			else
				return false;
		}
	}

	@Override
	public Stream<T> stream() {
		synchronized (lock) {
			return new LinkedList<>(elements).stream();
		}
	}

	@Override
	public int size() {
		synchronized (lock) {
			return elements.size();
		}
	}
}
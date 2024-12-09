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

package io.mokamint.node.api;

import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;

/**
 * A memory of things, that remembers that last inserted things.
 * In this way, it is possible to know if something has been already seen.
 * The test is incomplete, in general, since this memory has limited size.
 * 
 * @param <T> the type of the things remembered by this memory
 */
@ThreadSafe
public interface Memory<T> {

	/**
	 * Adds the given element to this memory. If it is full already,
	 * then the oldest inserted element is discarded.
	 * 
	 * @param element the element to add
	 * @return true if and only if {@code element} was not in the container
	 *         and has been consequently added
	 */
	boolean add(T element);

	/**
	 * Removes the given element from this memory.
	 * 
	 * @param element the element to remove
	 * @return true if and only if the element has been removed
	 */
	boolean remove(T element);

	/**
	 * Yields the elements of this memory.
	 * 
	 * @return the elements
	 */
	Stream<T> stream();

	/**
	 * Yields the number of elements in this memory.
	 * 
	 * @return the number of elements in this memory
	 */
	int size();
}
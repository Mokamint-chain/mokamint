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

package io.mokamint.node.messages.api;

import io.hotmoka.annotations.ThreadSafe;

/**
 * A memory of whispered things, that remembers that last inserted things.
 * In this way, it is possible to know if something has been already whispered.
 * The test is incomplete, in general, since this memory has limited size.
 * 
 * @param <W> the type of the objects that get whispered
 */
@ThreadSafe
public interface WhisperingMemory<W> {

	/**
	 * Adds the given whispered thing to this container. If it is full already,
	 * then the oldest inserted thing is discarded.
	 * 
	 * @param whispered the whispered thing to add
	 * @return true if and only if {@code whispered} was not in the container
	 *              and has been consequently added
	 */
	boolean add(W whispered);
}
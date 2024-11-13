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

package io.mokamint.node;

import io.mokamint.node.api.Memory;
import io.mokamint.node.internal.MemoryImpl;

/**
 * A provider of memories of things.
 */
public final class Memories {
	
	private Memories() {}

	/**
	 * Yields a memory with the given maximal size.
	 * 
	 * @param <T> the type of the things that will be contained in the memory
	 * @param size the size (maximal number of stored elements)
	 * @return the memory
	 */
	public static <T> Memory<T> of(int size) {
		return new MemoryImpl<>(size);
	}
}
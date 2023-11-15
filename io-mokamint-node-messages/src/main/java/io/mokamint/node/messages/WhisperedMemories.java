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

package io.mokamint.node.messages;

import io.mokamint.node.messages.api.WhisperingMemory;
import io.mokamint.node.messages.internal.WhisperedMemoryImpl;

/**
 * A provider of memories of whispered things.
 */
public final class WhisperedMemories {
	
	private WhisperedMemories() {}

	/**
	 * Yields a memory with the given maximal size.
	 * 
	 * @param size the size (maximal number of stored whispered things)
	 * @return the memory
	 * @throws IllegalArgumentException if {@code size} is negative
	 */
	public static WhisperingMemory of(int size) {
		return new WhisperedMemoryImpl(size);
	}
}
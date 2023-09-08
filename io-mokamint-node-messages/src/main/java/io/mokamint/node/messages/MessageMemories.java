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

import io.mokamint.node.messages.api.MessageMemory;
import io.mokamint.node.messages.internal.MessageMemoryImpl;

/**
 * A provider of message memories.
 */
public final class MessageMemories {
	
	private MessageMemories() {}

	/**
	 * Yields a message memory of the given maximal size.
	 * 
	 * @param size the size (maximal number of stored messages)
	 * @return the message memory
	 * @throws IllegalArgumentException if {@code size} is negative
	 */
	public static MessageMemory of(long size) {
		return new MessageMemoryImpl(size);
	}
}
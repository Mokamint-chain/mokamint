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

import java.util.SortedSet;
import java.util.TreeSet;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.mokamint.node.messages.MessageMemory;

/**
 * Implementation of a memory of messages, that remembers that last inserted messages.
 * In this way, it is possible to know if a message has been already seen.
 * The test is incomplete, in general, since this memory has limited size.
 */
@ThreadSafe
public class MessageMemoryImpl implements MessageMemory {

	/**
	 * The size of the memory (number of messages that can be stored).
	 */
	private final long size;

	/**
	 * UUIDs of the messages added to this container.
	 */
	@GuardedBy("itself")
	private final SortedSet<String> seenUUIDs = new TreeSet<>();

	/**
	 * Creates a memory of the given size.
	 * 
	 * @param size the size (maximal number of stored messages)
	 * @throws IllegalArgumentException if {@code size} is negative
	 */
	public MessageMemoryImpl(long size) {
		if (size < 0)
			throw new IllegalArgumentException("size cannot be negative");

		this.size = size;
	}

	@Override
	public boolean add(RpcMessage message) {
		synchronized (seenUUIDs) {
			if (seenUUIDs.add(message.getId())) {
				if (seenUUIDs.size() > size)
					seenUUIDs.remove(seenUUIDs.first());

				return true;
			}
			else
				return false;
		}
	}
}
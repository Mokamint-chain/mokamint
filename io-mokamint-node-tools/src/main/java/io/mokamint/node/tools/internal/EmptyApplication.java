/*
Copyright 2024 Fausto Spoto

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

package io.mokamint.node.tools.internal;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import io.mokamint.application.api.Application;
import io.mokamint.application.api.Description;
import io.mokamint.application.api.Name;
import io.mokamint.node.api.Transaction;
import io.mokamint.nonce.api.Deadline;

/**
 * An empty Mokamint application. It can be used for experimenting with
 * the creation of new Mokamint chains.
 */
@Name("empty")
@Description("an application with no state, accepting all transactions, useful for experiments")
public class EmptyApplication implements Application {
	private final AtomicInteger nextId = new AtomicInteger();

	/**
	 * There is only one state (empty) and consequently only one hash.
	 */
	private final static byte[] STATE_HASH = new byte[] { 13, 1, 19, 73 };

	@Override
	public boolean checkPrologExtra(byte[] extra) {
		return true;
	}

	@Override
	public void checkTransaction(Transaction transaction) {
	}

	@Override
	public long getPriority(Transaction transaction) {
		return 0L;
	}

	@Override
	public byte[] getInitialStateHash() {
		return STATE_HASH;
	}

	@Override
	public int beginBlock(long height, byte[] stateHash, LocalDateTime creationStartDateTime) {
		return nextId.getAndIncrement();
	}

	@Override
	public void deliverTransaction(Transaction transaction, int id) {
	}

	@Override
	public byte[] endBlock(int id, Deadline deadline) {
		return STATE_HASH;
	}

	@Override
	public void commitBlock(int id) {
	}

	@Override
	public void abortBlock(int id) {
	}

	@Override
	public String getRepresentation(Transaction transaction) {
		return "[]";
	}

	@Override
	public void close() {
	}
}
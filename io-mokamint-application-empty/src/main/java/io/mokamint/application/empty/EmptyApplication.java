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

package io.mokamint.application.empty;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import io.hotmoka.closeables.api.OnCloseHandler;
import io.mokamint.application.AbstractApplication;
import io.mokamint.application.api.ClosedApplicationException;
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
public class EmptyApplication extends AbstractApplication {
	private final AtomicInteger nextId = new AtomicInteger();

	/**
	 * There is only one state (empty) and consequently only one identifier.
	 */
	private final static byte[] STATE_ID = new byte[] { 13, 1, 19, 73 };

	/**
	 * Creates an empty application.
	 */
	public EmptyApplication() {}

	@Override
	public boolean checkPrologExtra(byte[] extra) throws ClosedApplicationException {
		try (var scope = mkScope()) {
			return true;
		}
	}

	@Override
	public void checkTransaction(Transaction transaction) throws ClosedApplicationException {
		try (var scope = mkScope()) {}
	}

	@Override
	public long getPriority(Transaction transaction) throws ClosedApplicationException {
		try (var scope = mkScope()) {
			return 0L;
		}
	}

	@Override
	public byte[] getInitialStateId() throws ClosedApplicationException {
		try (var scope = mkScope()) {
			return STATE_ID;
		}
	}

	@Override
	public int beginBlock(long height, LocalDateTime creationStartDateTime, byte[] stateId) throws ClosedApplicationException {
		try (var scope = mkScope()) {
			return nextId.getAndIncrement();
		}
	}

	@Override
	public void deliverTransaction(int groupId, Transaction transaction) throws ClosedApplicationException {
		try (var scope = mkScope()) {}
	}

	@Override
	public byte[] endBlock(int groupId, Deadline deadline) throws ClosedApplicationException {
		try (var scope = mkScope()) {
			return STATE_ID;
		}
	}

	@Override
	public void commitBlock(int groupId) throws ClosedApplicationException {
		try (var scope = mkScope()) {}
	}

	@Override
	public void abortBlock(int groupId) throws ClosedApplicationException {
		try (var scope = mkScope()) {}
	}

	@Override
	public String getRepresentation(Transaction transaction) throws ClosedApplicationException {
		try (var scope = mkScope()) {
			return "[]";
		}
	}

	@Override
	public void keepFrom(LocalDateTime start) throws ClosedApplicationException {
		try (var scope = mkScope()) {}
	}

	@Override
	public void addOnCloseHandler(OnCloseHandler handler) {
	}

	@Override
	public void removeOnCloseHandler(OnCloseHandler handler) {
	}
}
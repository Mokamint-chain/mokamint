/*
Copyright 2026 Fausto Spoto

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

package io.mokamint.application.parity;

import java.math.BigInteger;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.mokamint.application.AbstractApplication;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.application.api.Description;
import io.mokamint.application.api.Name;
import io.mokamint.application.api.UnknownScopeIdException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.Request;
import io.mokamint.node.api.RequestRejectedException;
import io.mokamint.nonce.api.Deadline;

/**
 * A minimal Mokamint application. It keeps track of the parity of an integer value.
 * It is meant to exemplify the definition of a minimal application without database.
 */
@Name("Parity")
@Description("An application that keeps track of the parity of an integer value")
public class ParityApplication extends AbstractApplication {
	private final AtomicInteger nextId = new AtomicInteger();

	/**
	 * There state of the application that states that the integer is even.
	 */
	private final static byte[] EVEN_STATE_ID = new byte[] { 0 };

	/**
	 * There state of the application that states that the integer is odd.
	 */
	private final static byte[] ODD_STATE_ID = new byte[] { 1 };

	/**
	 * A map from each scope identifier to the current state of that scope identifier.
	 */
	private final ConcurrentMap<Integer, byte[]> currentStates = new ConcurrentHashMap<>();

	/**
	 * Creates a parity application.
	 */
	public ParityApplication() {}

	@Override
	public Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey publicKey) throws ClosedApplicationException {
		try (var scope = mkScope()) {
			return Optional.empty(); // balances have no meaning for this application
		}
	}

	@Override
	public boolean checkDeadline(Deadline deadline) throws ClosedApplicationException {
		try (var scope = mkScope()) {
			return true; // nothing to check
		}
	}

	@Override
	public void checkRequest(Request request) throws ClosedApplicationException, RequestRejectedException {
		try (var scope = mkScope()) {
			// we check if it is possible to extract an int followed by a progressive byte
			extractInt(request);
		}
	}

	@Override
	public long getPriority(Request request) throws ClosedApplicationException {
		try (var scope = mkScope()) {
			return 0L; // all requests have the same priority
		}
	}

	@Override
	public byte[] getInitialStateId() throws ClosedApplicationException {
		try (var scope = mkScope()) {
			return EVEN_STATE_ID.clone(); // the integer value is supposed to be 0 at the beginning
		}
	}

	@Override
	public int beginBlock(long height, LocalDateTime creationStartDateTime, byte[] stateId) throws ClosedApplicationException, UnknownStateException {
		try (var scope = mkScope()) {
			int scopeId = nextId.getAndIncrement();
			if (Arrays.equals(EVEN_STATE_ID, stateId))
				currentStates.put(scopeId, EVEN_STATE_ID);
			else if (Arrays.equals(ODD_STATE_ID, stateId))
				currentStates.put(scopeId, ODD_STATE_ID);
			else
				throw new UnknownStateException("Unknown state id: " + Hex.toHexString(stateId));

			return scopeId;
		}
	}

	@Override
	public void executeTransaction(int scopeId, Request request) throws ClosedApplicationException, RequestRejectedException, UnknownScopeIdException {
		try (var scope = mkScope()) {
			currentStates.put(scopeId, (EVEN_STATE_ID == getCurrentStateFor(scopeId)) == (extractInt(request) % 2 == 0) ? EVEN_STATE_ID : ODD_STATE_ID);
		}
	}

	@Override
	public byte[] endBlock(int scopeId, Deadline deadline) throws ClosedApplicationException, UnknownScopeIdException {
		try (var scope = mkScope()) {
			// there are no coinbase transactions to add
			return getCurrentStateFor(scopeId).clone();
		}
	}

	@Override
	public void commitBlock(int scopeId) throws ClosedApplicationException, UnknownScopeIdException {
		try (var scope = mkScope()) {
			// nothing to commit: there is no database
			var currentState = currentStates.remove(scopeId);
			if (currentState == null)
				throw new UnknownScopeIdException("Unknown scope id " + scopeId);
		}
	}

	@Override
	public void abortBlock(int scopeId) throws ClosedApplicationException, UnknownScopeIdException {
		try (var scope = mkScope()) {
			var currentState = currentStates.remove(scopeId);
			if (currentState == null)
				throw new UnknownScopeIdException("Unknown scope id " + scopeId);
		}
	}

	@Override
	public String getRepresentation(Request request) throws ClosedApplicationException, RequestRejectedException {
		try (var scope = mkScope()) {
			return "add " + extractInt(request);
		}
	}

	@Override
	public void keepFrom(LocalDateTime start) throws ClosedApplicationException {
		try (var scope = mkScope()) {} // no garbage collection is needed
	}

	@Override
	public void publish(Block block) throws ClosedApplicationException {
		try (var scope = mkScope()) {} // no events get published
	}

	@Override
	public void setHead(byte[] stateId) throws UnknownStateException, ClosedApplicationException {
		try (var scope = mkScope()) {
			if (!Arrays.equals(EVEN_STATE_ID, stateId) && !Arrays.equals(ODD_STATE_ID, stateId))
				throw new UnknownStateException("Unknown state id: " + Hex.toHexString(stateId));
		}
	}

	private byte[] getCurrentStateFor(int scopeId) throws UnknownScopeIdException {
		var currentState = currentStates.get(scopeId);
		if (currentState == null)
			throw new UnknownScopeIdException("Unknown scope id " + scopeId);
		else
			return currentState;
	}

	private static int extractInt(Request request) throws RequestRejectedException {
		if (request.getNumberOfBytes() != 5)
			throw new RequestRejectedException("A request must contain a four byte two-complement int followed by a progressive byte to distinguish repeated requests");

		byte[] bytes = request.getBytes();
		byte b1 = bytes[0];
	    byte b2 = bytes[1];
	    byte b3 = bytes[2];
	    byte b4 = bytes[3];

	    return ((0xFF & b1) << 24) | ((0xFF & b2) << 16) | ((0xFF & b3) << 8) | (0xFF & b4);
	}
}
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

package io.mokamint.application.bitcoin;

import static java.math.BigInteger.ZERO;

import java.math.BigInteger;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.patricia.api.UnknownKeyException;
import io.hotmoka.xodus.ExodusException;
import io.hotmoka.xodus.env.Environment;
import io.hotmoka.xodus.env.Store;
import io.hotmoka.xodus.env.Transaction;
import io.mokamint.application.AbstractApplication;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.application.api.Description;
import io.mokamint.application.api.Name;
import io.mokamint.application.api.UnknownScopeIdException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.application.bitcoin.internal.KeyValueStoreOnXodus;
import io.mokamint.application.bitcoin.internal.TrieOfKeys;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.Request;
import io.mokamint.node.api.RequestRejectedException;
import io.mokamint.nonce.api.Deadline;

/**
 * An application functionally equivalent to the kernel of Bitcoin. It keeps track
 * of the balances of accounts (public keys) and allows transfers of balances.
 * Accounts can increase their balance by mining.
 */
@Name("Bitcoin")
@Description("An application functionally equivalent to the kernel of Bitcoin")
public class BitcoinApplication extends AbstractApplication {
	private final AtomicInteger nextId = new AtomicInteger();

	private static record State(TrieOfKeys trie, Transaction txn) {};

	/**
	 * A map from each scope identifier to the current state of that scope identifier.
	 */
	private final ConcurrentMap<Integer, State> currentStates = new ConcurrentHashMap<>();

	private final Environment env;

	private final Store storeOfState;

	private final static Logger LOGGER = Logger.getLogger(BitcoinApplication.class.getName());

	private final static BigInteger ONE_HUNDRED = BigInteger.valueOf(100);

	/**
	 * Creates a Bitcoin application.
	 * 
	 * @param workingDir the directory where the database of the application will be stored
	 */
	public BitcoinApplication(Path workingDir) {
		this.env = new Environment(workingDir.resolve("state").toString());
		this.storeOfState = env.computeInTransaction(txn -> env.openStoreWithoutDuplicatesWithPrefixing("state", txn));
		LOGGER.log(Level.INFO, "bitcoin: opened the state database");
	}

	@Override
	protected void closeResources() {
		try {
			super.closeResources();
		}
		finally {
			try {
				env.close();
				LOGGER.log(Level.INFO, "bitcoin: closed the state database");
			}
			catch (ExodusException e) {
				LOGGER.log(Level.SEVERE, "bitcoin: failed to close the Exodus environment", e);
			}
		}
	}

	private TrieOfKeys mkTrie(Transaction txn, byte[] root) throws UnknownKeyException {
		return new TrieOfKeys(new KeyValueStoreOnXodus(storeOfState, txn), root);
	}

	@Override
	public Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey publicKey) throws ClosedApplicationException {
		try (var scope = mkScope()) {
			return Optional.empty(); // balances have no meaning for this application
		}
	}

	@Override
	public boolean checkDeadline(Deadline deadline) throws ClosedApplicationException {
		try (var scope = mkScope()) {
			// nothing to check
			return true;
		}
	}

	@Override
	public void checkRequest(Request request) throws ClosedApplicationException, RequestRejectedException {
		try (var scope = mkScope()) {
			// we check if it is possible to extract an int followed by a progressive byte
			//extractInt(request);
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
			return new byte[32]; // tries use an empty array to represent the empty trie
		}
	}

	@Override
	public int beginBlock(long height, LocalDateTime creationStartDateTime, byte[] stateId) throws ClosedApplicationException, UnknownStateException {
		Transaction txn = null;

		try (var scope = mkScope()) {
			txn = env.beginTransaction();
			TrieOfKeys trie = mkTrie(txn, stateId);
			int scopeId = nextId.getAndIncrement();
			currentStates.put(scopeId, new State(trie, txn));

			return scopeId;
		}
		catch (UnknownKeyException e) {
			txn.abort();
			throw new UnknownStateException("Unknown state id: " + Hex.toHexString(stateId));
		}
	}

	@Override
	public void executeTransaction(int scopeId, Request request) throws ClosedApplicationException, RequestRejectedException, UnknownScopeIdException {
		try (var scope = mkScope()) {
			//currentStates.put(scopeId, null);
		}
	}

	@Override
	public byte[] endBlock(int scopeId, Deadline deadline) throws ClosedApplicationException, UnknownScopeIdException {
		try (var scope = mkScope()) {
			// we reward the peer and the miner that created the block
			State state = getCurrentStateFor(scopeId);
			var prolog = deadline.getProlog();
			PublicKey publicKeyOfPeer = prolog.getPublicKeyForSigningBlocks();
			TrieOfKeys trie = state.trie;
			BigInteger balanceOfPeer = trie.get(publicKeyOfPeer).orElse(ZERO);
			PublicKey publicKeyOfMiner = prolog.getPublicKeyForSigningDeadlines();
			BigInteger balanceOfMiner = trie.get(publicKeyOfMiner).orElse(ZERO);
			trie = trie.put(publicKeyOfPeer, balanceOfPeer.add(ONE_HUNDRED));
			trie = trie.put(publicKeyOfMiner, balanceOfMiner.add(ONE_HUNDRED));

			return trie.getRoot();
		}
	}

	@Override
	public void commitBlock(int scopeId) throws ClosedApplicationException, UnknownScopeIdException {
		try (var scope = mkScope()) {
			var currentState = currentStates.remove(scopeId);
			if (currentState == null)
				throw new UnknownScopeIdException("Unknown scope id " + scopeId);

			currentState.txn.commit();
		}
	}

	@Override
	public void abortBlock(int scopeId) throws ClosedApplicationException, UnknownScopeIdException {
		try (var scope = mkScope()) {
			var currentState = currentStates.remove(scopeId);
			if (currentState == null)
				throw new UnknownScopeIdException("Unknown scope id " + scopeId);

			currentState.txn.abort();
		}
	}

	@Override
	public String getRepresentation(Request request) throws ClosedApplicationException, RequestRejectedException {
		try (var scope = mkScope()) {
			return ""; //add " + extractInt(request);
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

	private State getCurrentStateFor(int scopeId) throws UnknownScopeIdException {
		var currentState = currentStates.get(scopeId);
		if (currentState == null)
			throw new UnknownScopeIdException("Unknown scope id " + scopeId);
		else
			return currentState;
	}

	/*private static int extractInt(Request request) throws RequestRejectedException {
		if (request.getNumberOfBytes() != 5)
			throw new RequestRejectedException("A request must contain a four byte two-complement int followed by a progressive byte to distinguish repeated requests");

		byte[] bytes = request.getBytes();
		byte b1 = bytes[0];
	    byte b2 = bytes[1];
	    byte b3 = bytes[2];
	    byte b4 = bytes[3];

	    return ((0xFF & b1) << 24) | ((0xFF & b2) << 16) | ((0xFF & b3) << 8) | (0xFF & b4);
	}*/
}
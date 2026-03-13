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

import java.io.IOException;
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

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.patricia.api.UnknownKeyException;
import io.hotmoka.xodus.ByteIterable;
import io.hotmoka.xodus.ExodusException;
import io.hotmoka.xodus.env.Environment;
import io.hotmoka.xodus.env.Store;
import io.hotmoka.xodus.env.Transaction;
import io.mokamint.application.AbstractApplication;
import io.mokamint.application.ApplicationException;
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

	private static record State(TrieOfKeys trie, Transaction txn, long height) {};

	/**
	 * A map from each scope identifier to the current state of that scope identifier.
	 */
	private final ConcurrentMap<Integer, State> currentStates = new ConcurrentHashMap<>();

	private final Environment env;

	private final Store storeOfState;

	private final Object stateAtHeadLock = new Object();

	@GuardedBy("stateAtHeadLock")
	private State stateAtHead;

	private final static Logger LOGGER = Logger.getLogger(BitcoinApplication.class.getName());

	/**
	 * The key used to store, inside {@code storeOfState},
	 * the state identifier of the head of the best chain.
	 */
	private final static ByteIterable STATE_ID_OF_HEAD_KEY = ByteIterable.fromBytes("state id of head".getBytes());

	private final static BigInteger ONE_HUNDRED = BigInteger.valueOf(100);

	/**
	 * Tries use an empty array to represent the empty trie.
	 */
	private final static byte[] INITIAL_STATE_ID = new byte[32];

	/**
	 * Creates a Bitcoin application.
	 * 
	 * @param workingDir the directory where the database of the application will be stored
	 */
	public BitcoinApplication(Path workingDir) {
		this.env = new Environment(workingDir.resolve("state").toString());
		this.storeOfState = env.computeInTransaction(txn -> env.openStoreWithoutDuplicatesWithPrefixing("state", txn));
		LOGGER.log(Level.INFO, "bitcoin: opened the state database");

		byte[] stateIdOfHead = Optional.ofNullable(env.computeInReadonlyTransaction(t -> storeOfState.get(t, STATE_ID_OF_HEAD_KEY)))
				.map(ByteIterable::getBytes)
				.orElse(INITIAL_STATE_ID);

		try {
			setStateAtHead(stateIdOfHead);
		}
		catch (UnknownStateException e) {
			// if the saved state is not available, the database must have been corrupted
			throw new ApplicationException(e.getMessage());
		}
	}

	@Override
	public Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey publicKey) throws ClosedApplicationException {
		try (var scope = mkScope()) {
			synchronized (stateAtHeadLock) {
				return stateAtHead.trie().get(publicKey).or(() -> Optional.of(ZERO));
			}
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
			// if the signature is invalid, then parse() throws a RequestRejectedException below
			if (parse(request).getAmount().compareTo(BigInteger.ZERO) <= 0)
				throw new RequestRejectedException("The sent amount must be positive");
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
			return INITIAL_STATE_ID.clone();
		}
	}

	@Override
	public int beginBlock(long height, LocalDateTime creationStartDateTime, byte[] stateId) throws ClosedApplicationException, UnknownStateException {
		Transaction txn = null;

		try (var scope = mkScope()) {
			txn = env.beginTransaction();
			TrieOfKeys trie = mkTrie(txn, stateId);
			int scopeId = nextId.getAndIncrement();
			setCurrentStateFor(scopeId, new State(trie, txn, height));

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
			State state = getCurrentStateFor(scopeId);
			SendRequest sendRequest = parse(request);
			TrieOfKeys trie = state.trie;
			var publicKeyOfSender = sendRequest.getPublicKeyOfSender();
			var amount = sendRequest.getAmount();
			var publicKeyOfReceiver = sendRequest.getPublicKeyOfReceiver();

			var balanceOfSender = trie.get(publicKeyOfSender).orElse(ZERO);
			if (balanceOfSender.compareTo(amount) < 0)
				throw new RequestRejectedException("The sender is too poor to send " + amount + " coins");

			var balanceOfReceiver = trie.get(publicKeyOfReceiver).orElse(ZERO);

			trie = trie.put(publicKeyOfSender, balanceOfSender.subtract(amount));
			trie = trie.put(publicKeyOfReceiver, balanceOfReceiver.add(amount));

			LOGGER.info("bitcoin: " + sendRequest);

			setCurrentStateFor(scopeId, new State(trie, state.txn, state.height));
		}
	}

	@Override
	public byte[] endBlock(int scopeId, Deadline deadline) throws ClosedApplicationException, UnknownScopeIdException {
		try (var scope = mkScope()) {
			// we must reward the peer and the miner that created the block
			State state = getCurrentStateFor(scopeId);
			var prolog = deadline.getProlog();
			PublicKey publicKeyOfPeer = prolog.getPublicKeyForSigningBlocks();
			TrieOfKeys trie = state.trie;
			BigInteger balanceOfPeer = trie.get(publicKeyOfPeer).orElse(ZERO);
			PublicKey publicKeyOfMiner = prolog.getPublicKeyForSigningDeadlines();
			BigInteger balanceOfMiner = trie.get(publicKeyOfMiner).orElse(ZERO);

			// we implement a reward decreasing with the time
			BigInteger reward = ONE_HUNDRED.subtract(BigInteger.valueOf(state.height / 10L));
			// we do not allow negative rewards
			if (reward.signum() > 0) {
				LOGGER.info("bitcoin: rewarding " + reward + " to " + prolog.getPublicKeyForSigningBlocksBase58() + " and " + prolog.getPublicKeyForSigningDeadlinesBase58());
				// we add the reward to the keys of the peer and miner
				trie = trie.put(publicKeyOfPeer, balanceOfPeer.add(reward));
				trie = trie.put(publicKeyOfMiner, balanceOfMiner.add(reward));
				setCurrentStateFor(scopeId, new State(trie, state.txn, state.height));
			}

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
			return parse(request).toString();
		}
	}

	@Override
	public void keepFrom(LocalDateTime start) throws ClosedApplicationException {
		try (var scope = mkScope()) {} // garbage collection is not implemented
	}

	@Override
	public void publish(Block block) throws ClosedApplicationException {
		try (var scope = mkScope()) {} // there are no events to publish
	}

	@Override
	public void setHead(byte[] stateId) throws UnknownStateException, ClosedApplicationException {
		try (var scope = mkScope()) {
			setStateAtHead(stateId);
		}
	}

	@Override
	protected void closeResources() {
		try {
			super.closeResources();
		}
		finally {
			try {
				synchronized (stateAtHeadLock) {
					if (this.stateAtHead != null)
						this.stateAtHead.txn.abort();
				}
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
	}

	private TrieOfKeys mkTrie(Transaction txn, byte[] root) throws UnknownKeyException {
		return new TrieOfKeys(new KeyValueStoreOnXodus(storeOfState, txn), root);
	}

	private void setStateAtHead(byte[] stateId) throws UnknownStateException {
		Transaction txn = null;

		try {
			txn = env.beginReadonlyTransaction();
			TrieOfKeys trie = mkTrie(txn, stateId);
			env.executeInTransaction(t -> storeOfState.put(t, STATE_ID_OF_HEAD_KEY, ByteIterable.fromBytes(stateId)));
			var stateAtHead = new State(trie, txn, 0L);
			
			synchronized (stateAtHeadLock) {
				if (this.stateAtHead != null)
					this.stateAtHead.txn.abort();
			
				this.stateAtHead = stateAtHead;
			}

			LOGGER.info("bitcoin: set state at head to " + Hex.toHexString(stateId));
		}
		catch (UnknownKeyException e) {
			txn.abort();
			throw new UnknownStateException("Unknown state id: " + Hex.toHexString(stateId));
		}
	}

	/**
	 * Yields the state for the given scope identifier, if any.
	 * 
	 * @param scopeId the scope identifier
	 * @return the state for {@code scopeId}
	 * @throws UnknownScopeIdException if no state is available for {@code scopeId}
	 */
	private State getCurrentStateFor(int scopeId) throws UnknownScopeIdException {
		var currentState = currentStates.get(scopeId);
		if (currentState == null)
			throw new UnknownScopeIdException("Unknown scope id " + scopeId);
		else
			return currentState;
	}

	/**
	 * Sets the state for the given scope identifier.
	 * 
	 * @param scopeId the scope identifier
	 * @param state the state to set
	 */
	private void setCurrentStateFor(int scopeId, State state) {
		currentStates.put(scopeId, state);
	}

	/**
	 * Parses the request bytes arrived to Mokamint into an actual request for
	 * sending coins between two public keys, if possible.
	 * 
	 * @param request the request containing the bytes arrived to Mokamint
	 * @return the bytes in {@code request}, parsed as a {@link SendRequest}
	 * @throws RequestRejectedException if {@code request} cannot be parsed correctly
	 */
	private static SendRequest parse(Request request) throws RequestRejectedException {
		try {
			return SendRequest.from(request.getBytes());
		}
		catch (IOException e) {
			throw new RequestRejectedException(e.getMessage());
		}
	}
}
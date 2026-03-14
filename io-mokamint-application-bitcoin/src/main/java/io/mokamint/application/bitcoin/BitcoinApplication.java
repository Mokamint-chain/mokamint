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

	/**
	 * The counter for the generation of unique scope identifiers
	 * inside {@link #beginBlock(long, LocalDateTime, byte[])}.
	 */
	private final AtomicInteger nextId = new AtomicInteger();

	/**
	 * The state associated to each scope identifier. It is a triple
	 * of the trie containing the balances of each public key,
	 * an open database transaction that is used to read data
	 * from the trie and that must be closed when the state is not used
	 * anymore and the height at which the state has been created.
	 * This height will be used in {@link BitcoinApplication#endBlock(int, Deadline)}
	 * to determine the amount of reward to send to the peer and miner that
	 * created the block having this state as final state.
	 */
	private static record State(TrieOfKeys trie, Transaction txn, long height) {};

	/**
	 * A map from each scope identifier to the current state of that scope identifier.
	 */
	private final ConcurrentMap<Integer, State> currentStates = new ConcurrentHashMap<>();

	/**
	 * The database environment where the state trie is kept. It can be used
	 * to start new database transactions.
	 */
	private final Environment env;

	/**
	 * The database store where the trie of states is kept.
	 */
	private final Store storeOfState;

	/**
	 * A lock for synchronized access to {@link #stateAtHead}.
	 */
	private final Object stateAtHeadLock = new Object();

	/**
	 * The state at the head of the best chain of the blockchain. This is
	 * the state whose identifier is saved in the database under the
	 * {@link #STATE_ID_OF_HEAD_KEY}. In this way, this state can be recovered
	 * if the application is turned off and then on again.
	 * This state is used only to implement queries over the state of the application.
	 * In this application, it is only used to implement
	 * {@link #getBalance(SignatureAlgorithm, PublicKey)}.
	 */
	@GuardedBy("stateAtHeadLock")
	private State stateAtHead;

	/**
	 * The key used to store, inside {@code storeOfState},
	 * the state identifier of the head of the best chain.
	 */
	private final static ByteIterable STATE_ID_OF_HEAD_KEY = ByteIterable.fromBytes("state id of head".getBytes());

	/**
	 * A constant used for rewarding peer and miner inside
	 * {@link #endBlock(int, Deadline).
	 */
	private final static BigInteger ONE_HUNDRED = BigInteger.valueOf(100);

	/**
	 * Tries use an empty array to represent the empty trie.
	 */
	private final static byte[] INITIAL_STATE_ID = new byte[32];

	private final static Logger LOGGER = Logger.getLogger(BitcoinApplication.class.getName());

	/**
	 * Creates a Bitcoin application.
	 * 
	 * @param workingDir the directory where the database of the application will be stored
	 */
	public BitcoinApplication(Path workingDir) {
		// open the database and recover the store where the trie of states is saved
		this.env = new Environment(workingDir.resolve("state").toString());
		this.storeOfState = env.computeInTransaction(txn -> env.openStoreWithoutDuplicatesWithPrefixing("state", txn));
		LOGGER.log(Level.INFO, "bitcoin: opened the state database");

		// access the database to see which was the last state identifier for the state at the
		// head of the blockchain; if missing, the state identifier of the initial, empty trie is used
		byte[] stateIdOfHead = Optional.ofNullable(env.computeInReadonlyTransaction(txn -> storeOfState.get(txn, STATE_ID_OF_HEAD_KEY)))
				.map(ByteIterable::getBytes)
				.orElse(INITIAL_STATE_ID);

		// set the field {@code stateAtHead} to the state whose id was saved in the database
		// as the state at the head of the current chain of the blockchain
		try {
			setStateAtHead(stateIdOfHead);
		}
		catch (UnknownStateException e) {
			// if the saved state is not available, the database must be corrupted
			throw new ApplicationException("Cannot find the state of the head of the current chain: " + e.getMessage());
		}
	}

	@Override
	public Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey publicKey) throws ClosedApplicationException {
		try (var scope = mkScope()) {
			synchronized (stateAtHeadLock) {
				// we access the trie and yield the balance bound to the required key;
				// if missing, the balance is implicitly zero
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
			// we check out the state with the requested state identifier,
			// inside a new database transaction that can later be used
			// in {@code executeTransaction()} to modify the database
			txn = env.beginTransaction();
			TrieOfKeys trie = mkTrie(txn, stateId); // if the state is unknown, it throws an UnknownKeyException
			int scopeId = nextId.getAndIncrement(); // create a new, unique scope identifier
			setCurrentStateFor(scopeId, new State(trie, txn, height)); // bind the scope identifier to the new state

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
			// recover the state for the requested scope identifier, if any
			State state = getCurrentStateFor(scopeId);
			// parse the request bytes into an actually send request: this is the only
			// request understood by this application
			SendRequest sendRequest = parse(request);
			TrieOfKeys trie = state.trie;
			var publicKeyOfSender = sendRequest.getPublicKeyOfSender();
			var amount = sendRequest.getAmount();
			var publicKeyOfReceiver = sendRequest.getPublicKeyOfReceiver();

			// check if the sender has enough coins to send
			var balanceOfSender = trie.get(publicKeyOfSender).orElse(ZERO);
			if (balanceOfSender.compareTo(amount) < 0) {
				LOGGER.warning("bitcoin: request rejected since the sender is too poor to send " + sendRequest.getAmount() + " coins");
				throw new RequestRejectedException("The sender is too poor to send " + amount + " coins");
			}

			var balanceOfReceiver = trie.get(publicKeyOfReceiver).orElse(ZERO);

			// decrease the balance of the sender
			trie = trie.put(publicKeyOfSender, balanceOfSender.subtract(amount));

			// increase the balance of the receiver
			trie = trie.put(publicKeyOfReceiver, balanceOfReceiver.add(amount));

			LOGGER.info("bitcoin: " + sendRequest);

			// update the state for the scope identifier
			setCurrentStateFor(scopeId, new State(trie, state.txn, state.height));
		}
	}

	@Override
	public byte[] endBlock(int scopeId, Deadline deadline) throws ClosedApplicationException, UnknownScopeIdException {
		try (var scope = mkScope()) {
			// the goal of this code is to reward the peer and the miner that created the block

			// recover the state for the requested scope identifier, if any
			State state = getCurrentStateFor(scopeId);

			// inside the prolog of the deadline, there is information
			// on the public keys of the peer and miner that contributed to the creation of the block
			var prolog = deadline.getProlog();
			PublicKey publicKeyOfPeer = prolog.getPublicKeyForSigningBlocks();
			TrieOfKeys trie = state.trie;
			BigInteger balanceOfPeer = trie.get(publicKeyOfPeer).orElse(ZERO);
			PublicKey publicKeyOfMiner = prolog.getPublicKeyForSigningDeadlines();
			BigInteger balanceOfMiner = trie.get(publicKeyOfMiner).orElse(ZERO);

			// implement a reward decreasing with the time: these are freshly minted coins
			BigInteger reward = ONE_HUNDRED.subtract(BigInteger.valueOf(state.height / 10L));
			// do not allow negative rewards
			if (reward.signum() > 0) {
				LOGGER.info("bitcoin: rewarding " + reward + " to " + prolog.getPublicKeyForSigningBlocksBase58() + " and " + prolog.getPublicKeyForSigningDeadlinesBase58());
				// add the reward to the keys of the peer and miner
				trie = trie.put(publicKeyOfPeer, balanceOfPeer.add(reward));
				trie = trie.put(publicKeyOfMiner, balanceOfMiner.add(reward));

				// update the state for the scope identifier
				setCurrentStateFor(scopeId, new State(trie, state.txn, state.height));
			}

			// yields the state identifier of the trie resulting at the end of the execution
			// of the requests in the block and of the rewarding transactions
			return trie.getRoot();
		}
	}

	@Override
	public void commitBlock(int scopeId) throws ClosedApplicationException, UnknownScopeIdException {
		try (var scope = mkScope()) {
			var currentState = currentStates.remove(scopeId);
			if (currentState == null)
				throw new UnknownScopeIdException("Unknown scope id " + scopeId);

			// commit the database transaction for the scope identifier
			currentState.txn.commit();
		}
	}

	@Override
	public void abortBlock(int scopeId) throws ClosedApplicationException, UnknownScopeIdException {
		try (var scope = mkScope()) {
			var currentState = currentStates.remove(scopeId);
			if (currentState == null)
				throw new UnknownScopeIdException("Unknown scope id " + scopeId);

			// abort the database transaction for the scope identifier
			currentState.txn.abort();
		}
	}

	@Override
	public String getRepresentation(Request request) throws ClosedApplicationException, RequestRejectedException {
		try (var scope = mkScope()) {
			// yield a human-readable representation of the request: it parses
			// the request bytes into a send request and then transforms the latter into a string
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
		// this method is called by the peer whenever the head of the blockchain changes:
		// it will keep node of the state identifier at the head of the chain, since it
		// is needed to implement {@code getBalance()}
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
				// abort the ongoing transaction for the state at the head of the blockchain
				synchronized (stateAtHeadLock) {
					if (this.stateAtHead != null)
						this.stateAtHead.txn.abort();
				}
			}
			finally {
				try {
					// close the database
					env.close();
					LOGGER.log(Level.INFO, "bitcoin: closed the state database");
				}
				catch (ExodusException e) {
					LOGGER.log(Level.SEVERE, "bitcoin: failed to close the Exodus environment", e);
				}
			}
		}
	}

	/**
	 * Yields the trie in the database, checked out at the given state identifier.
	 * 
	 * @param txn the database transaction for the creation of the trie
	 * @param root the state identifier to check out
	 * @return the resulting trie
	 * @throws UnknownKeyException if {@code root} is unknown in the database
	 */
	private TrieOfKeys mkTrie(Transaction txn, byte[] root) throws UnknownKeyException {
		return new TrieOfKeys(new KeyValueStoreOnXodus(storeOfState, txn), root);
	}

	private void setStateAtHead(byte[] stateId) throws UnknownStateException {
		Transaction txn = null;

		try {
			// the state at the head of the blockchain is only used to implement
			// {@code getBalance()} and is therefore enough to create a read-only database transaction
			txn = env.beginReadonlyTransaction();
			TrieOfKeys trie = mkTrie(txn, stateId);

			// store the new state identifier in the database, so that the state at the head of the blockchain
			// can be recovered if the application is turned off and later on again
			env.executeInTransaction(t -> storeOfState.put(t, STATE_ID_OF_HEAD_KEY, ByteIterable.fromBytes(stateId)));
			var stateAtHead = new State(trie, txn, 0L);
			
			synchronized (stateAtHeadLock) {
				if (this.stateAtHead != null)
					// abort the transaction of the previous state, if any
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
	 * Parses the request bytes received from the Mokamint peer into an actual request for
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
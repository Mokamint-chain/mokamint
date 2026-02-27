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

package io.mokamint.application.api;

import java.math.BigInteger;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.closeables.api.OnCloseHandlersContainer;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.Request;
import io.mokamint.node.api.RequestRejectedException;
import io.mokamint.nonce.api.Deadline;

/**
 * An application for the Mokamint blockchain. It specifies the application layer
 * of a blockchain. Mokamint applications are the run-time support to filter
 * the transactions reaching a node and to execute sequential groups
 * of transactions, those inside a block of the blockchain. In particular,
 * a node executes the transactions inside a block at a given <code>height</code>,
 * having a given <code>deadline</code>,
 * starting from the state whose identifier is <code>initialStateId</code> (which is
 * typically that at the end of the previous block), at a moment <code>when</code>,
 * by calling the API of its application as follows:
 * 
 * <ul>
 * <li> <code>groupId = beginBlock(height, when, initialStateId);</code>
 * <li> for each transaction <code>tx</code> in the block, do:
 * <ul>
 * <li> <code>checkTransaction(tx);</code>
 * <li> <code>deliverTransaction(tx, groupId);</code>
 * </ul>
 * <li> <code>finalStateId = endBlock(groupId, deadline);</code>
 * <li> <code>commitBlock(groupId);</code>
 * </ul>
 * 
 * This commits the final state at the end of the execution of all transactions,
 * whose identifier is <code>finalStateId</code>. That is, that state will be recoverable
 * in the future, from <code>finalStateId</code>, if required by the node.
 * 
 * If, instead, the final state needn't be committed, because if won't be needed in the
 * future (for instance, because <code>finalStateId</code> does not match its expected
 * value and hence block verification fails), then {@link #abortBlock(int)} is called
 * at some moment, and {@link #commitBlock(int)} is not called.
 * 
 * <br>
 * 
 * The execution of a group of transactions is identified by a numerical <code>groupId</code>,
 * that should be unique among all currently ongoing executions. This is because the application
 * might be required to run more groups of transactions, concurrently, and each of those
 * executions must be identified by a different <code>groupId</code>. After the execution of
 * <code>commitBlock(groupId)</code> or <code>abortBlock(groupId)</code>, that
 * <code>groupId</code> may be recycled for a subsequent execution.
 * 
 * <br>
 * 
 * The implementation of all these methods must be deterministic. The execution of a group
 * of transactions can depend only on <code>height</code>, <code>deadline</code>,
 * <code>initialStateId</code> and <code>when</code>.
 * 
 * <br>
 * 
 * The method {@link #checkRequest(Request)} does not receive the identifier of the
 * group execution, since it performs a context-independent (and typically, consequently, superficial)
 * check, that is applied also as a filter before adding transactions to the mempool of a node.
 * Method {@link #executeTransaction(int, Request)} subsequently performs a more thorough consistency
 * check later, that considers the context of its execution (for instance, the state where the check is
 * performed). This is why both these methods can throw a {@link RequestRejectedException}.
 */
@ThreadSafe
public interface Application extends AutoCloseable, OnCloseHandlersContainer {

	/**
	 * Yields information about this application.
	 * 
	 * @return the information
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	Info getInfo() throws ClosedApplicationException, TimeoutException, InterruptedException;

	/**
	 * Yields the balance of the given public key, if this means something for this application.
	 * Typically, the public key is that of a miner for the Mokamint engine where this application
	 * is running. Not all applications have a notion of balance and not all public keys must
	 * have a balance. Therefore, this method returns an optional value.
	 * 
	 * @param signature the signature algorithm of the {@code publicKey}
	 * @param publicKey the public key whose balance is required
	 * @return the balance of {@code publicKey}, if any
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey publicKey) throws ClosedApplicationException, TimeoutException, InterruptedException;

	/**
	 * Performs application-specific checks on the given deadline.
	 * This method is called whenever a node receives a new deadline from one of its miners.
	 * The application can decide to accept or reject the deadline. Most applications
	 * do not require any extra constraint on the deadlines and will just
	 * require extra data to be empty in the deadline. Other applications might require a specific
	 * structure of this extra data of the deadline, for instance if they require deadlines
	 * to be signed by the miner.
	 * 
	 * @param deadline the deadline to check
	 * @return true if and only if {@code deadline} is valid according to this application
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	boolean checkDeadline(Deadline deadline) throws ClosedApplicationException, TimeoutException, InterruptedException;

	/**
	 * Checks if the given request is valid according to this application.
	 * Invalid requests are just rejected when added to a node and they
	 * will never be added to the mempool and consequently never to the blockchain.
	 * This check is called as soon as a request reaches a node.
	 * It is typically a simply syntactical, context-independent check.
	 * An application can safely implement this method as
	 * a no-operation, since it is only meant for a quick optimization: to discard
	 * requests that are clearly invalid, avoiding pollution of the mempool and
	 * without even trying to deliver them.
	 * 
	 * @param request the request to check
	 * @throws RequestRejectedException if the check failed and the request should not be added to the mempool
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	void checkRequest(Request request) throws RequestRejectedException, ClosedApplicationException, TimeoutException, InterruptedException;

	/**
	 * Computes the priority of the given request. Nodes may (but are not required to)
	 * implement a policy of request inclusion in blocks that favors requests with higher priority.
	 * 
	 * @param request the request
	 * @return the priority of {@code request}
	 * @throws RequestRejectedException if the priority of the request cannot be computed
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	long getPriority(Request request) throws RequestRejectedException, ClosedApplicationException, TimeoutException, InterruptedException;

	/**
	 * Yields a string representation of the request, that can be used to print
	 * or process its structure. This can be everything, possibly, but not necessarily, JSON.
	 * It is expected (but not required) that if a transaction passes the
	 * {@link #checkRequest(Request)} test without throwing a {@link RequestRejectedException},
	 * then {@link #getRepresentation(Request)} will not throw that exception either.
	 * 
	 * @param request the request
	 * @return the representation of {@code transaction}
	 * @throws RequestRejectedException if the representation of the transaction cannot be computed
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	String getRepresentation(Request request) throws RequestRejectedException, ClosedApplicationException, TimeoutException, InterruptedException;

	/**
	 * Yields the identifier of the state of this application when it starts, before any transaction
	 * has been executed. This is typically the hash of the empty state of the application.
	 * 
	 * @return the identifier of the state of this application when it starts
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	byte[] getInitialStateId() throws ClosedApplicationException, TimeoutException, InterruptedException;

	/**
	 * The node calls this method at the start of the execution of the requests
	 * inside a block. The application must be able to support the concurrent
	 * execution of more groups of requests. Consequently, each execution is identified
	 * by a unique number, called execution scope and returned by this method.
	 * 
	 * @param height the height of the block whose requests are being executed
	 * @param when the time at the beginning of the execution of the requests in the block
	 * @param stateId the identifier of the state of the application at the beginning of the execution of
	 *                the requests in the block
	 * @return the identifier of the execution scope that is being started; this must be
	 *         different from the identifier of other executions that are currently being performed
	 * @throws UnknownStateException if the application cannot find the state with identifier {@code stateId}
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	int beginBlock(long height, LocalDateTime when, byte[] stateId) throws UnknownStateException, ClosedApplicationException, TimeoutException, InterruptedException;

	/**
	 * Executes another transaction inside the scope of execution of requests identified by {@code scopeId}.
	 * This means that the request will be semantically checked and then executed.
	 * If the semantical check fails, an exception is thrown instead. Semantically checked means that
	 * the transaction could be completed verified in the context of execution.
	 * This is then a thorough, context-dependent check, must stronger, in general, than the
	 * check performed by {@link #checkRequest(Request)}.
	 * 
	 * @param scopeId the identifier of the execution scope
	 * @param request the request to execute
	 * @throws RequestRejectedException if the check or execution of the request failed
	 * @throws UnknownScopeIdException if the {@code scopeId} is not valid
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	void executeTransaction(int scopeId, Request request) throws RequestRejectedException, UnknownScopeIdException, ClosedApplicationException, TimeoutException, InterruptedException;

	/**
	 * The node calls this method when a group execution of requests ends.
	 * This gives the application the opportunity of adding the effects of coinbase transactions
	 * that it should execute at the end of the execution of the requests in the group.
	 * Such coinbase transactions are typically related to a deadline, that inside the block
	 * whose requests have been executed, which is why the latter is provided to this method.
	 * 
	 * @param scopeId the identifier of the execution scope of requests that is being ended
	 * @param deadline the deadline that has been computed for the block containing the requests
	 * @return the identifier of the state resulting at the end of the group execution
	 *         of the requests, including eventual coinbase transactions added at its end
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws UnknownScopeIdException if the {@code scopeId} is not valid
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	byte[] endBlock(int scopeId, Deadline deadline) throws ClosedApplicationException, UnknownScopeIdException, TimeoutException, InterruptedException;

	/**
	 * The node calls this method to commit the state resulting at the end of the execution
	 * of the scope of requests identified by {@code scopeId}. This means that the
	 * application, in the future, must be able to recover the state at the
	 * end of that execution, from the identifier of that state.
	 * This typically requires to commit the resulting state into a database.
	 * It is guaranteed that this method is called only after {@code #endBlock(int, Deadline)}
	 * has been called on the same {@code scopeId}.
	 * 
	 * @param scopeId the identifier of the execution scope of requests that is being committed
	 * @throws UnknownScopeIdException if the {@code scopeId} is not valid
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	void commitBlock(int scopeId) throws ClosedApplicationException, UnknownScopeIdException, TimeoutException, InterruptedException;

	/**
	 * The node calls this method to abort the scope of execution of requests
	 * identified by {@code scopeId}. This means that the application, in the future,
	 * will not be required to recover the state at the end of that execution, from the
	 * identifier of that state. This typically requires to abort a database transaction
	 * and clear some resources. Note that this method might be called also when
	 * {@link #endBlock(int, Deadline)} has not been called before for the same {@code scopeId}.
	 * The only guarantee is that {@link #beginBlock(long, LocalDateTime, byte[])} has been
	 * called before for the same {@code scopeId}.
	 * 
	 * @param scopeId the identifier of the scope of execution of requests that is being aborted
	 * @throws UnknownScopeIdException if the {@code scopeId} is not valid
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	void abortBlock(int scopeId) throws ClosedApplicationException, UnknownScopeIdException, TimeoutException, InterruptedException;

	/**
	 * Informs the application that states of blocks created with a {@link #beginBlock(long, LocalDateTime, byte[])} whose
	 * {@code when} parameter is before {@code start} are allowed to be garbage-collected, if the application has some notion
	 * of garbage-collection.
	 * 
	 * @param start the limit time, before which states can be garbage-collected
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	void keepFrom(LocalDateTime start) throws ClosedApplicationException, TimeoutException, InterruptedException;

	/**
	 * Publishes the given block, that has just been added to the blockchain.
	 * The application might exploit this hook, for instance, in order to trigger
	 * events on the basis of the content of the block. Or it might just do nothing.
	 * 
	 * @param block the block to publish
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	void publish(Block block) throws ClosedApplicationException, TimeoutException, InterruptedException;

	/**
	 * Closes this application. After this closure, the methods of this application will throw
	 * a {@link ClosedApplicationException}. An application cannot be reopened after being closed.
	 */
	@Override
	void close();
}
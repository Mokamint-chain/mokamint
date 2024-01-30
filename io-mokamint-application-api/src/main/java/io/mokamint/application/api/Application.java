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

import java.time.LocalDateTime;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.nonce.api.Deadline;

/**
 * An application for the Mokamint blockchain. It specifies the application layer
 * of a blockchain. Mokamint applications are the run-time support to filter
 * the transactions reaching a node and to execute sequential groups
 * of transactions, those inside a block of the blockchain. In particular,
 * a node executes the transactions inside a block at a given <code>height</code>,
 * having a given <code>deadline</code>,
 * starting from the state whose identifier is <code>initialId</code> (which is typically
 * that at the end of the previous block), at a moment <code>when</code>,
 * by calling the API of its application as follows:
 * 
 * <ul>
 * <li> <code>id = beginBlock(height, initialId, when);</code>
 * <li> for each transaction <code>tx</code> in the block, do:
 * <ul>
 * <li> <code>checkTransaction(tx);</code>
 * <li> <code>deliverTransaction(tx, id);</code>
 * </ul>
 * <li> <code>finalId = endBlock(id, deadline);</code>
 * <li> <code>commitBlock(id);</code>
 * </ul>
 * 
 * This commits the final state at the end of the execution of all transactions,
 * whose identifier is <code>finalId</code>. That is, that state will be recoverable
 * in the future, from <code>finalId</code>, if required by the node.
 * 
 * If, instead, the final state needn't be committed, because if won't be needed in the
 * future (for instance, because <code>finalId</code> does not match its expected
 * value and hence block verification fails), then {@link #abortBlock(int)} is called
 * at the end, instead of {@link #commitBlock(int)}.
 * 
 * <br>
 * 
 * The execution of a group of transactions is identified by a numerical <code>id</code>,
 * that should be unique among all currently ongoing executions. This is because the application
 * might be required to run more groups of transactions, concurrently, and each of those
 * executions must be identified by a different <code>id</code>. After the execution of
 * <code>commitBlock(id)</code> or <code>abortBlock(id)</code>, that <code>id</code> might
 * be recycled for a subsequent execution.
 * 
 * <br>
 * 
 * The implementation of all these methods must be deterministic. The execution of a group
 * of transactions can depend only on <code>height</code>, <code>deadline</code>,
 * <code>initialId</code> and <code>when</code>.
 * 
 * <br>
 * 
 * The method {@link #checkTransaction(Transaction)} does not receive the identifier of the
 * group execution, since it performs a context-independent (and typically, consequently, superficial)
 * check, that is applied also as a filter before adding transactions to the mempool of a node.
 * Method {@link #deliverTransaction(Transaction, int)} will perform a more thorough consistency
 * check later, that considers the context of its execution (for instance, the state where the check is
 * performed). This is why both these methods can throw a {@link RejectedTransactionException}.
 */
@ThreadSafe
public interface Application extends AutoCloseable {

	/**
	 * Checks if the given extra data from the prolog of a deadline is considered
	 * valid by this application. This method is called whenever a node
	 * receives a new deadline from one of its miners.
	 * The application can decide to accept or reject the deadline, on the
	 * basis of its prolog's extra bytes. This allows applications to require a specific
	 * structure for the prologs of the valid deadlines. If this is not relevant for an
	 * application, it can just require this array to be any array or force it to be empty.
	 * 
	 * @param extra the extra, application-specific bytes of the prolog
	 * @return true if and only if {@code extra} is valid according to this application
	 */
	boolean checkPrologExtra(byte[] extra);

	/**
	 * Checks if the given transaction is valid according to this application.
	 * Invalid transactions are just rejected when added to a node and they
	 * will never be added to the mempool and consequently never to the blockchain.
	 * This check is called as soon as a transaction reaches a node.
	 * It is typically a simply syntactical, context-independent check.
	 * An application can safely implement this method as
	 * a no-operation, since it is only meant for a quick optimization: to discard
	 * transactions that are clearly invalid, avoiding pollution of the mempool and
	 * without even trying to deliver them.
	 * 
	 * @param transaction the transaction to check
	 * @throws RejectedTransactionException if the check failed and the transaction
	 *                                      should not be added to the mempool
	 */
	void checkTransaction(Transaction transaction) throws RejectedTransactionException;

	/**
	 * Computes the priority of the given transaction. Nodes may (but are not required to)
	 * implement a policy of transaction inclusion in blocks that favors transactions with higher priority.
	 * 
	 * @param transaction the transaction
	 * @return the priority of {@code transaction}
	 * @throws RejectedTransactionException if the priority of the transaction cannot be computed
	 */
	long getPriority(Transaction transaction) throws RejectedTransactionException;

	/**
	 * Yields a string representation of the transaction, that can be used to print
	 * or process its structure. This can be everything, possibly, but not necessarily, JSON.
	 * It is expected (but not required) that if a transaction passes the
	 * {@link #checkTransaction(Transaction)} test without throwing a {@link RejectedTransactionException},
	 * then {@link #getRepresentation(Transaction)} will not throw that exception either.
	 * 
	 * @param transaction the transaction
	 * @return the representation of {@code transaction}
	 * @throws RejectedTransactionException if the representation of the transaction cannot be computed
	 */
	String getRepresentation(Transaction transaction) throws RejectedTransactionException;

	/**
	 * Yields the identifier of the state of this application when it starts, before any transaction
	 * has been executed. This is typically the hash of the empty state of the application.
	 * 
	 * @return the identifier of the state of this application when it starts
	 */
	byte[] getInitialStateId();

	/**
	 * The node calls this method at the start of the execution of the transactions
	 * inside a block. The application must be able to support the concurrent
	 * execution of more groups of transactions. Consequently, each execution is identified
	 * by a unique number.
	 * 
	 * @param height the height of the block whose transactions are being executed
	 * @param stateId the identifier of the state of the application at the beginning of the execution of
	 *                the transactions in the block
	 * @param initialDateTime the time at the beginning of the execution of the transactions in the block
	 * @return the identifier of the group execution that is being started; this must be
	 *         different from the identifier of other executions that are currently being performed
	 * @throws UnknownStateException if the application cannot find the state with identifier {@code stateId}
	 */
	int beginBlock(long height, byte[] stateId, LocalDateTime initialDateTime) throws UnknownStateException;

	/**
	 * Delivers another transaction inside the group execution identified by {@code id}.
	 * This means that the transaction will be fully checked and then executed.
	 * If the full check fails, an exception is thrown instead. Fully checked means that
	 * the transaction could be completed verified in the context of the block.
	 * This is then a thorough, context-dependent check, must stronger, in general, than the
	 * check performed by {@link #checkTransaction(Transaction)}.
	 * 
	 * @param transaction the transaction to deliver
	 * @param id the identifier of the group execution
	 * @throws RejectedTransactionException if the check or execution of the transaction failed
	 */
	void deliverTransaction(Transaction transaction, int id) throws RejectedTransactionException;

	/**
	 * The node calls this method when a group execution of transactions ends.
	 * This gives the application the opportunity of adding the effects of coinbase transactions
	 * that it should execute at the end of the execution of the transactions in the group.
	 * Such coinbase transactions are typically related to a deadline, that inside the block
	 * whose transactions have been executed, which is why the latter is provided to this method.
	 * 
	 * @param id the identifier of the group execution of transactions that is being ended
	 * @param deadline the deadline that has been computed for the block containing the transactions
	 * @return the identifier of the state resulting at the end of the group execution
	 *         of the transactions, including eventual coinbase transactions added at its end
	 */
	byte[] endBlock(int id, Deadline deadline);

	/**
	 * The node calls this method to commit the state resulting at the end of the execution
	 * of the group of transactions identified by {@code id}. This means that the
	 * application, in the future, must be able to recover the state at the
	 * end of that execution, from the identifier of that state.
	 * This typically requires to commit the resulting state into a database.
	 * 
	 * @param id the identifier of the execution of a group of transactions that is being committed
	 */
	void commitBlock(int id);

	/**
	 * The node calls this method to abort the execution of the group of transactions
	 * identified by {@code id}. This means that the application, in the future,
	 * will not be required to recover the state at the end of that execution, from the
	 * identifier of that state. This typically requires to abort a database transaction
	 * and clear some resources.
	 * 
	 * @param id the identifier of the execution of a group of transactions that is being aborted
	 */
	void abortBlock(int id);
}
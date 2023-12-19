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

import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.nonce.api.Deadline;

/**
 * An application for the Mokamint blockchain.
 */
public interface Application {

	/**
	 * Checks if the given extra from the prolog of a deadline is considered
	 * valid by this application. This method is called whenever a node
	 * receives a new deadline from one of its miners.
	 * The application can decide to accept or reject the deadline, on the
	 * basis of its prolog's extra bytes.
	 * 
	 * @param extra the extra, application-specific bytes of the prolog
	 * @return true if and only if {@code extra} is valid according to this application
	 */
	boolean checkPrologExtra(byte[] extra);

	/**
	 * Checks if the given transaction is valid according to this application.
	 * Invalid transactions are just rejected when added to a node and they
	 * will never be added to the mempool and consequently never to the blockchain.
	 * This check is invoked as soon as
	 * a transaction reaches a node. It is typically a merely syntactical,
	 * context-independent check. An application can safely implement this method as
	 * a no-operation, since it is only meant for a quick optimization: to discard
	 * transactions that are clearly invalid, avoiding pollution of the mempool and
	 * without even trying to deliver them.
	 * 
	 * @param transaction the transaction to check
	 * @throws RejectedTransactionException if the check failed
	 */
	void checkTransaction(Transaction transaction) throws RejectedTransactionException;

	/**
	 * Computes the priority of the given transaction.
	 * 
	 * @param transaction the transaction
	 * @return the priority
	 * @throws RejectedTransactionException if the priority of the transaction cannot be computed
	 */
	long getPriority(Transaction transaction) throws RejectedTransactionException;

	/**
	 * Yields the state of this application when it starts, before any transaction has been executed.
	 * 
	 * @return the state of this application when it starts
	 */
	byte[] getInitialStateHash();

	/**
	 * The node calls this method when a new block is being created. It marks the beginning
	 * of the delivering of its transactions. The application must be able to support
	 * the construction of many blocks at the same time. Consequently, each construction
	 * is identified by a unique number.
	 * 
	 * @param height the height of the block that is being created
	 * @param initialStateHash the hash of the state at the beginning of the execution of
	 *                         the transactions in the block
	 * @param initialDateTime the time at the beginning of the execution of
	 *                        the transactions in the block
	 * @return the identifier of the block creation that is being started; this is guaranteed
	 *         to be different from the identifier of other block creations that are currently
	 *         being performed
	 */
	int beginBlock(long height, byte[] initialStateHash, LocalDateTime initialDateTime);

	/**
	 * Delivers another transaction inside the block whose creation is identified by {@code id}.
	 * This means that the transaction will be fully checked and then executed.
	 * If the full check fails, an exception is thrown instead. Fully checked means that
	 * the transaction can be completed verified in the context of the block.
	 * This is then a thorough, context-dependent check, must stronger, in general, than the
	 * check performed by {@link #checkTransaction(Transaction)}.
	 * 
	 * @param transaction the transaction to deliver
	 * @param id the identifier of the block creation
	 * @throws RejectedTransactionException if the check of the transaction failed
	 */
	void deliverTransaction(Transaction transaction, int id) throws RejectedTransactionException;

	/**
	 * The node calls this method when a new block creation ends and no more transactions
	 * will be delivered for the block. This gives the application the opportunity of adding
	 * the effect of coinbase transactions that it should execute at the end of the execution
	 * of the transactions inside the block.
	 * 
	 * @param id the identifier of the block creation the is being ended
	 * @param deadline the deadline that has been computed for the block being created
	 * @return the hash of the state at the end of the execution of the transactions delivered
	 *         during the creation of the block, including eventual coinbase transactions added
	 *         at its end
	 */
	byte[] endBlock(int id, Deadline deadline);

	void commitBlock(int id);
}
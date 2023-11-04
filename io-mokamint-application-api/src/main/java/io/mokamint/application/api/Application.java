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

import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;

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
	 * will never be added to the blockchain. This check is invoked as soon as
	 * a transaction reaches a node and can only be mainly syntactical and
	 * context-independent. It will typically check the syntactical structure of
	 * the transaction only. An application can safely always return {@code true} here,
	 * since this method is only meant for a quick optimization: discarding transactions
	 * that are clearly invalid, without even trying to deliver them.
	 * 
	 * @param transaction the transaction to check
	 * @return true if and only if the transaction is valid
	 */
	boolean checkTransaction(Transaction transaction);

	/**
	 * Computes the priority of the given transaction.
	 * 
	 * @param transaction the transaction
	 * @return the priority
	 * @throws RejectedTransactionException if the priority of the transaction cannot be computed
	 */
	long getPriority(Transaction transaction) throws RejectedTransactionException;
}
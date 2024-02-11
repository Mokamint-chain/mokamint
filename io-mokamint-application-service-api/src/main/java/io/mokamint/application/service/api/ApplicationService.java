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

package io.mokamint.application.service.api;

import io.hotmoka.websockets.server.api.WebSocketServer;
import io.mokamint.application.api.Application;
import io.mokamint.node.api.Transaction;

/**
 * A websocket server for the public API of a Mokamint application.
 */
public interface ApplicationService extends WebSocketServer {

	/**
	 * The network endpoint path where {@link Application#checkPrologExtra(byte[])} is published.
	 */
	String CHECK_PROLOG_EXTRA_ENDPOINT = "/check_prolog_extra";

	/**
	 * The network endpoint path where {@link Application#checkTransaction(Transaction)} is published.
	 */
	String CHECK_TRANSACTION_ENDPOINT = "/check_transaction";

	/**
	 * The network endpoint path where {@link Application#getPriority(Transaction)} is published.
	 */
	String GET_PRIORITY_ENDPOINT = "/get_priority";

	/**
	 * The network endpoint path where {@link Application#getRepresentation(Transaction)} is published.
	 */
	String GET_REPRESENTATION_ENDPOINT = "/get_representation";

	/**
	 * The network endpoint path where {@link Application#getInitialStateId()} is published.
	 */
	String GET_INITIAL_STATE_ID_ENDPOINT = "/get_initial_state_id";

	/**
	 * The network endpoint path where {@link Application#beginBlock(long, byte[], java.time.LocalDateTime)} is published.
	 */
	String BEGIN_BLOCK_ENDPOINT = "/begin_block";

	/**
	 * The network endpoint path where {@link Application#deliverTransaction(Transaction, int)} is published.
	 */
	String DELIVER_TRANSACTION_ENDPOINT = "/deliver_transaction";

	/**
	 * The network endpoint path where {@link Application#endBlock(int, io.mokamint.nonce.api.Deadline)} is published.
	 */
	String END_BLOCK_ENDPOINT = "/end_block";

	/**
	 * The network endpoint path where {@link Application#commitBlock(int)} is published.
	 */
	String COMMIT_BLOCK_ENDPOINT = "/commit_block";

	/**
	 * The network endpoint path where {@link Application#abortBlock(int)} is published.
	 */
	String ABORT_BLOCK_ENDPOINT = "/abort_block";

	@Override
	void close() throws InterruptedException;
}
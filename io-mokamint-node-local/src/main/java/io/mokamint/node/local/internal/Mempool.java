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

/**
 * 
 */
package io.mokamint.node.local.internal;

import io.mokamint.application.api.Application;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;

/**
 * The mempool of a Mokamint node. It contains transactions that are available
 * to be processed and included in the blocks of the blockchain.
 */
public class Mempool {

	/**
	 * The node having this mempool.
	 */
	private final LocalNodeImpl node;

	/**
	 * The application running in the node.
	 */
	private final Application app;

	/**
	 * Creates a mempool for the given node.
	 * 
	 * @param node the node
	 */
	public Mempool(LocalNodeImpl node) {
		this.node = node;
		this.app = node.getApplication();
	}

	/**
	 * Schedules the addition of the given transaction to this mempool.
	 * This method returns immediately. Eventually, the transaction will be
	 * checked for validity and, if valid, added to this mempool.
	 * 
	 * @param transaction the transaction to add
	 */
	public void scheduleAddition(Transaction transaction) {
		
		class AddToMempoolTask implements Task {

			@Override
			public void body() {
				// TODO
			}

			@Override
			public String toString() {
				return "transaction addition task to the mempool";
			}

			@Override
			public String logPrefix() {
				return "[mempool] ";
			}
		}

		node.submit(new AddToMempoolTask());
	}
}
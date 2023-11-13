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

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.api.Transaction;

/**
 * Bridge class to give access to protected methods to its subclass,
 * that represents the mempool of a Mokamint node.
 */
@ThreadSafe
public abstract class AbstractMempool {

	/**
	 * The node having this mempool.
	 */
	private final LocalNodeImpl node;

	/**
	 * Creates the mempool of a Mokamint node.
	 * 
	 * @param node the node having the mempool
	 */
	protected AbstractMempool(LocalNodeImpl node) {
		this.node = node;
	}

	/**
	 * Yields the node having this mempool.
	 * 
	 * @return the node having this mempool
	 */
	protected final LocalNodeImpl getNode() {
		return node;
	}

	/**
	 * @see LocalNodeImpl#onTransactionAdded(io.mokamint.node.api.Transaction).
	 */
	protected void onTransactionAdded(Transaction transaction) {
		node.onTransactionAdded(transaction);
	}
}
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

package io.mokamint.node.local.internal.blockchain;

import java.util.ArrayList;
import java.util.List;

import io.hotmoka.annotations.GuardedBy;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.mempool.Mempool.TransactionEntry;

/**
 * A task that executes the transactions taken from a queue.
 * It works while a block mining task looks for a deadline.
 * Once the deadline expires, all transactions executed by this task
 * can be added to the new block.
 */
public class TransactionExecutionTask implements Task {

	public interface Source {
		TransactionEntry take() throws InterruptedException;
	}

	private final Source source;

	/**
	 * The transactions that have been executed up to now.
	 */
	@GuardedBy("itself")
	private final List<TransactionEntry> transactions = new ArrayList<>();

	public TransactionExecutionTask(Source source) {
		this.source = source;
	}

	@Override
	public void body() throws InterruptedException {
		while (true) {
			TransactionEntry next = source.take();
			// TODO: check and deliver
			transactions.add(next);
		}
	}
}
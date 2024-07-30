/*
Copyright 2024 Fausto Spoto

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

package io.mokamint.node.local.internal;

import java.util.concurrent.TimeoutException;

import io.mokamint.node.api.NodeException;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;

/**
 * A task that synchronizes the blockchain from the peers.
 * That is, it asks the peers about their best chain from the genesis to the head
 * and downloads the blocks in that chain, exploiting parallelism as much as possible.
 */
public class SynchronizationTask implements Task {

	/**
	 * The node performing the mining.
	 */
	private final LocalNodeImpl node;

	/**
	 * Creates a task that synchronizes the blockchain from the peers.
	 * 
	 * @param node the node for which synchronization is performed
	 */
	public SynchronizationTask(LocalNodeImpl node) {
		this.node = node;
	}

	@Override
	public void body() throws InterruptedException, TimeoutException, NodeException {
		node.getBlockchain().synchronize();
	}
}
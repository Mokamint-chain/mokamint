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

import java.util.logging.Logger;

import io.hotmoka.annotations.OnThread;
import io.mokamint.node.api.Block;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;

/**
 * A task that synchronizes the blockchain from a given block downwards.
 * That is, it asks the peers about a chain from the block towards the genesis
 * block. If that chain is found, it adds it to the blockchain.
 */
public class SynchronizationTask implements Task {

	/**
	 * The node performing the mining.
	 */
	private final LocalNodeImpl node;

	/**
	 * The block from which the synchronization starts.
	 */
	private final Block start;

	/**
	 * The hash of {@link #start}, as a hexadecimal string.
	 */
	private final String hexStartHash;

	private final static Logger LOGGER = Logger.getLogger(SynchronizationTask.class.getName());

	/**
	 * Creates a task that synchronizes the blockchain from a given starting block downwards,
	 * towards the genesis block.
	 * 
	 * @param node the node requesting the synchronization
	 * @param start the block from which synchronization starts
	 */
	public SynchronizationTask(LocalNodeImpl node, Block start) {
		this.node = node;
		this.start = start;
		this.hexStartHash = start.getHexHash(node.getConfig().getHashingForBlocks());
	}

	@Override
	public String logPrefix() {
		return "";
	}

	@Override
	public String toString() {
		return "chain synchronization from block " + hexStartHash;
	}

	@Override @OnThread("tasks")
	public void body() {
		LOGGER.info("starting " + this);
	}
}
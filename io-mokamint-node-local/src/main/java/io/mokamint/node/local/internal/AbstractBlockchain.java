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
import io.mokamint.miner.api.Miner;
import io.mokamint.node.api.Block;
import io.mokamint.nonce.api.Deadline;

/**
 * Bridge class to give access to protected methods to its subclass,
 * that represents the blockchain of a Mokamint node.
 */
@ThreadSafe
public abstract class AbstractBlockchain {

	/**
	 * The node having this blockchain.
	 */
	private final LocalNodeImpl node;

	/**
	 * Creates the blockchain of a Mokamint node.
	 * 
	 * @param node the node having the blockchain
	 */
	protected AbstractBlockchain(LocalNodeImpl node) {
		this.node = node;
	}

	/**
	 * Yields the node having this blockchain.
	 * 
	 * @return the node having this blockchain
	 */
	protected final LocalNodeImpl getNode() {
		return node;
	}

	/**
	 * @see LocalNodeImpl#onBlockAdded(Block).
	 */
	protected void onBlockAdded(Block block) {
		node.onBlockAdded(block);
	}

	/**
	 * @see LocalNodeImpl#onBlockMined(Block).
	 */
	protected void onBlockMined(Block block) {
		node.onBlockMined(block);
	}

	/**
	 * @see LocalNodeImpl#onNoDeadlineFound(Block).
	 */
	protected void onNoDeadlineFound(Block previous) {
		node.onNoDeadlineFound(previous);
	}

	/**
	 * @see LocalNodeImpl#onIllegalDeadlineComputed(Deadline, Miner).
	 */
	protected void onIllegalDeadlineComputed(Deadline deadline, Miner miner) {
		node.onIllegalDeadlineComputed(deadline, miner);
	}

	/**
	 * @see LocalNodeImpl#onNoMinersAvailable().
	 */
	protected void onNoMinersAvailable() {
		node.onNoMinersAvailable();
	}

	/**
	 * @see LocalNodeImpl#scheduleWhisperingWithoutAddition(Block).
	 */
	protected void scheduleWhisperingWithoutAddition(Block block) {
		node.scheduleWhisperingWithoutAddition(block);
	}
}
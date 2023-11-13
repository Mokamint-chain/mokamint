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

/**
 * Bridge class to give access to protected methods to its subclass,
 * that represents the miners of a Mokamint node.
 */
@ThreadSafe
public abstract class AbstractMiners {

	/**
	 * The node having these miners.
	 */
	private final LocalNodeImpl node;

	/**
	 * Creates the miners of a Mokamint node.
	 * 
	 * @param node the node having the miners
	 */
	protected AbstractMiners(LocalNodeImpl node) {
		this.node = node;
	}

	/**
	 * Yields the node having these miners.
	 * 
	 * @return the node having these miners
	 */
	protected final LocalNodeImpl getNode() {
		return node;
	}

	/**
	 * @see LocalNodeImpl#onMinerAdded(Miner).
	 */
	protected void onMinerAdded(Miner miner) {
		node.onMinerAdded(miner);
	}

	/**
	 * @see LocalNodeImpl#onMinerRemoved(Miner).
	 */
	protected void onMinerRemoved(Miner miner) {
		node.onMinerRemoved(miner);
	}
}
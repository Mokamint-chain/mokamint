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

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.node.api.Block;

/**
 * The callbacks of a local Mokamint node, related to its blockchain.
 */
@ThreadSafe
public abstract class BlockchainCallbacks {

	private final static Logger LOGGER = Logger.getLogger(BlockchainCallbacks.class.getName());

	/**
	 * Called when no deadline has been found.
	 * 
	 * @param previous the block for whose subsequent block the deadline was being looked up
	 */
	protected void onNoDeadlineFound(Block previous) {
		LOGGER.info("height " + (previous.getHeight() + 1) + ": no deadline found on top of block " + previous.getHexHash(getHashingForBlocks()));
	}

	/**
	 * Called when a block gets added to the database of blocks.
	 * 
	 * @param block the added block
	 */
	protected void onBlockAdded(Block block) {
		LOGGER.info("height " + block.getHeight() + ": added block " + block.getHexHash(getHashingForBlocks()));
	}

	/**
	 * Yields the hashing algorithm used for the blocks.
	 * 
	 * @return the hashing algorithm
	 */
	protected abstract HashingAlgorithm getHashingForBlocks();
}
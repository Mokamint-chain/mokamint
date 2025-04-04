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

package io.mokamint.node.api;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.nonce.api.Deadline;

/**
 * The description of a non-genesis block of the Mokamint blockchain.
 * This is the header of a block, missing signature and transactions
 * wrt an actual block.
 */
@Immutable
public interface NonGenesisBlockDescription extends BlockDescription {

	/**
	 * Yields the deadline computed for the block.
	 * 
	 * @return the deadline
	 */
	Deadline getDeadline();

	/**
	 * Yields the reference to the previous block.
	 * 
	 * @return the reference to the previous block
	 */
	byte[] getHashOfPreviousBlock();

	/**
	 * Yields the hashing algorithm used for the transactions in the blocks.
	 * 
	 * @return the hashing algorithm used for the transactions in the blocks
	 */
	HashingAlgorithm getHashingForTransactions();
}
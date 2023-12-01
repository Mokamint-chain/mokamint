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

import io.hotmoka.annotations.Immutable;

/**
 * The address of a transaction inside the blockchain (block hash and position inside
 * the transactions' table of that block).
 */
@Immutable
public class TransactionAddress {

	/**
	 * The hash of the block containing the transaction.
	 */
	private final byte[] blockHash;

	/**
	 * The progressive number of the transaction inside the table of the
	 * transactions inside the block.
	 */
	private final int progressive;

	/**
	 * Creates a reference to a transaction inside a block.
	 * 
	 * @param blockHash the hash of the block containing the transaction
	 * @param progressive the progressive number of the transaction inside the table of the
	 *                    transactions inside the block
	 */
	TransactionAddress(byte[] blockHash, int progressive) {
		this.blockHash = blockHash.clone();
		this.progressive = progressive;
	}

	/**
	 * Yields the hash of the block containing the transaction.
	 * 
	 * @return the hash of the block containing the transaction
	 */
	public byte[] getBlockHash() {
		return blockHash.clone();
	}

	/**
	 * Yields the progressive number of the transaction inside the table of the transactions inside the block.
	 *                    
	 * @return the progressive number
	 */
	public int getProgressive() {
		return progressive;
	}
}
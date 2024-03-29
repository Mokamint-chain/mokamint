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

package io.mokamint.node.internal;

import java.util.Arrays;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Hex;
import io.mokamint.node.api.TransactionAddress;

/**
 * The implementation of the address of a transaction inside a blockchain
 * (block hash and position inside the table of the transactions of that block).
 */
@Immutable
public class TransactionAddressImpl implements TransactionAddress {

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
	public TransactionAddressImpl(byte[] blockHash, int progressive) {
		if (progressive < 0)
			throw new IllegalArgumentException("progressive cannot be negative");

		this.blockHash = blockHash.clone();
		this.progressive = progressive;
	}

	@Override
	public byte[] getBlockHash() {
		return blockHash.clone();
	}

	@Override
	public int getProgressive() {
		return progressive;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof TransactionAddress ta &&
			ta.getProgressive() == progressive &&
			Arrays.equals(ta.getBlockHash(), blockHash);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(blockHash) ^ progressive;
	}

	@Override
	public String toString() {
		return "#" + progressive + "@" + Hex.toHexString(blockHash);
	}
}
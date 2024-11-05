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
import java.util.Objects;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.internal.gson.TransactionAddressJson;

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

		this.progressive = progressive;
		this.blockHash = Objects.requireNonNull(blockHash).clone();
	}

	/**
	 * Creates a reference to a transaction from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public TransactionAddressImpl(TransactionAddressJson json) throws InconsistentJsonException {
		int progressive = json.getProgressive();
		if (progressive < 0)
			throw new InconsistentJsonException("progressive cannot be negative");

		String blockHash = json.getBlockHash();
		if (blockHash == null)
			throw new InconsistentJsonException("blockHash cannot be null");

		try {
			this.blockHash = Hex.fromHexString(blockHash);
		}
		catch (HexConversionException e) {
			throw new InconsistentJsonException(e);
		}

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
			Arrays.equals(ta instanceof TransactionAddressImpl tai ? tai.blockHash : ta.getBlockHash(), blockHash); // optimization
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
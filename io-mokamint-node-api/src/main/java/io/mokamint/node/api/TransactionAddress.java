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

/**
 * The address of a transaction inside a blockchain
 * (block hash and position inside the table of the transactions of that block).
 */
@Immutable
public interface TransactionAddress {

	/**
	 * Yields the hash of the block containing the transaction.
	 * 
	 * @return the hash of the block containing the transaction
	 */
	byte[] getBlockHash();

	/**
	 * Yields the progressive number of the transaction inside the table of the transactions inside the block.
	 *                    
	 * @return the progressive number
	 */
	int getProgressive();

	@Override
	boolean equals(Object other);

	@Override
	int hashCode();

	@Override
	String toString();
}
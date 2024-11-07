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

import java.util.stream.Stream;

import io.hotmoka.annotations.Immutable;

/**
 * A non-genesis block of the Mokamint blockchain.
 */
@Immutable
public interface NonGenesisBlock extends Block {

	@Override
	NonGenesisBlockDescription getDescription();

	/**
	 * Yields the reference to the previous block.
	 * 
	 * @return the reference to the previous block
	 */
	byte[] getHashOfPreviousBlock();

	/**
	 * Yields the transactions inside this block.
	 * 
	 * @return the transactions
	 */
	Stream<Transaction> getTransactions();

	/**
	 * Yields the number of transactions inside this block.
	 * 
	 * @return the number of transactions
	 */
	int getTransactionsCount();

	/**
	 * Yields the {@code progressive}th transaction inside this block.
	 * 
	 * @param progressive the index of the transaction
	 * @return the transaction
	 * @throws IndexOutOfBoundsException if there is no transaction with that progressive index
	 */
	Transaction getTransaction(int progressive);
}
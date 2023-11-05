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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hotmoka.annotations.Immutable;
import io.mokamint.node.api.MempoolPortion;
import io.mokamint.node.api.TransactionInfo;

/**
 * Implementation of information about the transactions of a sorted, sequential portion of the
 * mempool of a Mokamint node.
 */
@Immutable
public class MempoolPortionImpl implements MempoolPortion {

	/**
	 * The transaction information objects in the sequence, in increasing order of transaction priority.
	 */
	private final TransactionInfo[] transactions;

	/**
	 * Constructs an object containing information transaction objects of a sequential
	 * portion of the mempool of a Mokamint node.
	 * 
	 * @param transactions the transaction information objects, in increasing order of transaction priority
	 */
	public MempoolPortionImpl(Stream<TransactionInfo> transactions) {
		this.transactions = transactions.toArray(TransactionInfo[]::new);
	}

	@Override
	public Stream<TransactionInfo> getTransactions() {
		return Stream.of(transactions);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof MempoolPortion mpp &&
			Arrays.deepEquals(transactions, mpp.getTransactions().toArray(TransactionInfo[]::new));
	}

	@Override
	public String toString() {
		return getTransactions().map(TransactionInfo::toString).collect(Collectors.joining("\n"));
	}
}
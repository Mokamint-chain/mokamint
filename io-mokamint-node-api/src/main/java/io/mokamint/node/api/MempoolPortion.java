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
 * Information about the transactions of a sorted, sequential portion of the
 * mempool of a Mokamint node.
 */
@Immutable
public interface MempoolPortion {

	/**
	 * Yields the information about the transactions of the sequential portion of the mempool, sorted
	 * in increasing order of transaction priority.
	 * 
	 * @return the mempool entries containing the transactions
	 */
	Stream<MempoolEntry> getEntries();

	@Override
	boolean equals(Object other);

	@Override
	String toString();
}
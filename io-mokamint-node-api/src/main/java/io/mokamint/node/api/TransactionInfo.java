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
import io.hotmoka.marshalling.api.Marshallable;

/**
 * Information about a transaction of the Mokamint blockchain.
 */
@Immutable
public interface TransactionInfo extends Marshallable {

	/**
	 * Yields the hash of the transaction.
	 * 
	 * @return the hash of the transaction
	 */
	byte[] getHash();

	/**
	 * Yields the priority of the transaction.
	 * 
	 * @return the priority
	 */
	long getPriority();

	@Override
	boolean equals(Object other);

	@Override
	int hashCode();

	@Override
	String toString();
}
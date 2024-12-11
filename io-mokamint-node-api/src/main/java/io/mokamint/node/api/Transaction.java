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

import java.io.IOException;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.Hasher;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.Marshallable;
import io.hotmoka.marshalling.api.MarshallingContext;

/**
 * A transaction of the Mokamint blockchain. Transactions are ordered
 * by increasing bytes, lexicographically.
 */
@Immutable
public interface Transaction extends Marshallable, Whisperable, Comparable<Transaction> {

	/**
	 * Yields the bytes of the transaction. The meaning of these bytes if application-dependent.
	 * 
	 * @return the bytes of the transaction
	 */
	byte[] getBytes();

	@Override
	String toString();

	/**
	 * Yields the hashing algorithm to use for this transaction.
	 * 
	 * @return the hashing algorithm
	 */
	HashingAlgorithm getHashing();

	/**
	 * Yields the hexadecimal hash of this transaction, by using the given hasher.
	 * 
	 * @param hasher the hasher
	 * @return the hash, as a string
	 */
	String getHexHash(Hasher<Transaction> hasher);

	/**
	 * Marshals this object into a given stream. This method in general
	 * performs better than standard Java serialization, wrt the size of the marshalled data.
	 * It does not report information that can be recomputed from the configuration of the
	 * node storing this transaction.
	 * 
	 * @param context the context holding the stream
	 * @throws IOException if this object cannot be marshalled
	 */
	void intoWithoutConfigurationData(MarshallingContext context) throws IOException;
}
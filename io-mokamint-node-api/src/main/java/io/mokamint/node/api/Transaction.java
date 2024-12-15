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
import io.hotmoka.crypto.api.Hasher;
import io.hotmoka.marshalling.api.Marshallable;

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

	/**
	 * Yields the bytes of this transaction, Base64-encoded.
	 * 
	 * @return the Based64-encoded bytes
	 */
	String toBase64String();

	@Override
	String toString();

	/**
	 * Yields the hexadecimal hash of this transaction, by using the given hasher.
	 * 
	 * @param hasher the hasher
	 * @return the hash, as a string
	 */
	String getHexHash(Hasher<Transaction> hasher);
}
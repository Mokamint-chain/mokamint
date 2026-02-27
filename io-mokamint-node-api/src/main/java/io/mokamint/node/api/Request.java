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
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.Marshallable;

/**
 * A request to execute a transaction on a Mokamint blockchain. Requests are ordered
 * by increasing bytes, lexicographically.
 */
@Immutable
public interface Request extends Marshallable, Whisperable, Comparable<Request> {

	/**
	 * Yields the bytes of the request. The meaning of these bytes is application-dependent.
	 * 
	 * @return the bytes of the request
	 */
	byte[] getBytes();

	/**
	 * Yields the number of bytes of this request.
	 * 
	 * @return the number of bytes of this request
	 */
	int getNumberOfBytes();

	/**
	 * Yields the bytes of this request, Base64-encoded.
	 * 
	 * @return the Based64-encoded bytes
	 */
	String toBase64String();

	/**
	 * Yields the hexadecimal hash of this request, by using the given hashing algorithm.
	 * 
	 * @param hashing the hashing algorithm
	 * @return the hash, as a string
	 */
	String getHexHash(HashingAlgorithm hashing);

	/**
	 * Yields the hash of this request, by using the given hashing algorithm.
	 * 
	 * @param hashing the hashing algorithm
	 * @return the hash
	 */
	byte[] getHash(HashingAlgorithm hashing);

	@Override
	String toString();
}
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

package io.mokamint.nonce.api;

import io.hotmoka.crypto.api.HashingAlgorithm;

/**
 * The description of a deadline. This can be provided for instance to
 * a miner to describe the properties if the deadline one is looking for.
 */
public interface DeadlineDescription {

	/**
	 * Yields the number of the scoop considered to compute the deadline.
	 * 
	 * @return the number of the scoop
	 */
	int getScoopNumber();

	/**
	 * Yields the data used to compute the deadline.
	 * 
	 * @return the data
	 */
	byte[] getData();

	/**
	 * The hashing algorithm used for the plot file from which
	 * this deadline has been generated.
	 */
	HashingAlgorithm<byte[]> getHashing();

	/**
	 * Yields a string representation of this deadline description.
	 * 
	 * @return the string representation
	 */
	String toString();
}
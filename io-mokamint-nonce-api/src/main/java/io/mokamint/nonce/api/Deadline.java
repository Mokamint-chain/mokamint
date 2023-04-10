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
 * A deadline inside a plot file. It is a reference to a nonce
 * and a value computed for that nonce. Deadlines are ordered
 * by the lexicographical ordering of their values.
 */
public interface Deadline {

	/**
	 * The prolog that was used to create the plot file from which
	 * this deadline has been generated.
	 * 
	 * @return the prolog
	 */
	byte[] getProlog();

	/**
	 * The progressive number of the nonce of the deadline. It is between
	 * {@link Plot#getStart()} (inclusive) and {@link Plot#getStart()}+{@link Plot#getLength()}.
	 */
	long getProgressive();

	/**
	 * The value of the deadline computed for the nonce {@link #getProgressive()}
	 * of the plot file.
	 */
	byte[] getValue();

	/**
	 * Yields the number of the scoop considered to compute tre deadline.
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
	 * Computes the nonce whose deadline is this.
	 * 
	 * @return the nonce
	 */
	Nonce toNonce();

	/**
	 * Checks if this deadline is equal to another object (same progressive, same value).
	 * 
	 * @param other the other object
	 * @return true if and only if {@code other} is a {@link Deadline} with the same data
	 */
	boolean equals(Object other);

	/**
	 * Compares the value of this deadline with that of another deadline.
	 * They are assumed to have been generated with the same hashing algorithm.
	 * 
	 * @param other the other deadline
	 * @return negative if the value of this deadline is smaller than the
	 *         value of the other deadline; positive if the value of this deadline
	 *         if larger than the value of the other deadline; 0 if the value are equal
	 */
	int compareByValue(Deadline other);

	/**
	 * Yields a string representation of this deadline.
	 * 
	 * @return the string representation
	 */
	String toString();
}
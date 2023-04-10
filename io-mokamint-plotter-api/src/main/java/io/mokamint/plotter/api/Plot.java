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

package io.mokamint.plotter.api;

import java.io.IOException;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.spacemint.nonce.api.Deadline;
import io.hotmoka.spacemint.nonce.api.Nonce;

/**
 * A plot file, containing sequential nonces. Each nonce contains
 * a sequence of scoops. Each scoop contains a pair of hashes.
 */
public interface Plot extends AutoCloseable {

	/**
	 * The maximal length of the prolog of a plot, in bytes.
	 */
	public final int MAX_PROLOG_SIZE = Nonce.MAX_PROLOG_SIZE; // 16 megabytes

	/**
	 * Yields the prolog of this plot.
	 * 
	 * @return the prolog
	 */
	byte[] getProlog();

	/**
	 * Yields the starting progressive number of the nonces inside this plot.
	 * 
	 * @return the starting progressive number
	 */
	long getStart();

	/**
	 * Yields the number of nonces in this plot.
	 * 
	 * @return the number of nonces
	 */
	long getLength();

	/**
	 * Yields the hashing algorithm used by this plot.
	 * 
	 * @return the hashing algorithm
	 */
	HashingAlgorithm<byte[]> getHashing();

	@Override
	void close() throws IOException;

	/**
	 * Yields the smallest deadline for the given scoop number and data
	 * in this plot file. This method selects the given scoop
	 * for all nonces contained in this plot file. For each scoop, it computes
	 * its deadline value by hashing the scoop data and the provided {@code data}.
	 * It returns the pair (progressive of the nonce, deadline value)
	 * with the smallest value. It uses the same hashing algorithm used for
	 * creating this plot file.
	 * 
	 * @param scoopNumber the number of the scoop to consider
	 * @param data the data to hash together with the scoop data
	 * @return the smallest deadline
	 * @throws IOException if the plot file cannot be read
	 */
	Deadline getSmallestDeadline(int scoopNumber, byte[] data) throws IOException;
}

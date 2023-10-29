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
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.SignatureException;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import io.mokamint.nonce.api.Prolog;

/**
 * A plot file, containing sequential nonces. Each nonce contains
 * a sequence of scoops. Each scoop contains a pair of hashes.
 */
@Immutable
public interface Plot extends AutoCloseable {

	/**
	 * Yields the prolog of this plot.
	 * 
	 * @return the prolog
	 */
	Prolog getProlog();

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
	HashingAlgorithm getHashing();

	@Override
	void close() throws IOException, InterruptedException;

	/**
	 * Yields the smallest deadline in this plot file, matching the given description.
	 * This method selects the scoop in the description
	 * for all nonces contained in this plot file. For each scoop, it computes
	 * its deadline value by hashing the scoop data and the provided description's {@code data}.
	 * It returns the pair (progressive of the nonce, deadline value)
	 * with the smallest value.
	 * 
	 * @param description the description of the requested deadline
	 * @param privateKey the private key used to sign the deadline
	 * @return the smallest deadline
	 * @throws InterruptedException if the thread is interrupted while waiting for the computation
	 *                              of the smallest deadline
	 * @throws IOException if the plot file cannot be read
	 * @throws IllegalArgumentException if the description is for a deadline using a different
	 *                                  hashing algorithm than that used to create this plot file
	 * @throws InvalidKeyException if {@code privateKey} is invalid
	 * @throws SignatureException if the deadline could not be signed
	 */
	Deadline getSmallestDeadline(DeadlineDescription description, PrivateKey privateKey) throws InterruptedException, IOException, InvalidKeyException, SignatureException;
}

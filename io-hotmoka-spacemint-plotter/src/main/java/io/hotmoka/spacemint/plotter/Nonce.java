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

package io.hotmoka.spacemint.plotter;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.plotter.internal.NonceImpl;

/**
 * A nonce of a plot file. Each nonce contains scoops.
 * Each scoop contains a pair of hashes.
 */
public interface Nonce {

	/**
	 * The number of scoops containted in a nonce.
	 */
	public final static int SCOOPS_PER_NONCE = 4096;

	/**
	 * Yields the nonce for the given data and with the given number.
	 * 
	 * @param prolog generic data that identifies, for instance, the creator
	 *               of the nonce. This can be really anything, but cannot be {@code null}
	 * @param progressive the progressive number of the nonce. This must be non-negative
	 * @param hashing the hashing algorithm to use to create the nonce
	 * @return the nonce
	 */
	static Nonce of(byte[] prolog, long progressive, HashingAlgorithm<byte[]> hashing) {
		return new NonceImpl(prolog, progressive, hashing);
	}
}
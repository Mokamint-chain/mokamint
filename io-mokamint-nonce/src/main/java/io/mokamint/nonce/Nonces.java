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

package io.mokamint.nonce;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.Nonce;
import io.mokamint.nonce.internal.NonceImpl;

/**
 * A provider of nonces.
 */
public interface Nonces {

	/**
	 * Yields the nonce for the given data and with the given number, usingthe given
	 * hashing algorithm.
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

	/**
	 * Yields the nonce corresponding to the given deadline.
	 * 
	 * @param deadline the deadline
	 * @return the nonce
	 */
	static Nonce from(Deadline deadline) {
		return new NonceImpl(deadline.getProlog(),
			deadline.getProgressive(),
			deadline.getHashing());
	}
}
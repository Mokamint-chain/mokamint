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
import io.mokamint.nonce.api.Nonce;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.nonce.internal.NonceImpl;

/**
 * A provider of nonces.
 */
public abstract class Nonces {

	private Nonces() {}

	/**
	 * Yields the nonce for the given data and with the given number, using the given hashing algorithm.
	 * 
	 * @param prolog generic data that identifies, for instance, the creator of the nonce
	 * @param progressive the progressive number of the nonce. This must be non-negative
	 * @param hashingForDeadlines the hashing algorithm to use to create the nonce
	 * @return the nonce
	 */
	public static Nonce of(Prolog prolog, long progressive, HashingAlgorithm hashingForDeadlines) {
		return new NonceImpl(prolog, progressive, hashingForDeadlines);
	}
}
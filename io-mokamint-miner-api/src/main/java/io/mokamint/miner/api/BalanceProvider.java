/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.miner.api;

import java.math.BigInteger;
import java.security.PublicKey;
import java.util.Optional;

import io.hotmoka.crypto.api.SignatureAlgorithm;

/**
 * A provider of the balance of a key.
 */
public interface BalanceProvider {

	/**
	 * Yields the balance of the given public key.
	 * 
	 * @param signature the signature algorithm of {@code publicKey}
	 * @param publicKey the public key whose balance is requested
	 * @return the balance, if any
	 * @throws GetBalanceException if the balance could not be determined
	 */
	Optional<BigInteger> get(SignatureAlgorithm signature, PublicKey publicKey) throws GetBalanceException;
}

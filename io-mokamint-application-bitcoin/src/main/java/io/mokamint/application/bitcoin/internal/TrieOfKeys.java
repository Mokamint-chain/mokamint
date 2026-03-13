/*
Copyright 2026 Fausto Spoto

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

package io.mokamint.application.bitcoin.internal;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.Hasher;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.patricia.AbstractPatriciaTrie;
import io.hotmoka.patricia.api.KeyValueStore;
import io.hotmoka.patricia.api.UnknownKeyException;
import io.mokamint.application.ApplicationException;

/**
 * A Merkle-Patricia trie that maps public keys to their balance. It uses sha256 as hashing algorithm
 * for the trie's nodes and an array of 0's to represent the empty trie.
 */
public class TrieOfKeys extends AbstractPatriciaTrie<PublicKey, BigInteger, TrieOfKeys> {

	/**
	 * Builds a Merkle-Patricia trie that maps public keys to their balance.
	 * 
	 * @param store the supporting key/value store
	 * @param root the root of the trie to check out
	 * @throws UnknownKeyException if {@code root} cannot be found in the trie
	 */
	public TrieOfKeys(KeyValueStore store, byte[] root) throws UnknownKeyException {
		super(store, root, hasherOfPublicKeys(), sha256(), new byte[32], BigInteger::toByteArray, BigInteger::new);
	}

	private TrieOfKeys(TrieOfKeys cloned, byte[] root) throws UnknownKeyException {
		super(cloned, root);
	}

	private static HashingAlgorithm sha256() {
		try {
			return HashingAlgorithms.sha256();
		}
		catch (NoSuchAlgorithmException e) {
			throw new ApplicationException(e);
		}
	}

	private static SignatureAlgorithm ed25519() {
		try {
			return SignatureAlgorithms.ed25519();
		}
		catch (NoSuchAlgorithmException e) {
			throw new ApplicationException(e);
		}
	}

	private static Hasher<PublicKey> hasherOfPublicKeys() {
		return sha256().getHasher(publicKey -> {
			try {
				return ed25519().encodingOf(publicKey);
			}
			catch (InvalidKeyException e) {
				throw new ApplicationException(e);
			}
		});
	}

	@Override
	public TrieOfKeys checkoutAt(byte[] root) throws UnknownKeyException {
		return new TrieOfKeys(this, root);
	}
}
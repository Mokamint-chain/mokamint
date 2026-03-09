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
		super(store, root, mkHasherOfPublicKeys(), mkSHA256(), new byte[32], BigInteger::toByteArray, BigInteger::new);
	}

	private TrieOfKeys(TrieOfKeys cloned, byte[] root) throws UnknownKeyException {
		super(cloned, root);
	}

	private static HashingAlgorithm mkSHA256() {
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

	private static Hasher<PublicKey> mkHasherOfPublicKeys() {
		return mkSHA256().getHasher(publicKey -> {
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
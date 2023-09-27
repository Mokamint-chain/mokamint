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

import java.security.PublicKey;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.api.Marshallable;

/**
 * The prolog of a deadline.
 */
@Immutable
public interface Prolog extends Marshallable {

	/**
	 * The maximal length of the prolog, in bytes (inclusive).
	 */
	final int MAX_PROLOG_SIZE = 16777216; // 16 megabytes

	/**
	 * Yields the chain identifier of the blockchain where the deadline is legal.
	 * 
	 * @return the chain identifier
	 */
	String getChainId();

	/**
	 * Yields the signature algorithm that nodes must use to sign blocks
	 * having a deadline with this prolog, with the key {@link #getPublicKeyForSigningBlocks()}.
	 * 
	 * @return the signature algorithm
	 */
	SignatureAlgorithm<byte[]> getSignatureForBlocks();

	/**
	 * Yields the public key that must be used to sign the blocks having
	 * a deadline with this prolog.
	 * 
	 * @return the public key
	 */
	PublicKey getPublicKeyForSigningBlocks();

	/**
	 * Yields the public key that must be used to sign the blocks having
	 * a deadline with this prolog, in Base58 format.
	 * 
	 * @return the public key
	 */
	String getPublicKeyForSigningBlocksBase58();

	/**
	 * Yields the signature algorithm that miners must use to sign deadlines
	 * having this prolog, with the key {@link #getPublicKeyForSigningDeadlines()}.
	 * 
	 * @return the signature algorithm
	 */
	SignatureAlgorithm<byte[]> getSignatureForDeadlines();

	/**
	 * Yields the public key that miners must use to sign deadlines having this prolog.
	 * 
	 * @return the public key
	 */
	PublicKey getPublicKeyForSigningDeadlines();

	/**
	 * Yields the public key that miners must use to sign deadlines having this prolog,
	 * in Base58 format.
	 * 
	 * @return the public key
	 */
	String getPublicKeyForSigningDeadlinesBase58();

	/**
	 * Application-specific extra data in the prolog.
	 * 
	 * @return the extra data
	 */
	byte[] getExtra();

	@Override
	String toString();

	@Override
	boolean equals(Object other);

	@Override
	int hashCode();
}
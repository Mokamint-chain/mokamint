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

import java.io.IOException;
import java.security.PublicKey;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.api.Marshallable;
import io.hotmoka.marshalling.api.MarshallingContext;

/**
 * The prolog of a deadline.
 */
@Immutable
public interface Prolog extends Marshallable {

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
	SignatureAlgorithm getSignatureForBlocks();

	/**
	 * Yields the public key that must be used to sign the blocks having
	 * a deadline with this prolog.
	 * 
	 * @return the public key; this is guaranteed to be a valid key for
	 *         {@link #getSignatureForBlocks()}.
	 */
	PublicKey getPublicKeyForSigningBlocks();

	/**
	 * Yields the public key that must be used to sign the blocks having
	 * a deadline with this prolog, in Base58 format.
	 * 
	 * @return the public key; this is guaranteed to be a valid key for
	 *         {@link #getSignatureForBlocks()}
	 */
	String getPublicKeyForSigningBlocksBase58();

	/**
	 * Yields the signature algorithm that miners must use to sign deadlines
	 * having this prolog, with the key {@link #getPublicKeyForSigningDeadlines()}.
	 * 
	 * @return the signature algorithm
	 */
	SignatureAlgorithm getSignatureForDeadlines();

	/**
	 * Yields the public key that miners must use to sign deadlines having this prolog.
	 * 
	 * @return the public key; this is guaranteed to be a valid key for
	 *         {@link #getSignatureForDeadlines()}
	 */
	PublicKey getPublicKeyForSigningDeadlines();

	/**
	 * Yields the public key that miners must use to sign deadlines having this prolog,
	 * in Base58 format.
	 * 
	 * @return the public key; this is guaranteed to be a valid key for
	 *         {@link #getSignatureForDeadlines()}
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

	/**
	 * Marshals this object into a given stream. This method in general
	 * performs better than standard Java serialization, wrt the size of the marshalled data.
	 * It does not report information that can be recomputed from the configuration of the
	 * node storing this prolog.
	 * 
	 * @param context the context holding the stream
	 * @throws IOException if this object cannot be marshalled
	 */
	void intoWithoutConfigurationData(MarshallingContext context) throws IOException;

	@Override
	boolean equals(Object other);

	@Override
	int hashCode();
}
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
	 * The signature algorithm for the key {@link #getNodePublicKey()}.
	 * 
	 * @return the signature algorithm
	 */
	SignatureAlgorithm<byte[]> getNodeSignature();

	/**
	 * Yields the public key that the node using the deadlines with this prolog
	 * uses to sign new mined blocks.
	 * 
	 * @return the public key
	 */
	PublicKey getNodePublicKey();

	/**
	 * Yields the public key that the node using the deadlines with this prolog
	 * uses to sign new mined blocks, in Base58 format.
	 * 
	 * @return the public key
	 */
	String getNodePublicKeyBase58();

	/**
	 * The signature algorithm for the key {@link #getPlotPublicKey()}.
	 * 
	 * @return the signature algorithm
	 */
	SignatureAlgorithm<byte[]> getPlotSignature();

	/**
	 * Yields the public key that identifies the plots from which this deadline is derived.
	 * 
	 * @return the public key
	 */
	PublicKey getPlotPublicKey();

	/**
	 * Yields the public key that identifies the plots from which this deadline is derived,
	 * in Base58 format.
	 * 
	 * @return the public key
	 */
	String getPlotPublicKeyBase58();

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
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

package io.mokamint.node.api;

import java.security.PublicKey;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.SignatureAlgorithm;

/**
 * The genesis block of a Mokamint blockchain.
 */
@Immutable
public interface GenesisBlock extends GenesisBlockDescription, Block {

	/**
	 * Yields the signature algorithm used for signing this block.
	 * 
	 * @return the signature algorithm
	 */
	SignatureAlgorithm getSignatureForBlocks();

	/**
	 * Yields the public key that can be used to verify the
	 * signature of this block.
	 * 
	 * @return the public key
	 */
	PublicKey getPublicKey();

	/**
	 * Yields a Base58 representation of {@link #getPublicKey()}.
	 * 
	 * @return the Base58 representation
	 */
	String getPublicKeyBase58();
}
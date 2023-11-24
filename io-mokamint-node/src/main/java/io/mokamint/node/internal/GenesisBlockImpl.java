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

package io.mokamint.node.internal;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.GenesisBlockDescription;

/**
 * The implementation of a genesis block of a Mokamint blockchain.
 */
@Immutable
public class GenesisBlockImpl extends AbstractBlock<GenesisBlockDescription> implements GenesisBlock {

	/**
	 * Creates a genesis block with the given description and signs it with the given keys and signature algorithm.
	 * 
	 * @param description the description
	 * @param privateKey the key used for signing the block
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public GenesisBlockImpl(GenesisBlockDescription description, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		super(description, privateKey);
	}

	/**
	 * Creates a genesis block and signs it with the given keys and signature algorithm.
	 * 
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public GenesisBlockImpl(LocalDateTime startDateTimeUTC, BigInteger acceleration, SignatureAlgorithm signatureForBlocks, KeyPair keys) throws InvalidKeyException, SignatureException {
		super(new GenesisBlockDescriptionImpl(startDateTimeUTC, acceleration, signatureForBlocks, keys.getPublic()), keys.getPrivate());
	}

	/**
	 * Creates a new genesis block.
	 *
	 * @throws InvalidKeySpecException if the public key is invalid
	 */
	public GenesisBlockImpl(LocalDateTime startDateTimeUTC, BigInteger acceleration, SignatureAlgorithm signatureForBlocks, String publicKeyBase58, byte[] signature) throws InvalidKeySpecException {
		super(new GenesisBlockDescriptionImpl(startDateTimeUTC, acceleration, signatureForBlocks, publicKeyBase58), signature);
	}

	/**
	 * Unmarshals a genesis block from the given context. The description of the block has been already read.
	 * 
	 * @param description the description of the block
	 * @param context the context
	 * @return the block
	 * @throws IOException if the block cannot be unmarshalled
	 */
	GenesisBlockImpl(GenesisBlockDescriptionImpl description, UnmarshallingContext context) throws IOException {
		super(description, context);
	}

	@Override
	public LocalDateTime getStartDateTimeUTC() {
		return getDescription().getStartDateTimeUTC();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GenesisBlock && super.equals(other);
	}
}
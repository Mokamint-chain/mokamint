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
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.time.LocalDateTime;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.internal.gson.BlockJson;

/**
 * The implementation of a genesis block of a Mokamint blockchain.
 */
@Immutable
public non-sealed class GenesisBlockImpl extends AbstractBlock<GenesisBlockDescription, GenesisBlockImpl> implements GenesisBlock {

	/**
	 * Creates a genesis block with the given description and signs it with the given keys and signature algorithm.
	 * 
	 * @param description the description
	 * @param stateId the identifier of the state of the application at the end of this block
	 * @param privateKey the key used for signing the block
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public GenesisBlockImpl(GenesisBlockDescription description, byte[] stateId, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		super(description, stateId, privateKey, AbstractBlock::toByteArrayWithoutSignature);
	}

	/**
	 * Creates a genesis block from the given JSON representation.
	 * 
	 * @param description the description of the block
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public GenesisBlockImpl(GenesisBlockDescription description, BlockJson json) throws InconsistentJsonException {
		super(description, json);

		try {
			ensureSignatureIsCorrect();
		}
		catch (InvalidKeyException | SignatureException e) {
			throw new InconsistentJsonException(e);
		}
	}

	/**
	 * Unmarshals a genesis block from the given context. The description of the block has been already read.
	 * 
	 * @param description the description of the block
	 * @param context the context
	 * @return the block
	 * @throws IOException if the block cannot be unmarshalled
	 */
	protected GenesisBlockImpl(GenesisBlockDescription description, UnmarshallingContext context) throws IOException {
		super(description, context);

		try {
			ensureSignatureIsCorrect();
		}
		catch (InvalidKeyException | SignatureException e) {
			throw new IOException(e);
		}
	}

	@Override
	public LocalDateTime getStartDateTimeUTC() {
		return getDescription().getStartDateTimeUTC();
	}

	@Override
	protected GenesisBlockImpl getThis() {
		return this;
	}
}
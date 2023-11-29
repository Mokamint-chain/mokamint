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
import java.util.function.Function;
import java.util.stream.Stream;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.api.Transaction;

/**
 * The implementation of a genesis block of a Mokamint blockchain.
 */
@Immutable
public non-sealed class GenesisBlockImpl extends AbstractBlock<GenesisBlockDescription> implements GenesisBlock {

	/**
	 * Creates a genesis block with the given description and signs it with the given keys and signature algorithm.
	 * 
	 * @param description the description
	 * @param transactions the transactions inside the block
	 * @param privateKey the key used for signing the block
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public GenesisBlockImpl(GenesisBlockDescription description, Stream<Transaction> transactions, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		super(description, transactions, privateKey);
	}

	/**
	 * Creates a new genesis block with the given description and signature.
	 *
	 * @param description the description
	 * @param transactions the transactions inside the block
	 * @param signature the signature
	 */
	public GenesisBlockImpl(GenesisBlockDescription description, Stream<Transaction> transactions, byte[] signature) {
		super(description, transactions, signature);
	}

	/**
	 * Unmarshals a genesis block from the given context. The description of the block has been already read.
	 * 
	 * @param description the description of the block
	 * @param context the context
	 * @return the block
	 * @throws IOException if the block cannot be unmarshalled
	 */
	GenesisBlockImpl(GenesisBlockDescription description, UnmarshallingContext context) throws IOException {
		super(description, context);
	}

	@Override
	public LocalDateTime getStartDateTimeUTC() {
		return getDescription().getStartDateTimeUTC();
	}

	@Override
	public <E extends Exception> void matchesOrThrow(BlockDescription description, Function<String, E> exceptionSupplier) throws E {
		if (description instanceof GenesisBlockDescription) {
			var acceleration = getDescription().getAcceleration();
			if (!acceleration.equals(description.getAcceleration()))
				throw exceptionSupplier.apply("Acceleration mismatch (expected " + description.getAcceleration() + " but found " + acceleration + ")");

			var signatureForBlocks = getDescription().getSignatureForBlock();
			if (!signatureForBlocks.equals(description.getSignatureForBlock()))
				throw exceptionSupplier.apply("Block signature algorithm mismatch (expected " + description.getSignatureForBlock() + " but found " + signatureForBlocks + ")");

			// TODO: in the future, maybe check for the public key as well
		}
		else
			throw exceptionSupplier.apply("Block type mismatch (expected non-genesis but found genesis)");
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GenesisBlock && super.equals(other);
	}
}
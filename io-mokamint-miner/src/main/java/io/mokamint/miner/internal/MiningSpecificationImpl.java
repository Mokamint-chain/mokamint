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

package io.mokamint.miner.internal;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.exceptions.ExceptionSupplier;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.internal.json.MiningSpecificationJson;

/**
 * Implementation of the specification of the mining parameter for the deadlines expected by a miner.
 */
@Immutable
public class MiningSpecificationImpl implements MiningSpecification {

	/**
	 * The chain id of the deadlines expected by a miner having this specification.
	 */
	private final String chainId;

	/**
	 * The hashing algorithm used for computing the deadlines expected by a miner
	 * having this specification.
	 */
	private final HashingAlgorithm hashingForDeadlines;

	/**
	 * The signature algorithm used for the key identifying the node
	 * in the deadlines expected by a miner having this specification.
	 */
	private final SignatureAlgorithm signatureForBlocks;

	/**
	 * The signature algorithm used for the key identifying the plot
	 * containing the deadlines expected by a miner having this specification.
	 */
	private final SignatureAlgorithm signatureForDeadlines;

	/**
	 * The public key identifying the node in the deadlines
	 * expected by a miner having this specification. This is a public key for the
	 * {@link #signatureForBlocks} algorithm.
	 */
	private final PublicKey publicKeyForSigningBlocks;

	/**
	 * The Base58-encoded public key identifying the node in the deadlines
	 * expected by a miner having this specification. This is a public key for the
	 * {@link #signatureForBlocks} algorithm.
	 */
	private final String publicKeyForSigningBlocksBase58;

	/**
	 * Creates the mining specification for a miner.
	 * 
	 * @param chainId the chain id of the deadlines expected by the miner
	 * @param hashingForDeadlines the hashing algorithm used for computing the deadlines expected by a miner
	 *                            having this specification
	 * @param signatureForBlocks the signature algorithm used for the key identifying the node
	 *                           in the deadlines expected by a miner having this specification
	 * @param signatureForDeadlines the signature algorithm used for the key identifying the plot
	 *                              containing the deadlines expected by a miner having this specification
	 * @param publicKeyForSigningBlocks the public key identifying the node in the deadlines
	 *                                  expected by a miner having this specification. This is a public key for the
	 *                                  {@code signatureForBlocks} algorithm
	 * @throws InvalidKeyException if {@code publicKeyForSigningBlocks} is invalid
	 */
	public MiningSpecificationImpl(String chainId, HashingAlgorithm hashingForDeadlines, SignatureAlgorithm signatureForBlocks, SignatureAlgorithm signatureForDeadlines, PublicKey publicKeyForSigningBlocks) throws IllegalArgumentException, InvalidKeyException {
		this(chainId, hashingForDeadlines, signatureForBlocks, signatureForDeadlines, publicKeyForSigningBlocks, IllegalArgumentException::new);
	}

	/**
	 * Creates a mining specification object from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 * @throws NoSuchAlgorithmException if the mining specification uses a non-available cryptographic algorithm
	 */
	public MiningSpecificationImpl(MiningSpecificationJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
		this(
			json.getChainId(),
			HashingAlgorithms.of(Objects.requireNonNull(json.getHashingForDeadlines(), "hashingForDeadlines cannot be null", InconsistentJsonException::new)),
			SignatureAlgorithms.of(Objects.requireNonNull(json.getSignatureForBlocks(), "signatureForBlocks cannot be null", InconsistentJsonException::new)),
			SignatureAlgorithms.of(Objects.requireNonNull(json.getSignatureForDeadlines(), "signatureForDeadlines cannot be null", InconsistentJsonException::new)),
			Objects.requireNonNull(json.getPublicKeyForSigningBlocksBase58(), "publicKeyForSigningBlocksBase58 cannot be null", InconsistentJsonException::new),
			InconsistentJsonException::new
		);
	}

	/**
	 * Builds a mining specification for a miner.
	 * 
	 * @param chainId the chain id of the deadlines expected by the miner
	 * @param hashingForDeadlines the hashing algorithm used for computing the deadlines expected by a miner
	 *                            having this specification
	 * @param signatureForBlocks the signature algorithm used for the key identifying the node
	 *                           in the deadlines expected by a miner having this specification
	 * @param signatureForDeadlines the signature algorithm used for the key identifying the plot
	 *                              containing the deadlines expected by a miner having this specification
	 * @param publicKeyForSigningBlocks the public key identifying the node in the deadlines
	 *                                  expected by a miner having this specification. This is a public key for the
	 *                                  {@code signatureForBlocks} algorithm
	 * @param onIllegalArgs the generator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 * @throws InvalidKeyException if {@code publicKeyForSigningBlocks} is invalid
	 */
	private <E extends Exception> MiningSpecificationImpl(String chainId, HashingAlgorithm hashingForDeadlines, SignatureAlgorithm signatureForBlocks, SignatureAlgorithm signatureForDeadlines, PublicKey publicKeyForSigningBlocks, ExceptionSupplier<? extends E> onIllegalArgs) throws E, InvalidKeyException {
		this.chainId = Objects.requireNonNull(chainId, "chainId cannot be null", onIllegalArgs);
		this.hashingForDeadlines = Objects.requireNonNull(hashingForDeadlines, "hashingForDeadlines cannot be null", onIllegalArgs);
		this.signatureForBlocks = Objects.requireNonNull(signatureForBlocks, "signatureForBlocks cannot be null", onIllegalArgs);
		this.signatureForDeadlines = Objects.requireNonNull(signatureForDeadlines, "signatureForDeadlines cannot be null", onIllegalArgs);
		this.publicKeyForSigningBlocks = Objects.requireNonNull(publicKeyForSigningBlocks, "publicKeyForSigningBlocks cannot be null", onIllegalArgs);
		this.publicKeyForSigningBlocksBase58 = Base58.toBase58String(signatureForBlocks.encodingOf(publicKeyForSigningBlocks));
	}

	/**
	 * Builds a mining specification for a miner.
	 * 
	 * @param chainId the chain id of the deadlines expected by the miner
	 * @param hashingForDeadlines the hashing algorithm used for computing the deadlines expected by a miner
	 *                            having this specification
	 * @param signatureForBlocks the signature algorithm used for the key identifying the node
	 *                           in the deadlines expected by a miner having this specification
	 * @param signatureForDeadlines the signature algorithm used for the key identifying the plot
	 *                              containing the deadlines expected by a miner having this specification
	 * @param publicKeyOfNodeBase58 the Base58-encoded public key identifying the node in the deadlines
	 *                              expected by a miner having this specification. This is a public key for the
	 *                              {@code signatureForBlocks} algorithm
	 * @param onIllegalArgs the generator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> MiningSpecificationImpl(String chainId, HashingAlgorithm hashingForDeadlines, SignatureAlgorithm signatureForBlocks, SignatureAlgorithm signatureForDeadlines, String publicKeyOfNodeBase58, ExceptionSupplier<? extends E> onIllegalArgs) throws E {
		this.chainId = Objects.requireNonNull(chainId, "chainId cannot be null", onIllegalArgs);
		this.hashingForDeadlines = Objects.requireNonNull(hashingForDeadlines, "hashingForDeadlines cannot be null", onIllegalArgs);
		this.signatureForBlocks = Objects.requireNonNull(signatureForBlocks, "signatureForBlocks cannot be null", onIllegalArgs);
		this.signatureForDeadlines = Objects.requireNonNull(signatureForDeadlines, "signatureForDeadlines cannot be null", onIllegalArgs);
		this.publicKeyForSigningBlocksBase58 = Objects.requireNonNull(publicKeyOfNodeBase58, "publicKeyOfNodeBase58 cannot be null", onIllegalArgs);

		try {
			this.publicKeyForSigningBlocks = signatureForBlocks.publicKeyFromEncoding(Base58.fromBase58String(publicKeyOfNodeBase58, onIllegalArgs));
		}
		catch (InvalidKeySpecException e) {
			throw onIllegalArgs.apply(e.getMessage());
		}
	}

	@Override
	public String getChainId() {
		return chainId;
	}

	@Override
	public HashingAlgorithm getHashingForDeadlines() {
		return hashingForDeadlines;
	}

	@Override
	public SignatureAlgorithm getSignatureForBlocks() {
		return signatureForBlocks;
	}

	@Override
	public SignatureAlgorithm getSignatureForDeadlines() {
		return signatureForDeadlines;
	}

	@Override
	public PublicKey getPublicKeyForSigningBlocks() {
		return publicKeyForSigningBlocks;
	}

	@Override
	public String getPublicKeyForSigningBlocksBase58() {
		return publicKeyForSigningBlocksBase58;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof MiningSpecification ms
			&& chainId.equals(ms.getChainId())
			&& hashingForDeadlines.equals(ms.getHashingForDeadlines())
			&& signatureForBlocks.equals(ms.getSignatureForBlocks())
			&& signatureForDeadlines.equals(ms.getSignatureForDeadlines())
			&& publicKeyForSigningBlocksBase58.equals(ms.getPublicKeyForSigningBlocksBase58());
	}

	@Override
	public int hashCode() {
		return chainId.hashCode() ^ hashingForDeadlines.hashCode() ^ signatureForBlocks.hashCode() ^ signatureForDeadlines.hashCode();
	}
}
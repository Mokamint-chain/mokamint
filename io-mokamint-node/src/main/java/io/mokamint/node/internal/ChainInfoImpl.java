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

import java.util.Arrays;
import java.util.Optional;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.internal.gson.ChainInfoJson;

/**
 * Implementation of the chain information of a Mokamint node.
 */
@Immutable
public class ChainInfoImpl implements ChainInfo {

	/**
	 * The length of the chain.
	 */
	private final long length;

	/**
	 * The hash of the genesis block, if any.
	 */
	private final Optional<byte[]> genesisHash;

	/**
	 * The hash of the head block, if any.
	 */
	private final Optional<byte[]> headHash;

	/**
	 * The state identifier of the head block, if any.
	 */
	private final Optional<byte[]> headStateId;

	/**
	 * Constructs a new chain information object.
	 * 
	 * @param length the length of the chain
	 * @param genesisHash the hash of the genesis block, if any
	 * @param headHash the hash of the head block, if any
	 * @param headStateId the state identifier of the head block, if any
	 */
	public ChainInfoImpl(long length, Optional<byte[]> genesisHash, Optional<byte[]> headHash, Optional<byte[]> headStateId) {
		if (length < 0)
			throw new IllegalArgumentException("length cannot be negative");
		else if (length == 0) {
			if (genesisHash.isPresent() || headHash.isPresent())
				throw new IllegalArgumentException("An empty chain cannot have a genesis nor a head block");
		}
		else if (genesisHash.isEmpty() || headHash.isEmpty())
			throw new IllegalArgumentException("A non-empty chain must have both a genesis and a head block");

		this.length = length;
		this.genesisHash = genesisHash.map(byte[]::clone);
		this.headHash = headHash.map(byte[]::clone);
		this.headStateId = headStateId.map(byte[]::clone);
	}

	/**
	 * Creates a chain information object from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public ChainInfoImpl(ChainInfoJson json) throws InconsistentJsonException {
		long length = json.getLength();
		String genesisHash = json.getGenesisHash();
		String headHash = json.getHeadHash();
		String headStateId = json.getHeadStateId();

		if (length < 0)
			throw new InconsistentJsonException("length cannot be negative");
		else if (length == 0) {
			if (genesisHash != null || headHash != null)
				throw new InconsistentJsonException("An empty chain cannot have a genesis nor a head block");
		}
		else if (genesisHash == null || headHash == null)
			throw new InconsistentJsonException("A non-empty chain must have both a genesis and a head block");

		this.length = length;

		try {
			this.genesisHash = genesisHash == null ? Optional.empty() : Optional.of(Hex.fromHexString(genesisHash));
			this.headHash = headHash == null ? Optional.empty() : Optional.of(Hex.fromHexString(headHash));
			this.headStateId = headStateId == null ? Optional.empty() : Optional.of(Hex.fromHexString(headStateId));
		}
		catch (HexConversionException e) {
			throw new InconsistentJsonException(e);
		}
	}

	@Override
	public long getLength() {
		return length;
	}

	@Override
	public Optional<byte[]> getGenesisHash() {
		return genesisHash.map(byte[]::clone);
	}

	@Override
	public Optional<byte[]> getHeadHash() {
		return headHash.map(byte[]::clone);
	}

	@Override
	public Optional<byte[]> getHeadStateId() {
		return headStateId.map(byte[]::clone);
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof ChainInfoImpl cii) // optimization
			return length == cii.length &&
				same(genesisHash, cii.genesisHash) &&
				same(headHash, cii.headHash) &&
				same(headStateId, cii.headStateId);
		else
			return other instanceof ChainInfo ci &&
				length == ci.getLength() &&
				same(genesisHash, ci.getGenesisHash()) &&
				same(headHash, ci.getHeadHash()) &&
				same(headStateId, ci.getHeadStateId());
	}

	@Override
	public int hashCode() {
		return headHash.or(() -> genesisHash).map(Arrays::hashCode).orElse(0);
	}

	@Override
	public String toString() {
		var builder = new StringBuilder();
		builder.append("* height: " + length + "\n");
		builder.append("* hash of the genesis block: " + toString(genesisHash) + "\n");
		builder.append("* hash of the head block: " + toString(headHash) + "\n");
		builder.append("* state id of the head block: " + toString(headStateId));
	
		return builder.toString();
	}

	private static boolean same(Optional<byte[]> hash1, Optional<byte[]> hash2) {
		return hash1.isEmpty() == hash2.isEmpty() && (hash1.isEmpty() || Arrays.equals(hash1.get(), hash2.get()));
	}

	private static String toString(Optional<byte[]> hash) {
		return hash.isEmpty() ? "--" : Hex.toHexString(hash.get());
	}
}
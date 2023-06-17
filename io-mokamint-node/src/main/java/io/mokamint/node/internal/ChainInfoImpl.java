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
import io.mokamint.node.api.ChainInfo;

/**
 * Implementation of the chain information of a Mokamint node.
 */
@Immutable
public class ChainInfoImpl implements ChainInfo {

	/**
	 * The height of the chain.
	 */
	private final long height;

	/**
	 * The hash of the genesis block, if any.
	 */
	private final Optional<byte[]> genesisHash;

	/**
	 * The hash of the head block, if any.
	 */
	private final Optional<byte[]> headHash;

	/**
	 * Constructs a new chain information object.
	 * 
	 * @param height the height of the chain
	 * @param genesisHash the hash of the genesis block, if any
	 * @param the hash of the head block, if any
	 */
	public ChainInfoImpl(long height, Optional<byte[]> genesisHash, Optional<byte[]> headHash) {
		this.height = height;
		this.genesisHash = genesisHash.map(byte[]::clone);
		this.headHash = headHash.map(byte[]::clone);
	}

	@Override
	public long getHeight() {
		return height;
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
	public boolean equals(Object other) {
		if (other instanceof ChainInfo) {
			var otherChainInfo = (ChainInfo) other;
			return height == otherChainInfo.getHeight() &&
				same(genesisHash, otherChainInfo.getGenesisHash()) &&
				same(headHash, otherChainInfo.getHeadHash());
		}
		else
			return false;
	}

	@Override
	public String toString() {
		var builder = new StringBuilder();
		builder.append("* height: " + height);
		builder.append("* hash of the head block: " + toString(headHash));
		builder.append("* hash of the genesis block: " + toString(genesisHash));
	
		return builder.toString();
	}

	private static boolean same(Optional<byte[]> hash1, Optional<byte[]> hash2) {
		return hash1.isEmpty() == hash2.isEmpty() &&
			(hash1.isEmpty() || Arrays.equals(hash1.get(), hash2.get()));
	}

	private static String toString(Optional<byte[]> hash) {
		return hash.isEmpty() ? "--" : Hex.toHexString(hash.get());
	}
}
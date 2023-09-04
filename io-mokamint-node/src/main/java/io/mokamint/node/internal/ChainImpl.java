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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Hex;
import io.mokamint.node.api.Chain;

/**
 * Implementation of information about the hashes of a sequential portion of the
 * current best chain of a Mokamint node.
 */
@Immutable
public class ChainImpl implements Chain {

	/**
	 * The hashes in the sequence.
	 */
	private final byte[][] hashes;

	/**
	 * Constructs an object containing the hashes of a sequential
	 * portion of the current best chain of a Mokamint node.
	 * 
	 * @param hashes the hashes
	 */
	public ChainImpl(Stream<byte[]> hashes) {
		this.hashes = hashes.map(byte[]::clone).toArray(byte[][]::new);
	}

	@Override
	public Stream<byte[]> getHashes() {
		return Stream.of(hashes).map(byte[]::clone);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Chain otherChain &&
			Arrays.deepEquals(hashes, otherChain.getHashes().toArray(byte[][]::new));
	}

	@Override
	public String toString() {
		return Stream.of(hashes).map(Hex::toHexString).collect(Collectors.joining("\n"));
	}
}
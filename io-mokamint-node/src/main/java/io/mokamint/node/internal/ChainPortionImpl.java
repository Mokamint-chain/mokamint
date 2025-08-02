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
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.internal.json.ChainPortionJson;

/**
 * Implementation of information about the hashes of a sequential portion of the
 * current best chain of a Mokamint node.
 */
@Immutable
public class ChainPortionImpl implements ChainPortion {

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
	public ChainPortionImpl(Stream<byte[]> hashes) {
		this.hashes = hashes.map(byte[]::clone).toArray(byte[][]::new);
	}

	/**
	 * Creates an object containing the hashes of a sequential
	 * portion of the current best chain of a Mokamint node, from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public ChainPortionImpl(ChainPortionJson json) throws InconsistentJsonException {
		String[] hashes = json.getHashes().toArray(String[]::new);
		this.hashes = new byte[hashes.length][];

		for (int pos = 0; pos < hashes.length; pos++) {
			String hash = hashes[pos];
			if (hash == null)
				throw new InconsistentJsonException("hashes cannot contain a null element");

			try {
				this.hashes[pos] = Hex.fromHexString(hashes[pos]);
			}
			catch (HexConversionException e) {
				throw new InconsistentJsonException(e);
			}
		}
	}

	@Override
	public Stream<byte[]> getHashes() {
		return Stream.of(hashes).map(byte[]::clone);
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof ChainPortionImpl cpi) // optimization
			return Arrays.deepEquals(hashes, cpi.hashes);
		else
			return other instanceof ChainPortion ocp &&
				Arrays.deepEquals(hashes, ocp.getHashes().toArray(byte[][]::new));
	}

	@Override
	public int hashCode() {
		return Arrays.deepHashCode(hashes);
	}

	@Override
	public String toString() {
		return Stream.of(hashes).map(Hex::toHexString).collect(Collectors.joining("\n"));
	}
}
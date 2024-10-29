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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Hex;
import io.mokamint.node.api.ChainPortion;

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
		this(hashes, NullPointerException::new, IllegalArgumentException::new);
	}

	/**
	 * Constructs an object containing the hashes of a sequential
	 * portion of the current best chain of a Mokamint node.
	 * 
	 * @param hashes the hashes
	 * @param onNull the generator of the exception to throw if some argument is {@code null}
	 * @param onIllegal the generator of the exception to throw if some argument has an illegal value
	 * @throws ON_NULL if some argument is {@code null}
	 * @throws ON_ILLEGAL if some argument has an illegal value
	 */
	public <ON_NULL extends Exception, ON_ILLEGAL extends Exception> ChainPortionImpl(Stream<byte[]> hashes, Function<String, ON_NULL> onNull, Function<String, ON_ILLEGAL> onIllegal) throws ON_NULL, ON_ILLEGAL {
		try {
			this.hashes = hashes.map(byte[]::clone).toArray(byte[][]::new);
		}
		catch (NullPointerException e) {
			throw onNull.apply(e.getMessage());
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
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
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.MempoolPortion;

/**
 * Implementation of information about the transactions of a sorted, sequential portion of the
 * mempool of a Mokamint node.
 */
@Immutable
public class MempoolPortionImpl implements MempoolPortion {

	/**
	 * The transaction information objects in the sequence, in increasing order of transaction priority.
	 */
	private final MempoolEntry[] entries;

	/**
	 * Constructs an object containing the entries of a sequential
	 * portion of the mempool of a Mokamint node.
	 * 
	 * @param entries the mempool entries, in increasing order of transaction priority
	 */
	public MempoolPortionImpl(Stream<MempoolEntry> entries) {
		this(entries, NullPointerException::new, IllegalArgumentException::new);
	}

	/**
	 * Constructs an object containing the entries of a sequential
	 * portion of the mempool of a Mokamint node.
	 * 
	 * @param entries the mempool entries, in increasing order of transaction priority
	 * @param onNull the generator of the exception to throw if some argument is {@code null}
	 * @param onIllegal the generator of the exception to throw if some argument has an illegal value
	 * @throws ON_NULL if some argument is {@code null}
	 * @throws ON_ILLEGAL if some argument has an illegal value
	 */
	public <ON_NULL extends Exception, ON_ILLEGAL extends Exception> MempoolPortionImpl(Stream<MempoolEntry> entries, Function<String, ON_NULL> onNull, Function<String, ON_ILLEGAL> onIllegal) throws ON_NULL, ON_ILLEGAL {
		if (entries == null)
			throw onNull.apply("entries cannot be null");

		this.entries = entries.toArray(MempoolEntry[]::new);
		for (var entry: this.entries)
			if (entry == null)
				throw onNull.apply("entries cannot contain a null element");
	}

	@Override
	public Stream<MempoolEntry> getEntries() {
		return Stream.of(entries);
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof MempoolPortionImpl mpi)
			return Arrays.deepEquals(entries, mpi.entries);
		else
			return other instanceof MempoolPortion mpp &&
				Arrays.deepEquals(entries, mpp.getEntries().toArray(MempoolEntry[]::new));
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(entries);
	}

	@Override
	public String toString() {
		return getEntries().map(MempoolEntry::toString).collect(Collectors.joining("\n"));
	}
}
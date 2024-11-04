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

import java.util.function.Function;

import io.hotmoka.annotations.Immutable;
import io.mokamint.node.api.MempoolInfo;

/**
 * An implementation of the information of the mempool of a Mokamint node.
 */
@Immutable
public class MempoolInfoImpl implements MempoolInfo {

	/**
	 * The size of the mempool.
	 */
	private final long size;

	/**
	 * Creates an information object about the mempool of a Mokamint node.
	 * 
	 * @param size the size of the mempool
	 */
	public MempoolInfoImpl(long size) {
		this(size, NullPointerException::new, IllegalArgumentException::new);
	}

	/**
	 * Creates an information object about the mempool of a Mokamint node.
	 * 
	 * @param onNull the generator of the exception to throw if some argument is {@code null}
	 * @param onIllegal the generator of the exception to throw if some argument has an illegal value
	 * @throws ON_NULL if some argument is {@code null}
	 * @throws ON_ILLEGAL if some argument has an illegal value
	 * @param size the size of the mempool
	 */
	public <ON_NULL extends Exception, ON_ILLEGAL extends Exception> MempoolInfoImpl(long size, Function<String, ON_NULL> onNull, Function<String, ON_ILLEGAL> onIllegal) throws ON_NULL, ON_ILLEGAL {
		if (size < 0)
			throw onIllegal.apply("size cannot be negative");

		this.size = size;
	}

	@Override
	public long getSize() {
		return size;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof MempoolInfo mi && size == mi.getSize();
	}

	@Override
	public int hashCode() {
		return Long.hashCode(size);
	}

	@Override
	public String toString() {
		return "size = " + size;
	}
}
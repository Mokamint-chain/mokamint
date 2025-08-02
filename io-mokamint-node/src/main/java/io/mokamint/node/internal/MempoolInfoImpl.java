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

import io.hotmoka.annotations.Immutable;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.internal.json.MempoolInfoJson;

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
		if (size < 0)
			throw new IllegalArgumentException("size cannot be negative");

		this.size = size;
	}

	/**
	 * Creates a mempool info from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public MempoolInfoImpl(MempoolInfoJson json) throws InconsistentJsonException {
		long size = json.getSize();
		if (size < 0)
			throw new InconsistentJsonException("size cannot be negative");

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
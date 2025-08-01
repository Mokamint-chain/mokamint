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
import java.util.Objects;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.internal.json.MempoolEntryJson;

/**
 * An implementation of an entry of the mempool of a Mokamint node.
 * It refers to a transaction that is going to be executed and added
 * to a block, eventually.
 */
@Immutable
public class MempoolEntryImpl implements MempoolEntry {

	/**
	 * The hash of the transaction in the entry.
	 */
	private final byte[] hash;

	/**
	 * The priority of the transaction in the entry.
	 */
	private final long priority;

	/**
	 * Creates an entry of the mempool of a Mokamint node.
	 * 
	 * @param hash the hash of the transaction in the entry
	 * @param priority the priority of the transaction in the entry
	 */
	public MempoolEntryImpl(byte[] hash, long priority) {
		this.hash = Objects.requireNonNull(hash).clone();
		this.priority = priority;
	}

	/**
	 * Creates an entry of the mempool of a Mokamint node, from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public MempoolEntryImpl(MempoolEntryJson json) throws InconsistentJsonException {
		String hash = json.getHash();
		if (hash == null)
			throw new InconsistentJsonException("hash cannot be null");

		try {
			this.hash = Hex.fromHexString(hash);
		}
		catch (HexConversionException e) {
			throw new InconsistentJsonException(e);
		}

		this.priority = json.getPriority();
	}

	@Override
	public byte[] getHash() {
		return hash.clone();
	}

	@Override
	public long getPriority() {
		return priority;
	}

	@Override
	public boolean equals(Object other) {
		// the priority is implied by the transaction
		if (other instanceof MempoolEntryImpl mei) // optimization
			return Arrays.equals(hash, mei.hash);
		else
			return other instanceof MempoolEntry ti && Arrays.equals(hash, ti.getHash());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(hash);
	}

	@Override
	public String toString() {
		return Hex.toHexString(hash) + " with priority " + priority;
	}
}
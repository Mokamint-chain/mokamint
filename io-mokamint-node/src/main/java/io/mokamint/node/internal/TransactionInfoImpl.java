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

import java.io.IOException;
import java.util.Arrays;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Hex;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.TransactionInfo;

/**
 * An implementation of a transaction information object.
 */
@Immutable
public class TransactionInfoImpl extends AbstractMarshallable implements TransactionInfo {

	/**
	 * The hash of the transaction.
	 */
	private final byte[] hash;

	/**
	 * The priority of the transaction.
	 */
	private final long priority;

	/**
	 * Creates a transaction information object.
	 * 
	 * @param hash the hash of the transaction
	 * @param priority the priority of the transaction
	 */
	public TransactionInfoImpl(byte[] hash, long priority) {
		this.hash = hash.clone();
		this.priority = priority;
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
		return other instanceof TransactionInfo ti && Arrays.equals(hash, ti.getHash());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(hash);
	}

	@Override
	public String toString() {
		return Hex.toHexString(hash) + " with priority " + priority;
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		context.writeLengthAndBytes(hash);
		context.writeLong(priority);
	}

	/**
	 * Unmarshals a transaction information object from the given context.
	 * 
	 * @param context the context
	 * @return the transaction information object
	 * @throws IOException if the transaction information object cannot be unmarshalled
	 */
	public static TransactionInfoImpl from(UnmarshallingContext context) throws IOException {
		return new TransactionInfoImpl(
			context.readLengthAndBytes("Transaction information hash length mismatch"),
			context.readLong()
		);
	}
}
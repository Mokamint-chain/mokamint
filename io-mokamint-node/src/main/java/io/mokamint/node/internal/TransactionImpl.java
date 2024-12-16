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
import java.util.Objects;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Base64;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Transaction;

/**
 * An implementation of a transaction.
 */
@Immutable
public class TransactionImpl extends AbstractMarshallable implements Transaction {

	/**
	 * The bytes of the transaction.
	 */
	private final byte[] bytes;

	/**
	 * Creates a transaction with the given bytes.
	 * 
	 * @param bytes the bytes
	 */
	public TransactionImpl(byte[] bytes) {
		this.bytes = Objects.requireNonNull(bytes).clone();
	}

	/**
	 * Creates a new transaction, unmarshalled from the given context.
	 * 
	 * @param context the context
	 * @throws IOException if the transaction cannot be unmarshalled
	 */
	public TransactionImpl(UnmarshallingContext context) throws IOException {
		this(context.readLengthAndBytes("Transaction length mismatch"));
	}

	@Override
	public byte[] getBytes() {
		return bytes.clone();
	}

	@Override
	public String toBase64String() {
		return Base64.toBase64String(bytes);
	}

	@Override
	public String getHexHash(Hasher<Transaction> hasher) {
		return Hex.toHexString(hasher.hash(this));
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof TransactionImpl ti)
			return Arrays.equals(bytes, ti.bytes); // optimization, to avoid cloning
		else
			return other instanceof Transaction t && Arrays.equals(bytes, t.getBytes());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(bytes);
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		context.writeLengthAndBytes(bytes);
	}

	@Override
	public String toString() {
		return toBase64String() + " (base64)";
	}

	@Override
	public int compareTo(Transaction other) {
		if (other instanceof TransactionImpl ti)
			return Arrays.compare(bytes, ti.bytes); // optimization, to avoid cloning
		else
			return Arrays.compare(bytes, other.getBytes());
	}
}
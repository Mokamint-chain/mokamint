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
import io.hotmoka.crypto.Base64;
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
		this.bytes = bytes.clone();
	}

	@Override
	public byte[] getBytes() {
		return bytes.clone();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Transaction t && Arrays.equals(bytes, t.getBytes());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(bytes);
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		context.writeCompactInt(bytes.length);
		context.write(bytes);
	}

	@Override
	public String toString() {
		return Base64.toBase64String(bytes);
	}

	/**
	 * Unmarshals a transaction from the given context.
	 * 
	 * @param context the context
	 * @return the transaction
	 * @throws IOException if the transaction cannot be unmarshalled
	 */
	public static TransactionImpl from(UnmarshallingContext context) throws IOException {
		int length = context.readCompactInt();
		return new TransactionImpl(context.readBytes(length, "Transaction length mismatch"));
	}
}
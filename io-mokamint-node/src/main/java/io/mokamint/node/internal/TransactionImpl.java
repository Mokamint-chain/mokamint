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
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Base64;
import io.hotmoka.crypto.Base64ConversionException;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.internal.gson.TransactionJson;

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
	 * The hashing algorithm used for hashing this transaction.
	 */
	private final HashingAlgorithm hashing;

	/**
	 * Creates a transaction with the given bytes.
	 * 
	 * @param bytes the bytes
	 * @param hashing the hashing algorithm to use for the transaction
	 */
	public TransactionImpl(byte[] bytes, HashingAlgorithm hashing) {
		this.bytes = Objects.requireNonNull(bytes).clone();
		this.hashing = Objects.requireNonNull(hashing);
	}

	/**
	 * Creates a transaction from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 * @throws NoSuchAlgorithmException if the JSON refers to an unknown cryptographic algorithm
	 */
	public TransactionImpl(TransactionJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
		String bytes = json.getBytes();
		if (bytes == null)
			throw new InconsistentJsonException("bytes cannot be null");

		try {
			this.bytes = Base64.fromBase64String(bytes);
		}
		catch (Base64ConversionException e) {
			throw new InconsistentJsonException(e);
		}

		String hashing = json.getHashing();
		if (hashing == null)
			throw new InconsistentJsonException("hashing cannot be null");

		this.hashing = HashingAlgorithms.of(hashing);
	}

	@Override
	public byte[] getBytes() {
		return bytes.clone();
	}

	@Override
	public HashingAlgorithm getHashing() {
		return hashing;
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
		context.writeStringShared(hashing.getName());
	}

	@Override
	public String toString() {
		return Base64.toBase64String(bytes) + " (base64)";
	}

	/**
	 * Unmarshals a transaction from the given context.
	 * 
	 * @param context the context
	 * @return the transaction
	 * @throws IOException if the transaction cannot be unmarshalled
	 * @throws NoSuchAlgorithmException if some cryptographic algorithm is not available
	 */
	public static TransactionImpl from(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		return new TransactionImpl(context.readLengthAndBytes("Transaction length mismatch"), HashingAlgorithms.of(context.readStringShared()));
	}

	/**
	 * Unmarshals a transaction from the given context.
	 * 
	 * @param context the context
	 * @return the transaction
	 * @throws IOException if the transaction cannot be unmarshalled
	 */
	public static TransactionImpl from(UnmarshallingContext context, HashingAlgorithm hashingForTransactions) throws IOException {
		return new TransactionImpl(context.readLengthAndBytes("Transaction length mismatch"), hashingForTransactions);
	}

	@Override
	public int compareTo(Transaction other) {
		if (other instanceof TransactionImpl ti)
			return Arrays.compare(bytes, ti.bytes); // optimization, to avoid cloning
		else
			return Arrays.compare(bytes, other.getBytes());
	}
}
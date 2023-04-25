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

package io.mokamint.nonce.internal;

import java.util.Arrays;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.exceptions.UncheckedNoSuchAlgorithmException;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.nonce.Nonces;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.Nonce;

/**
 * Implementation of a deadline inside a plot file. It is a reference to a nonce
 * and a value computed for that nonce. Deadlines are ordered
 * by the lexicographical ordering of their values.
 */
public class DeadlineImpl extends AbstractMarshallable implements Deadline {
	private final byte[] prolog;
	private final long progressive;
	private final byte[] value;
	private final int scoopNumber;
	private final byte[] data;
	private final HashingAlgorithm<byte[]> hashing;

	public DeadlineImpl(byte[] prolog, long progressive, byte[] value, int scoopNumber, byte[] data, HashingAlgorithm<byte[]> hashing) {
		this.prolog = prolog;
		this.progressive = progressive;
		this.value = value;
		this.scoopNumber = scoopNumber;
		this.data = data;
		this.hashing = hashing;
	}

	/**
	 * Unmarshals a deadline from the given context.
	 * 
	 * @param context the unmarshalling context
	 */
	public DeadlineImpl(UnmarshallingContext context) {
		int prologLength = context.readCompactInt();
		this.prolog = context.readBytes(prologLength, "mismatch in deadline's prolog length");
		this.progressive = context.readLong();
		int valueLength = context.readCompactInt();
		this.value = context.readBytes(valueLength, "mismatch in deadline's value length");
		this.scoopNumber = context.readInt();
		int dataLength = context.readInt();
		this.data = context.readBytes(dataLength, "mismatch in deadline's data length");
		String hashing = context.readUTF();
		this.hashing = UncheckedNoSuchAlgorithmException.wraps(() -> HashingAlgorithms.mk(hashing, (byte[] bytes) -> bytes));
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Deadline) {
			Deadline otherAsDeadline = (Deadline) other;
			return progressive == otherAsDeadline.getProgressive() &&
				scoopNumber == otherAsDeadline.getScoopNumber() &&
				Arrays.equals(value, otherAsDeadline.getValue()) &&
				Arrays.equals(prolog, otherAsDeadline.getProlog()) &&
				Arrays.equals(data, otherAsDeadline.getData()) &&
				hashing.getName().equals(otherAsDeadline.getHashing().getName());
		}
		else
			return false;
	}

	@Override
	public int hashCode() {
		return ((int) progressive) ^ scoopNumber ^ Arrays.hashCode(value); 
	}

	@Override
	public int compareByValue(Deadline other) {
		byte[] left = value, right = other.getValue();

		for (int i = 0; i < left.length; i++) {
			int a = left[i] & 0xff;
			int b = right[i] & 0xff;
			if (a != b)
				return a - b;
		}

		return 0; // deadlines with the same hashing algorithm have the same length
	}

	@Override
	public byte[] getProlog() {
		return prolog.clone();
	}

	@Override
	public long getProgressive() {
		return progressive;
	}

	@Override
	public byte[] getValue() {
		return value.clone();
	}

	@Override
	public int getScoopNumber() {
		return scoopNumber;
	}

	@Override
	public byte[] getData() {
		return data.clone();
	}

	@Override
	public HashingAlgorithm<byte[]> getHashing() {
		return hashing;
	}

	@Override
	public Nonce toNonce() {
		return Nonces.of(prolog, progressive, hashing);
	}

	@Override
	public boolean isValid() {
		return this.equals(toNonce().getDeadline(scoopNumber, data));
	}

	@Override
	public String toString() {
		return "scoopNumber: " + scoopNumber + ", data: " + Hex.toHexString(data) + ", nonce: " + progressive + ", value: " + Hex.toHexString(value);
	}

	@Override
	public void into(MarshallingContext context) {
		context.writeCompactInt(prolog.length);
		context.write(prolog);
		context.writeLong(progressive);
		context.writeCompactInt(value.length);
		context.write(value);
		context.writeInt(scoopNumber);
		context.writeInt(data.length);
		context.write(data);
		context.writeUTF(hashing.getName());
	}
}
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

import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.nonce.Nonces;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;

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

	public DeadlineImpl(HashingAlgorithm<byte[]> hashing, byte[] prolog, long progressive, byte[] value, int scoopNumber, byte[] data) {
		this.hashing = hashing;
		this.prolog = prolog;
		this.progressive = progressive;
		this.value = value;
		this.scoopNumber = scoopNumber;
		this.data = data;

		verify();
	}

	/**
	 * Unmarshals a deadline from the given context.
	 * 
	 * @param context the unmarshalling context
	 * @throws NoSuchAlgorithmException if the deadline uses an unknown hashing algorithm
	 * @throws IOException if the deadline could not be unmarshalled
	 */
	public DeadlineImpl(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		try {
			this.hashing = HashingAlgorithms.of(context.readStringUnshared(), Function.identity());
			this.prolog = context.readBytes(context.readCompactInt(), "Mismatch in deadline's prolog length");
			this.progressive = context.readLong();
			this.value = context.readBytes(hashing.length(), "Mismatch in deadline's value length");
			this.scoopNumber = context.readInt();
			this.data = context.readBytes(context.readInt(), "Mismatch in deadline's data length");

			verify();
		}
		catch (RuntimeException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Checks all constraints expected from a deadline.
	 * 
	 * @throws NullPointerException if some value is unexpectedly {@code null}
	 * @throws IllegalArgumentException if some value is illegal
	 */
	private void verify() {
		Objects.requireNonNull(prolog, "prolog cannot be null");
		Objects.requireNonNull(value, "value cannot be null");
		Objects.requireNonNull(data, "data cannot be null");
		Objects.requireNonNull(hashing, "hashing cannot be null");
	
		if (prolog.length > MAX_PROLOG_SIZE)
			throw new IllegalArgumentException("prolog too long: maximum is " + MAX_PROLOG_SIZE);
	
		if (progressive < 0L)
			throw new IllegalArgumentException("progressive cannot be negative");
	
		if (scoopNumber < 0 || scoopNumber > MAX_SCOOP_NUMBER)
			throw new IllegalArgumentException("Illegal scoopNumber: it must be between 0 and " + MAX_SCOOP_NUMBER);
	
		if (value.length != hashing.length())
			throw new IllegalArgumentException("Illegal deadline value: expected an array of length " + hashing.length() + " rather than " + value.length);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Deadline otherAsDeadline &&
			progressive == otherAsDeadline.getProgressive() &&
			scoopNumber == otherAsDeadline.getScoopNumber() &&
			Arrays.equals(value, otherAsDeadline.getValue()) &&
			Arrays.equals(prolog, otherAsDeadline.getProlog()) &&
			Arrays.equals(data, otherAsDeadline.getData()) &&
			hashing.getName().equals(otherAsDeadline.getHashing().getName());
	}

	@Override
	public int hashCode() {
		return scoopNumber ^ Arrays.hashCode(data) ^ hashing.getName().hashCode();
	}

	@Override
	public long getMillisecondsToWaitFor(BigInteger acceleration) {
		byte[] valueAsBytes = getValue();
		var value = new BigInteger(1, valueAsBytes);
		value = value.divide(acceleration);
		byte[] newValueAsBytes = value.toByteArray();
		// we recreate an array of the same length as at the beginning
		var dividedValueAsBytes = new byte[valueAsBytes.length];
		System.arraycopy(newValueAsBytes, 0, dividedValueAsBytes,
			dividedValueAsBytes.length - newValueAsBytes.length,
			newValueAsBytes.length);
		// we take the first 8 bytes of the divided value
		var firstEightBytes = new byte[] {
			dividedValueAsBytes[0], dividedValueAsBytes[1], dividedValueAsBytes[2], dividedValueAsBytes[3],
			dividedValueAsBytes[4], dividedValueAsBytes[5], dividedValueAsBytes[6], dividedValueAsBytes[7]
		};
		// TODO: theoretically, there might be an overflow when converting into long
		return new BigInteger(1, firstEightBytes).longValue();
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

		return 0; // deadlines with the same hashingName algorithm have the same length
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
	public boolean matches(DeadlineDescription description) {
		return description.equals(this);
	}

	@Override
	public boolean isValid() {
		return equals(Nonces.from(this).getDeadline(this));
	}

	@Override
	public String toString() {
		return "scoopNumber: " + scoopNumber + ", data: " + Hex.toHexString(data) + ", nonce: " + progressive + ", value: " + Hex.toHexString(value);
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		context.writeStringUnshared(hashing.getName());
		context.writeCompactInt(prolog.length);
		context.write(prolog);
		context.writeLong(progressive);
		// we do not write value.length, since it coincides with hashing.length()
		context.write(value);
		context.writeInt(scoopNumber);
		context.writeInt(data.length);
		context.write(data);
	}
}
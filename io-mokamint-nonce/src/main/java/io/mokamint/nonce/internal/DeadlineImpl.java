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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import io.mokamint.nonce.api.Prolog;

/**
 * Implementation of a deadline inside a plot file. It is a reference to a nonce
 * and a value computed for that nonce. Deadlines are ordered
 * by the lexicographical ordering of their values.
 */
@Immutable
public class DeadlineImpl extends AbstractMarshallable implements Deadline {
	private final Prolog prolog;
	private final long progressive;
	private final byte[] value;
	private final int scoopNumber;
	private final byte[] data;
	private final HashingAlgorithm hashing;
	private final byte[] signature;

	private final static BigInteger MAX_LONG_VALUE = BigInteger.valueOf(Long.MAX_VALUE);

	/**
	 * Yields a deadline.
	 * 
	 * @param prolog the prolog of the nonce of the deadline
	 * @param progressive the progressive number of the nonce of the deadline
	 * @param value the value of the deadline
	 * @param scoopNumber the number of the scoop of the nonce used to compute the deadline
	 * @param data the data used to compute the deadline
	 * @param hashing the hashing algorithm used to compute the deadline and the nonce
	 * @param privateKey the private key that will be used to sign the deadline; it must match the
	 *                   public key contained in the prolog
	 * @return the deadline
	 * @throws SignatureException if the signature of the deadline failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public DeadlineImpl(HashingAlgorithm hashing, Prolog prolog, long progressive, byte[] value, int scoopNumber, byte[] data, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		Objects.requireNonNull(prolog, "prolog cannot be null");
		Objects.requireNonNull(privateKey, "privateKey cannot be null");

		this.hashing = hashing;
		this.prolog = prolog;
		this.progressive = progressive;
		this.value = value;
		this.scoopNumber = scoopNumber;
		this.data = data;
		this.signature = prolog.getSignatureForDeadlines().getSigner(privateKey, DeadlineImpl::toByteArrayWithoutSignature).sign(this);

		verifyWithoutSignature();
	}

	/**
	 * Yields a deadline.
	 * 
	 * @param prolog the prolog of the nonce of the deadline
	 * @param progressive the progressive number of the nonce of the deadline
	 * @param value the value of the deadline
	 * @param scoopNumber the number of the scoop of the nonce used to compute the deadline
	 * @param data the data used to compute the deadline
	 * @param hashing the hashing algorithm used to compute the deadline and the nonce
	 * @param privateKey the private key that will be used to sign the deadline; it must match the
	 *                   public key contained in the prolog
	 * @return the deadline
	 * @throws IllegalArgumentException if some argument is illegal
	 */
	public DeadlineImpl(HashingAlgorithm hashing, Prolog prolog, long progressive, byte[] value, int scoopNumber, byte[] data, byte[] signature) throws IllegalArgumentException {
		this.hashing = hashing;
		this.prolog = prolog;
		this.progressive = progressive;
		this.value = value;
		this.scoopNumber = scoopNumber;
		this.data = data;
		this.signature = signature.clone();

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
			this.hashing = HashingAlgorithms.of(context.readStringUnshared());
			this.prolog = Prologs.from(context);
			this.progressive = context.readLong();
			this.value = context.readBytes(hashing.length(), "Mismatch in deadline's value length");
			this.scoopNumber = context.readInt();
			this.data = context.readBytes(context.readCompactInt(), "Mismatch in deadline's data length");
			this.signature = context.readBytes(context.readCompactInt(), "Mismatch in deadline's signature length");

			verify();
		}
		catch (RuntimeException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Checks all constraints expected from a deadline, trusting the
	 * signature to be correct (hence it is not verified).
	 * 
	 * @throws NullPointerException if some value is unexpectedly {@code null}
	 * @throws IllegalArgumentException if some value is illegal
	 */
	private void verifyWithoutSignature() {
		Objects.requireNonNull(prolog, "prolog cannot be null");
		Objects.requireNonNull(value, "value cannot be null");
		Objects.requireNonNull(data, "data cannot be null");
		Objects.requireNonNull(hashing, "hashing cannot be null");
	
		if (progressive < 0L)
			throw new IllegalArgumentException("progressive cannot be negative");
	
		if (scoopNumber < 0 || scoopNumber > MAX_SCOOP_NUMBER)
			throw new IllegalArgumentException("Illegal scoopNumber: it must be between 0 and " + MAX_SCOOP_NUMBER);
	
		if (value.length != hashing.length())
			throw new IllegalArgumentException("Illegal deadline value: expected an array of length " + hashing.length() + " rather than " + value.length);
	}

	/**
	 * Checks all constraints expected from a deadline (including the validity of the signature).
	 * 
	 * @throws NullPointerException if some value is unexpectedly {@code null}
	 * @throws IllegalArgumentException if some value is illegal
	 */
	private void verify() {
		verifyWithoutSignature();
		Objects.requireNonNull(signature, "signature cannot be null");
	
		try {
			if (!prolog.getSignatureForDeadlines().getVerifier(prolog.getPublicKeyForSigningDeadlines(), DeadlineImpl::toByteArrayWithoutSignature).verify(this, signature))
				throw new IllegalArgumentException("The deadline's signature is invalid");
		}
		catch (SignatureException e) {
			throw new IllegalArgumentException("The deadline's signature cannot be verified", e);
		}
		catch (InvalidKeyException e) {
			throw new IllegalArgumentException("The public key in the prolog of the deadline is invalid", e);
		}
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Deadline otherAsDeadline &&
			progressive == otherAsDeadline.getProgressive() &&
			scoopNumber == otherAsDeadline.getScoopNumber() &&
			Arrays.equals(value, otherAsDeadline.getValue()) &&
			prolog.equals(otherAsDeadline.getProlog()) &&
			Arrays.equals(data, otherAsDeadline.getData()) &&
			hashing.equals(otherAsDeadline.getHashing()) &&
			Arrays.equals(signature, otherAsDeadline.getSignature());
	}

	@Override
	public int hashCode() {
		return scoopNumber ^ Arrays.hashCode(data);
	}

	@Override
	public long getMillisecondsToWaitFor(BigInteger acceleration) {
		byte[] valueAsBytes = getValue();
		var newValueAsBytes = new BigInteger(1, valueAsBytes).divide(acceleration).toByteArray();
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
		var result = new BigInteger(1, firstEightBytes);
		if (result.subtract(MAX_LONG_VALUE).signum() == 1)
			// theoretically, there might be an overflow when converting into long,
			// but this would mean that the waiting time is larger than the life of the universe...
			throw new ArithmeticException("Overflow in the waiting time!");

		return result.longValue();
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
	public Prolog getProlog() {
		return prolog;
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
	public HashingAlgorithm getHashing() {
		return hashing;
	}

	@Override
	public byte[] getSignature() {
		return signature.clone();
	}

	@Override
	public <E extends Exception> void matchesOrThrow(DeadlineDescription description, Function<String, E> exceptionSupplier) throws E {
		if (scoopNumber != description.getScoopNumber())
			throw exceptionSupplier.apply("Scoop number mismatch (expected " + description.getScoopNumber() + " but found " + scoopNumber + ")");

		if (!Arrays.equals(data, description.getData()))
			throw exceptionSupplier.apply("Data mismatch");

		if (!hashing.equals(description.getHashing()))
			throw exceptionSupplier.apply("Hashing algorithm mismatch");
	}

	@Override
	public boolean isValid() {
		return Arrays.equals(value, new NonceImpl(prolog, progressive, hashing).getValueFor(this));
	}

	@Override
	public String toString() {
		return "prolog: { " + prolog + " }, scoopNumber: " + scoopNumber + ", data: " + Hex.toHexString(data)
			+ ", nonce: " + progressive + ", value: " + Hex.toHexString(value) + ", hashing: " + hashing
			+ ", signature: " + Hex.toHexString(signature);
	}

	@Override
	public String toStringSanitized() {
		var trimmedData = new byte[Math.min(256, data.length)];
		System.arraycopy(data, 0, trimmedData, 0, trimmedData.length);
		var trimmedSignature = new byte[Math.min(256, signature.length)];
		System.arraycopy(signature, 0, trimmedSignature, 0, trimmedSignature.length);
		// no risk with the value, since its length is bound to the length of the hashing algorithm
		return "prolog: { " + prolog.toStringSanitized() + " }, scoopNumber: " + scoopNumber + ", data: " + Hex.toHexString(trimmedData)
			+ ", nonce: " + progressive + ", value: " + Hex.toHexString(value) + ", hashing: " + hashing
			+ ", signature: " + Hex.toHexString(trimmedSignature);
	}

	/**
	 * Yields a marshalling of this object into a byte array, without considering
	 * its signature.
	 * 
	 * @return the marshalled bytes
	 */
	private byte[] toByteArrayWithoutSignature() {
		try (var baos = new ByteArrayOutputStream(); var context = createMarshallingContext(baos)) {
			intoWithoutSignature(context);
			context.flush();
			return baos.toByteArray();
		}
		catch (IOException e) {
			// impossible with a ByteArrayOutputStream
			throw new RuntimeException("Unexpected exception", e);
		}
	}

	/**
	 * Marshals this deadline into the given context, without its signature.
	 * 
	 * @param context the context
	 * @throws IOException if marshalling fails
	 */
	private void intoWithoutSignature(MarshallingContext context) throws IOException {
		context.writeStringUnshared(hashing.getName());
		prolog.into(context);
		context.writeLong(progressive);
		// we do not write value.length, since it coincides with hashing.length()
		context.write(value);
		context.writeInt(scoopNumber);
		context.writeCompactInt(data.length);
		context.write(data);
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		intoWithoutSignature(context);
		context.writeCompactInt(signature.length);
		context.write(signature);
	}
}
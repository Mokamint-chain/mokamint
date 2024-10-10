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
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.Challenge;
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
	private final Challenge challenge;
	private final byte[] signature;  // TODO: do we really need to sign deadlines?

	private final static BigInteger MAX_LONG_VALUE = BigInteger.valueOf(Long.MAX_VALUE);

	/**
	 * Yields a deadline.
	 * 
	 * @param prolog the prolog of the nonce of the deadline
	 * @param progressive the progressive number of the nonce of the deadline
	 * @param value the value of the deadline
	 * @param challenge the challenge the deadline responds to
	 * @param privateKey the private key that will be used to sign the deadline; it must match the
	 *                   public key contained in the prolog
	 * @return the deadline
	 * @throws SignatureException if the signature of the deadline failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public DeadlineImpl(Prolog prolog, long progressive, byte[] value, Challenge challenge, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		Objects.requireNonNull(prolog, "prolog cannot be null");
		Objects.requireNonNull(privateKey, "privateKey cannot be null");

		this.prolog = prolog;
		this.progressive = progressive;
		this.value = value;
		this.challenge = challenge;
		this.signature = prolog.getSignatureForDeadlines().getSigner(privateKey, DeadlineImpl::toByteArrayWithoutSignature).sign(this);

		verify();
	}

	/**
	 * Yields a deadline.
	 * 
	 * @param prolog the prolog of the nonce of the deadline
	 * @param progressive the progressive number of the nonce of the deadline
	 * @param value the value of the deadline
	 * @param challenge the challenge the deadline responds to
	 * @param signature the signature of the deadline with the private key corresponding to the public key contained in the prolog
	 * @return the deadline
	 * @throws IllegalArgumentException if some argument is illegal
	 */
	public DeadlineImpl(Prolog prolog, long progressive, byte[] value, Challenge challenge, byte[] signature) throws IllegalArgumentException {
		this.prolog = prolog;
		this.progressive = progressive;
		this.value = value;
		this.challenge = challenge;
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
		// TODO: possibly make challenges marshallable
		var hashing = HashingAlgorithms.of(context.readStringUnshared());
		this.prolog = Prologs.from(context);
		this.progressive = context.readLong();
		this.value = context.readBytes(hashing.length(), "Mismatch in deadline's value length");
		var scoopNumber = context.readInt();
		var generationSignature = context.readBytes(context.readCompactInt(), "Mismatch in deadline's generation signature length");
		this.challenge = Challenges.of(scoopNumber, generationSignature, hashing);
		this.signature = context.readBytes(context.readCompactInt(), "Mismatch in deadline's signature length");

		try {
			verify();
		}
		catch (NullPointerException | IllegalArgumentException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Checks all constraints expected from a deadline (including the validity of the signature).
	 * 
	 * @throws NullPointerException if some value is unexpectedly {@code null}
	 * @throws IllegalArgumentException if some value is illegal (also if the signature is invalid)
	 */
	private void verify() throws IllegalArgumentException {
		Objects.requireNonNull(prolog, "prolog cannot be null");
		Objects.requireNonNull(value, "value cannot be null");
		Objects.requireNonNull(challenge, "challenge cannot be null");
		Objects.requireNonNull(signature, "signature cannot be null");
	
		if (progressive < 0L)
			throw new IllegalArgumentException("progressive cannot be negative");
	
		if (value.length != challenge.getHashing().length())
			throw new IllegalArgumentException("Illegal deadline value: expected an array of length " + challenge.getHashing().length() + " rather than " + value.length);

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
			challenge.equals(otherAsDeadline.getChallenge()) &&
			Arrays.equals(value, otherAsDeadline.getValue()) &&
			prolog.equals(otherAsDeadline.getProlog()) &&
			Arrays.equals(signature, otherAsDeadline.getSignature());
	}

	@Override
	public int hashCode() {
		return challenge.hashCode();
	}

	@Override
	public Challenge getChallenge() {
		return challenge;
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
		byte[] left = value, right = other instanceof DeadlineImpl di ? di.value : other.getValue(); // optimization

		for (int i = 0; i < left.length; i++) {
			int a = left[i] & 0xff;
			int b = right[i] & 0xff;
			if (a != b)
				return a - b;
		}

		return 0; // deadlines with the same hashing algorithm have the same length
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
		return challenge.getScoopNumber();
	}

	@Override
	public byte[] getGenerationSignature() {
		return challenge.getGenerationSignature();
	}

	@Override
	public HashingAlgorithm getHashing() {
		return challenge.getHashing();
	}

	@Override
	public byte[] getSignature() {
		return signature.clone();
	}

	// TODO: maybe move inside Challenge ?
	@Override
	public <E extends Exception> void matchesOrThrow(Challenge challenge, Function<String, E> exceptionSupplier) throws E {
		if (this.challenge.getScoopNumber() != challenge.getScoopNumber())
			throw exceptionSupplier.apply("Scoop number mismatch (expected " + challenge.getScoopNumber() + " but found " + this.challenge.getScoopNumber() + ")");

		if (!Arrays.equals(this.challenge.getGenerationSignature(), challenge.getGenerationSignature()))
			throw exceptionSupplier.apply("Generation signature mismatch");

		if (!this.challenge.getHashing().equals(challenge.getHashing()))
			throw exceptionSupplier.apply("Hashing algorithm mismatch");
	}

	@Override
	public boolean isValid() {
		return Arrays.equals(value, new NonceImpl(prolog, progressive, challenge.getHashing()).getValueFor(this));
	}

	@Override
	public String toString() {
		return "prolog: { " + prolog + " }, progressive: " + progressive + ", challenge : { " + challenge + " }, value: " + Hex.toHexString(value) + ", signature: " + Hex.toHexString(signature);
	}

	@Override
	public String toStringSanitized() {
		var trimmedSignature = new byte[Math.min(256, signature.length)];
		System.arraycopy(signature, 0, trimmedSignature, 0, trimmedSignature.length);
		// the length of the value is fixed, so no problem with it
		return "prolog: { " + prolog.toStringSanitized() + " }, progressive: " + progressive + ", challenge : { " + challenge.toStringSanitized() + " }, value: " + Hex.toHexString(value) + ", signature: " + Hex.toHexString(trimmedSignature);
	}

	/**
	 * Yields a marshalling of this object into a byte array, without considering its signature.
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
		context.writeStringUnshared(challenge.getHashing().getName());
		prolog.into(context);
		context.writeLong(progressive);
		// we do not write value.length, since it coincides with hashing.length()
		context.writeBytes(value);
		context.writeInt(challenge.getScoopNumber());
		context.writeLengthAndBytes(challenge.getGenerationSignature());
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		intoWithoutSignature(context);
		context.writeLengthAndBytes(signature);
	}
}
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

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Nonces;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.nonce.internal.json.DeadlineJson;

/**
 * Implementation of a deadline inside a plot file. It identifies a nonce
 * and contains a value computed from that nonce. Deadlines are ordered
 * by the lexicographical ordering of their values.
 */
@Immutable
public final class DeadlineImpl extends AbstractMarshallable implements Deadline {
	private final Prolog prolog;
	private final long progressive;
	private final byte[] value;
	private final Challenge challenge;
	private final byte[] signature;  // TODO: do we really need to sign deadlines?

	/**
	 * Yields a deadline.
	 * 
	 * @param prolog the prolog of the nonce of the deadline
	 * @param progressive the progressive number of the nonce of the deadline
	 * @param value the value of the deadline
	 * @param challenge the challenge the deadline answers to
	 * @param privateKey the private key that will be used to sign the deadline; it must match the
	 *                   public key contained in the prolog
	 * @return the deadline
	 * @throws SignatureException if the signature of the deadline is invalid
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public DeadlineImpl(Prolog prolog, long progressive, byte[] value, Challenge challenge, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		this(prolog, progressive, value, challenge, privateKey, IllegalArgumentException::new);
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
	 */
	public DeadlineImpl(Prolog prolog, long progressive, byte[] value, Challenge challenge, byte[] signature) {
		this(prolog, challenge, progressive, value, signature, IllegalArgumentException::new);
	}

	/**
	 * Unmarshals a deadline from the given context. It assumes that it was marshalled
	 * by using {@link Deadline#into(MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @throws IOException if the deadline could not be unmarshalled
	 * @throws NoSuchAlgorithmException if the deadline refers to an unknown cryptographic algorithm
	 */
	public DeadlineImpl(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		this(
			context,
			Prologs.from(context),
			Challenges.from(context),
			context.readLong()
		);
	}

	/**
	 * Unmarshals a deadline from the given context. It assumes that it was marshalled
	 * by using {@link Deadline#intoWithoutConfigurationData(MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @param chainId the chain identifier of the node storing the deadline
	 * @param hashingForDeadlines the hashing algorithm for the deadlines
	 * @param hashingForGenerations the hashing algorithm for the generation signatures
	 * @param signatureForBlocks the signature algorithm for the blocks
	 * @param signatureForDeadlines the signature algorithm for the deadlines
	 * @throws IOException if the deadline could not be unmarshalled
	 */
	public DeadlineImpl(UnmarshallingContext context, String chainId, HashingAlgorithm hashingForDeadlines, HashingAlgorithm hashingForGenerations,
			SignatureAlgorithm signatureForBlocks, SignatureAlgorithm signatureForDeadlines) throws IOException {

		this(
			Prologs.from(context, chainId, signatureForBlocks, signatureForDeadlines),
			Challenges.from(context, hashingForDeadlines, hashingForGenerations),
			context.readLong(),
			context.readBytes(hashingForDeadlines.length(), "Mismatch in deadline's value length"),
			readSignature(context, signatureForDeadlines),
			IOException::new
		);
	}

	/**
	 * Creates a deadline from the given JSON description.
	 * 
	 * @param json the JSON description
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 * @throws NoSuchAlgorithmException if {@code json} refers to a non-existent cryptographic algorithm
	 */
	public DeadlineImpl(DeadlineJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
		this(
			json.getProlog().unmap(),
			json.getChallenge().unmap(),
			json.getProgressive(),
			Hex.fromHexString(json.getValue(), InconsistentJsonException::new),
			Hex.fromHexString(json.getSignature(), InconsistentJsonException::new),
			InconsistentJsonException::new
		);
	}

	private DeadlineImpl(UnmarshallingContext context, Prolog prolog, Challenge challenge, long progressive) throws IOException {
		this(
			prolog, challenge, progressive,
			context.readBytes(challenge.getHashingForDeadlines().length(), "Mismatch in deadline's value length"),
			readSignature(context, prolog.getSignatureForDeadlines()),
			IOException::new
		);
	}

	/**
	 * Yields a deadline.
	 * 
	 * @param <E> the type of exception thrown if some argument is illegal
	 * @param prolog the prolog of the nonce of the deadline
	 * @param progressive the progressive number of the nonce of the deadline
	 * @param value the value of the deadline
	 * @param challenge the challenge the deadline responds to
	 * @param signature the signature of the deadline with the private key corresponding to the public key contained in the prolog
	 * @param onIllegalArgs the supplier of the exception to throw if some argument is illegal
	 * @return the deadline
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> DeadlineImpl(Prolog prolog, Challenge challenge, long progressive, byte[] value, byte[] signature, ExceptionSupplierFromMessage<E> onIllegalArgs) throws E {
		this.prolog = Objects.requireNonNull(prolog, "prolog cannot be null", onIllegalArgs);
		this.progressive = progressive;
		this.value = Objects.requireNonNull(value, "value cannot be null", onIllegalArgs).clone();
		this.challenge = Objects.requireNonNull(challenge, "challenge cannot be null", onIllegalArgs);
		this.signature = Objects.requireNonNull(signature, "signature cannot be null", onIllegalArgs).clone();

		if (progressive < 0L)
			throw onIllegalArgs.apply("progressive cannot be negative");
	
		if (value.length != challenge.getHashingForDeadlines().length())
			throw onIllegalArgs.apply("value length mismatch: expected " + challenge.getHashingForDeadlines().length() + " but found " + value.length);

		var maybeLength = prolog.getSignatureForDeadlines().length();
		if (maybeLength.isPresent() && signature.length != maybeLength.getAsInt())
			throw onIllegalArgs.apply("Mismatch in deadline's signature length: expected " + maybeLength.getAsInt() + " but found " + signature.length);
	}

	/**
	 * Yields a deadline, signed with a private key.
	 * 
	 * @param <E> the exception thrown if some argument is illegal
	 * @param prolog the prolog of the nonce of the deadline
	 * @param progressive the progressive number of the nonce of the deadline
	 * @param value the value of the deadline
	 * @param challenge the challenge the deadline answers to
	 * @param privateKey the private key that will be used to sign the deadline; it must match the
	 *                   public key contained in the prolog
	 * @param onIllegalArgs the producer of the exception thrown if some argument is illegal
	 * @return the deadline
	 * @throws E if some argument is illegal
	 * @throws SignatureException if the signature of the deadline is invalid
	 * @throws InvalidKeyException if the private key is invalid
	 */
	private <E extends Exception> DeadlineImpl(Prolog prolog, long progressive, byte[] value, Challenge challenge, PrivateKey privateKey, ExceptionSupplierFromMessage<E> onIllegalArgs) throws E, InvalidKeyException, SignatureException {
		this.prolog = Objects.requireNonNull(prolog, "prolog cannot be null", onIllegalArgs);
		this.progressive = progressive;
		this.value = Objects.requireNonNull(value, "value cannot be null", onIllegalArgs).clone();
		this.challenge = Objects.requireNonNull(challenge, "challenge cannot be null", onIllegalArgs);

		if (progressive < 0L)
			throw onIllegalArgs.apply("progressive cannot be negative");
	
		if (value.length != challenge.getHashingForDeadlines().length())
			throw onIllegalArgs.apply("value length mismatch: expected " + challenge.getHashingForDeadlines().length() + " but found " + value.length);

		this.signature = prolog.getSignatureForDeadlines().getSigner
			(Objects.requireNonNull(privateKey, "privateKey cannot be null", onIllegalArgs), DeadlineImpl::toByteArrayWithoutSignature).sign(this);
	}

	private static byte[] readSignature(UnmarshallingContext context, SignatureAlgorithm signature) throws IOException {
		var maybeLength = signature.length();
		if (maybeLength.isEmpty())
			return context.readLengthAndBytes("Mismatch in deadline's signature length");
		else
			return context.readBytes(maybeLength.getAsInt(), "Mismatch in deadline's signature length");
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof DeadlineImpl di)
			return progressive == di.getProgressive() &&
				challenge.equals(di.getChallenge()) &&
				Arrays.equals(value, di.value) &&
				prolog.equals(di.getProlog()) &&
				Arrays.equals(signature, di.signature);
		else
			return other instanceof Deadline otherAsDeadline &&
				progressive == otherAsDeadline.getProgressive() &&
				challenge.equals(otherAsDeadline.getChallenge()) &&
				Arrays.equals(value, otherAsDeadline.getValue()) &&
				prolog.equals(otherAsDeadline.getProlog()) &&
				Arrays.equals(signature, otherAsDeadline.getSignature());
	}

	@Override
	public int hashCode() {
		return challenge.hashCode() ^ prolog.hashCode();
	}

	@Override
	public Challenge getChallenge() {
		return challenge;
	}

	@Override
	public long getMillisecondsToWait(BigInteger acceleration) {
		var newValueAsBytes = new BigInteger(1, value).divide(acceleration).toByteArray();
		// we recreate an array of the same length as at the beginning
		var dividedValueAsBytes = new byte[value.length];
		System.arraycopy(newValueAsBytes, 0, dividedValueAsBytes, dividedValueAsBytes.length - newValueAsBytes.length, newValueAsBytes.length);
		// we take the first 8 bytes of the divided value
		var firstEightBytes = new byte[] {
			dividedValueAsBytes[0], dividedValueAsBytes[1], dividedValueAsBytes[2], dividedValueAsBytes[3],
			dividedValueAsBytes[4], dividedValueAsBytes[5], dividedValueAsBytes[6], dividedValueAsBytes[7]
		};

		// theoretically, there might be an overflow when converting into long,
		// but this would mean that the waiting time is larger than the life of the universe...
		return new BigInteger(1, firstEightBytes).longValueExact();
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
	public byte[] getSignature() {
		return signature.clone();
	}

	@Override
	public boolean signatureIsValid() throws InvalidKeyException, SignatureException {
		return prolog.getSignatureForDeadlines()
				.getVerifier(prolog.getPublicKeyForSigningDeadlines(), DeadlineImpl::toByteArrayWithoutSignature)
				.verify(this, signature);
	}

	@Override
	public boolean isValid() {
		return Arrays.equals(value, Nonces.of(prolog, progressive, challenge.getHashingForDeadlines()).getValueFor(challenge));
	}

	@Override
	public BigInteger getPower() {
		return BigInteger.ONE.shiftLeft(challenge.getHashingForDeadlines().length() * 8).divide(new BigInteger(1, value).add(BigInteger.ONE));
	}

	@Override
	public String toString() {
		return "prolog: { " + prolog + " }, progressive: " + progressive
			+ ", challenge : { " + challenge + " }, value: " + Hex.toHexString(value)
			+ " (" + challenge.getHashingForDeadlines() + "), signature: "
			+ Hex.toHexString(signature) + " (" + prolog.getSignatureForDeadlines() + ")";
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
		prolog.into(context);
		challenge.into(context);
		context.writeLong(progressive);
		context.writeBytes(value);
	}

	/**
	 * Marshals this deadline into the given context, without its signature and without
	 * information that can be recovered from the configuration of the node storing this deadline.
	 * 
	 * @param context the context
	 * @throws IOException if marshalling fails
	 */
	private void intoWithoutSignatureWithoutConfigurationData(MarshallingContext context) throws IOException {
		prolog.intoWithoutConfigurationData(context);
		challenge.intoWithoutConfigurationData(context);
		context.writeLong(progressive);
		context.writeBytes(value);
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		intoWithoutSignature(context);
		writeSignature(context);
	}

	@Override
	public void intoWithoutConfigurationData(MarshallingContext context) throws IOException {
		intoWithoutSignatureWithoutConfigurationData(context);
		writeSignature(context);
	}

	private void writeSignature(MarshallingContext context) throws IOException {
		var maybeLength = prolog.getSignatureForDeadlines().length();
		if (maybeLength.isEmpty())
			context.writeLengthAndBytes(signature);
		else
			context.writeBytes(signature);
	}
}
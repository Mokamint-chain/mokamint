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
	private final byte[] extra;
	private final static byte[] NO_EXTRAS= new byte[0];

	/**
	 * Yields a deadline.
	 * 
	 * @param prolog the prolog of the nonce of the deadline
	 * @param progressive the progressive number of the nonce of the deadline
	 * @param value the value of the deadline
	 * @param challenge the challenge the deadline responds to
	 * @param extra application-specific data
	 * @return the deadline
	 */
	public DeadlineImpl(Prolog prolog, long progressive, byte[] value, Challenge challenge, byte[] extra) {
		this(prolog, challenge, progressive, value, extra, IllegalArgumentException::new);
	}

	/**
	 * Yields a deadline without application-specific data.
	 * 
	 * @param prolog the prolog of the nonce of the deadline
	 * @param progressive the progressive number of the nonce of the deadline
	 * @param value the value of the deadline
	 * @param challenge the challenge the deadline responds to
	 * @return the deadline
	 */
	public DeadlineImpl(Prolog prolog, long progressive, byte[] value, Challenge challenge) {
		this(prolog, challenge, progressive, value, NO_EXTRAS, IllegalArgumentException::new);
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
			context.readLengthAndBytes("Mismatch in deadline's extra length"),
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
			Hex.fromHexString(json.getExtra(), InconsistentJsonException::new),
			InconsistentJsonException::new
		);
	}

	private DeadlineImpl(UnmarshallingContext context, Prolog prolog, Challenge challenge, long progressive) throws IOException {
		this(
			prolog, challenge, progressive,
			context.readBytes(challenge.getHashingForDeadlines().length(), "Mismatch in deadline's value length"),
			context.readLengthAndBytes("Mismatch in deadline's extra length"),
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
	 * @param extra application-specific data
	 * @param onIllegalArgs the supplier of the exception to throw if some argument is illegal
	 * @return the deadline
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> DeadlineImpl(Prolog prolog, Challenge challenge, long progressive, byte[] value, byte[] extra, ExceptionSupplierFromMessage<E> onIllegalArgs) throws E {
		this.prolog = Objects.requireNonNull(prolog, "prolog cannot be null", onIllegalArgs);
		this.progressive = progressive;
		this.value = Objects.requireNonNull(value, "value cannot be null", onIllegalArgs).clone();
		this.challenge = Objects.requireNonNull(challenge, "challenge cannot be null", onIllegalArgs);
		this.extra = Objects.requireNonNull(extra, "extra cannot be null", onIllegalArgs).clone();

		if (progressive < 0L)
			throw onIllegalArgs.apply("progressive cannot be negative");
	
		if (value.length != challenge.getHashingForDeadlines().length())
			throw onIllegalArgs.apply("value length mismatch: expected " + challenge.getHashingForDeadlines().length() + " but found " + value.length);
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof DeadlineImpl di) // optimization
			return progressive == di.getProgressive() &&
				challenge.equals(di.getChallenge()) &&
				Arrays.equals(value, di.value) &&
				prolog.equals(di.getProlog()) &&
				Arrays.equals(extra, di.extra);
		else
			return other instanceof Deadline otherAsDeadline &&
				progressive == otherAsDeadline.getProgressive() &&
				challenge.equals(otherAsDeadline.getChallenge()) &&
				Arrays.equals(value, otherAsDeadline.getValue()) &&
				prolog.equals(otherAsDeadline.getProlog()) &&
				Arrays.equals(extra, otherAsDeadline.getExtra());
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
	public byte[] getExtra() {
		return extra.clone();
	}

	@Override
	public boolean hasExtra() {
		return extra.length > 0;
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
			+ " (" + challenge.getHashingForDeadlines() + "), extra: " + Hex.toHexString(extra);
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		prolog.into(context);
		challenge.into(context);
		context.writeLong(progressive);
		context.writeBytes(value);
		context.writeLengthAndBytes(extra);
	}

	@Override
	public void intoWithoutConfigurationData(MarshallingContext context) throws IOException {
		prolog.intoWithoutConfigurationData(context);
		challenge.intoWithoutConfigurationData(context);
		context.writeLong(progressive);
		context.writeBytes(value);
		context.writeLengthAndBytes(extra);
	}
}
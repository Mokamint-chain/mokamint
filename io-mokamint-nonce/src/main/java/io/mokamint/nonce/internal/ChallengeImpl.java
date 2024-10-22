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
import java.security.NoSuchAlgorithmException;
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
import io.mokamint.nonce.api.Challenge;

/**
 * Implementation of a challenge. It reports the information needed
 * to compute a deadline for this challenge.
 */
@Immutable
public final class ChallengeImpl extends AbstractMarshallable implements Challenge {
	private final int scoopNumber;
	private final byte[] generationSignature;
	private final HashingAlgorithm hashingForDeadlines;
	private final HashingAlgorithm hashingForGenerations;

	public ChallengeImpl(int scoopNumber, byte[] generationSignature, HashingAlgorithm hashingForDeadlines, HashingAlgorithm hashingForGenerations) {
		this.scoopNumber = scoopNumber;
		this.generationSignature = Objects.requireNonNull(generationSignature, "generation signature cannot be null").clone();
		this.hashingForDeadlines = Objects.requireNonNull(hashingForDeadlines, "hashingForDeadlines cannot be null");
		this.hashingForGenerations = Objects.requireNonNull(hashingForGenerations, "hashingForGenerations cannot be null");
	
		if (scoopNumber < 0 || scoopNumber > MAX_SCOOP_NUMBER)
			throw new IllegalArgumentException("scoopNumber must be between 0 and " + MAX_SCOOP_NUMBER);

		if (generationSignature.length != hashingForGenerations.length())
			throw new IllegalArgumentException("Mismatch in generation signature length: found " + generationSignature.length + " but expected " + hashingForGenerations.length());
	}

	/**
	 * Unmarshals a challenge from the given context. It assumes that the challenge
	 * was marshalled by using {@link Challenge#intoWithoutConfigurationData(MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @param hashingForDeadlines the hashing algorithm for the deadlines
	 * @param hashingForGenerations the hashing algorithm for the generation signatures
	 * @throws IOException if the challenge could not be unmarshalled
	 */
	public ChallengeImpl(UnmarshallingContext context, HashingAlgorithm hashingForDeadlines, HashingAlgorithm hashingForGenerations) throws IOException {		
		this.scoopNumber = readScoopNumber(context);
		this.hashingForDeadlines = Objects.requireNonNull(hashingForDeadlines, "hashingForDeadlines cannot be null");
		this.hashingForGenerations = Objects.requireNonNull(hashingForGenerations, "hashingForGenerations cannot be null");
		this.generationSignature = context.readBytes(hashingForGenerations.length(), "Mismatch in challenge's generation signature length");
	}

	@SuppressWarnings("unused")
	private int readScoopNumber(UnmarshallingContext context) throws IOException {
		int scoopNumber = MAX_SCOOP_NUMBER < Short.MAX_VALUE ? context.readShort() : context.readCompactInt();

		if (scoopNumber < 0 || scoopNumber > MAX_SCOOP_NUMBER)
			throw new IOException("scoopNumber must be between 0 and " + MAX_SCOOP_NUMBER);

		return scoopNumber;
	}

	/**
	 * Unmarshals a challenge from the given context. It assumes that the challenge
	 * was marshalled by using {@link Challenge#into(MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @throws IOException if the challenge could not be unmarshalled
	 * @throws NoSuchAlgorithmException if the challenge refers to an unknown cryptographic algorithm
	 */
	public ChallengeImpl(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {		
		this.scoopNumber = readScoopNumber(context);
		this.hashingForDeadlines = HashingAlgorithms.of(context.readStringShared());
		this.hashingForGenerations = HashingAlgorithms.of(context.readStringShared());
		this.generationSignature = context.readBytes(hashingForGenerations.length(), "Mismatch in challenge's generation signature length");
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Challenge otherAsChallenge &&
			scoopNumber == otherAsChallenge.getScoopNumber() &&
			// optimization below
			Arrays.equals(generationSignature, other instanceof ChallengeImpl ci ? ci.generationSignature : otherAsChallenge.getGenerationSignature()) &&
			hashingForDeadlines.equals(otherAsChallenge.getHashingForDeadlines()) &&
			hashingForGenerations.equals(otherAsChallenge.getHashingForGenerations());
	}

	@Override
	public int hashCode() {
		return scoopNumber ^ Arrays.hashCode(generationSignature) ^ hashingForDeadlines.hashCode() ^ hashingForGenerations.hashCode();
	}

	@Override
	public int getScoopNumber() {
		return scoopNumber;
	}

	@Override
	public byte[] getGenerationSignature() {
		return generationSignature.clone();
	}

	@Override
	public HashingAlgorithm getHashingForDeadlines() {
		return hashingForDeadlines;
	}

	@Override
	public HashingAlgorithm getHashingForGenerations() {
		return hashingForGenerations;
	}

	@Override
	public <E extends Exception> void matchesOrThrow(Challenge other, Function<String, E> exceptionSupplier) throws E {
		if (scoopNumber != other.getScoopNumber())
			throw exceptionSupplier.apply("Scoop number mismatch (expected " + other.getScoopNumber() + " but found " + scoopNumber + ")");

		// optimization below
		if (!Arrays.equals(generationSignature, other instanceof ChallengeImpl ci ? ci.generationSignature : other.getGenerationSignature()))
			throw exceptionSupplier.apply("Generation signature mismatch");

		if (!hashingForDeadlines.equals(other.getHashingForDeadlines()))
			throw exceptionSupplier.apply("Hashing algorithm for deadlines mismatch");

		if (!hashingForGenerations.equals(other.getHashingForGenerations()))
			throw exceptionSupplier.apply("Hashing algorithm for generations mismatch");
	}

	@Override
	public String toString() {
		return "scoopNumber: " + scoopNumber + ", generation signature: " + Hex.toHexString(generationSignature) + " (" + hashingForGenerations + "), hashing for deadline: " + hashingForDeadlines;
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		writeScoopNumber(context);
		context.writeStringShared(hashingForDeadlines.getName());
		context.writeStringShared(hashingForGenerations.getName());
		context.writeBytes(generationSignature);
	}

	@SuppressWarnings("unused")
	private void writeScoopNumber(MarshallingContext context) throws IOException {
		if (MAX_SCOOP_NUMBER < Short.MAX_VALUE)
			context.writeShort(scoopNumber);
		else
			context.writeCompactInt(scoopNumber);
	}

	@Override
	public void intoWithoutConfigurationData(MarshallingContext context) throws IOException {
		writeScoopNumber(context);
		context.writeBytes(generationSignature);
	}
}
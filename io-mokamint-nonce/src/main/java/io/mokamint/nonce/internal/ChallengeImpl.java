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
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;

/**
 * Implementation of a challenge. It reports the information needed
 * to compute a deadline for this challenge.
 */
@Immutable
public class ChallengeImpl extends AbstractMarshallable implements Challenge {
	private final int scoopNumber;
	private final byte[] generationSignature;
	private final HashingAlgorithm hashingForDeadlines;

	public ChallengeImpl(int scoopNumber, byte[] generationSignature, HashingAlgorithm hashing) {
		if (scoopNumber < 0 || scoopNumber > Deadline.MAX_SCOOP_NUMBER)
			throw new IllegalArgumentException("scoopNumber must be between 0 and " + Deadline.MAX_SCOOP_NUMBER);

		this.scoopNumber = scoopNumber;
		this.generationSignature = Objects.requireNonNull(generationSignature, "generation signature cannot be null");
		this.hashingForDeadlines = Objects.requireNonNull(hashing, "hashing cannot be null");
	}

	/**
	 * Unmarshals a challenge from the given context.
	 * 
	 * @param context the unmarshalling context
	 * @param hashingForDeadlines the hashing algorithm for the deadlines
	 * @throws NoSuchAlgorithmException if the challenge uses an unknown hashing algorithm
	 * @throws IOException if the challenge could not be unmarshalled
	 */
	public ChallengeImpl(UnmarshallingContext context, HashingAlgorithm hashingForDeadlines) throws NoSuchAlgorithmException, IOException {		
		this.scoopNumber = context.readCompactInt();
		if (scoopNumber < 0 || scoopNumber > Deadline.MAX_SCOOP_NUMBER)
			throw new IOException("scoopNumber must be between 0 and " + Deadline.MAX_SCOOP_NUMBER);

		this.generationSignature = context.readLengthAndBytes("Mismatch in deadline's generation signature length");
		this.hashingForDeadlines = hashingForDeadlines;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof ChallengeImpl ci) // optimization
			return scoopNumber == ci.getScoopNumber() &&
				Arrays.equals(generationSignature, ci.generationSignature) &&
				hashingForDeadlines.equals(ci.getHashingForDeadlines());
		else
			return other instanceof Challenge otherAsChallenge &&
				scoopNumber == otherAsChallenge.getScoopNumber() &&
				Arrays.equals(generationSignature, otherAsChallenge.getGenerationSignature()) &&
				hashingForDeadlines.equals(otherAsChallenge.getHashingForDeadlines());
	}

	@Override
	public int hashCode() {
		return scoopNumber ^ Arrays.hashCode(generationSignature) ^ hashingForDeadlines.hashCode();
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
	public <E extends Exception> void matchesOrThrow(Challenge other, Function<String, E> exceptionSupplier) throws E {
		if (scoopNumber != other.getScoopNumber())
			throw exceptionSupplier.apply("Scoop number mismatch (expected " + other.getScoopNumber() + " but found " + scoopNumber + ")");

		if (!Arrays.equals(generationSignature, other.getGenerationSignature()))
			throw exceptionSupplier.apply("Generation signature mismatch");

		if (!hashingForDeadlines.equals(other.getHashingForDeadlines()))
			throw exceptionSupplier.apply("Hashing algorithm mismatch");
	}

	@Override
	public String toString() {
		return "scoopNumber: " + scoopNumber + ", generation signature: " + Hex.toHexString(generationSignature) + ", hashing: " + hashingForDeadlines;
	}

	@Override
	public String toStringSanitized() {
		var trimmedGenerationSignature = new byte[Math.min(256, generationSignature.length)];
		System.arraycopy(generationSignature, 0, trimmedGenerationSignature, 0, trimmedGenerationSignature.length);

		return "scoopNumber: " + scoopNumber + ", generation signature: " + Hex.toHexString(trimmedGenerationSignature) + ", hashing: " + hashingForDeadlines;
	}

	/**
	 * Marshals this challenge into the given context.
	 * 
	 * @param context the context
	 * @throws IOException if marshalling fails
	 */
	public void into(MarshallingContext context) throws IOException {
		context.writeCompactInt(scoopNumber);
		context.writeLengthAndBytes(generationSignature);
		// we do not write the hashing for deadlines since it will be reconstructed from the configuration of the node
	}
}
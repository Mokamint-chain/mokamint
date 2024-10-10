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
import java.util.Objects;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.Challenge;

/**
 * Implementation of a challenge. It reports the information needed
 * to compute a deadline for this challenge.
 */
@Immutable
public class ChallengeImpl implements Challenge {
	private final int scoopNumber;
	private final byte[] generationSignature;
	private final HashingAlgorithm hashing;

	public ChallengeImpl(int scoopNumber, byte[] generationSignature, HashingAlgorithm hashing) {
		if (scoopNumber < 0 || scoopNumber > Deadline.MAX_SCOOP_NUMBER)
			throw new IllegalArgumentException("scoopNumber must be between 0 and " + Deadline.MAX_SCOOP_NUMBER);

		this.scoopNumber = scoopNumber;
		this.generationSignature = Objects.requireNonNull(generationSignature, "generation signature cannot be null");
		this.hashing = Objects.requireNonNull(hashing, "hashing cannot be null");
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof ChallengeImpl ci) // optimization
			return scoopNumber == ci.getScoopNumber() &&
				Arrays.equals(generationSignature, ci.generationSignature) &&
				hashing.equals(ci.getHashing());
		else
			return other instanceof Challenge otherAsChallenge &&
				scoopNumber == otherAsChallenge.getScoopNumber() &&
				Arrays.equals(generationSignature, otherAsChallenge.getGenerationSignature()) &&
				hashing.equals(otherAsChallenge.getHashing());
	}

	@Override
	public int hashCode() {
		return scoopNumber ^ Arrays.hashCode(generationSignature) ^ hashing.hashCode();
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
	public HashingAlgorithm getHashing() {
		return hashing;
	}

	@Override
	public String toString() {
		return "scoopNumber: " + scoopNumber + ", generation signature: " + Hex.toHexString(generationSignature) + ", hashing: " + hashing;
	}

	@Override
	public String toStringSanitized() {
		var trimmedGenerationSignature = new byte[Math.min(256, generationSignature.length)];
		System.arraycopy(generationSignature, 0, trimmedGenerationSignature, 0, trimmedGenerationSignature.length);

		return "scoopNumber: " + scoopNumber + ", generation signature: " + Hex.toHexString(trimmedGenerationSignature) + ", hashing: " + hashing;
	}
}
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

package io.mokamint.nonce.internal.json;

import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.Hex;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.internal.ChallengeImpl;

/**
 * The JSON representation of a {@link Challenge}.
 */
public abstract class ChallengeJson implements JsonRepresentation<Challenge> {
	private final int scoopNumber;
	private final String generationSignature;
	private final String hashingForDeadlines;
	private final String hashingForGenerations;

	protected ChallengeJson(Challenge challenge) {
		this.scoopNumber = challenge.getScoopNumber();
		this.generationSignature = Hex.toHexString(challenge.getGenerationSignature());
		this.hashingForDeadlines = challenge.getHashingForDeadlines().getName();
		this.hashingForGenerations = challenge.getHashingForGenerations().getName();
	}

	/**
	 * Yields the scoop number of the challenge.
	 * 
	 * @return the scoop number of the challenge
	 */
	public int getScoopNumber() {
		return scoopNumber;
	}

	/**
	 * Yields the generation signature of the challenge.
	 * 
	 * @return the generation signature of the challenge
	 */
	public String getGenerationSignature() {
		return generationSignature;
	}

	/**
	 * Yields the name of the hashing algorithm used for generating the deadline.
	 * 
	 * @return the name of the hashing algorithm
	 */
	public String getHashingForDeadlines() {
		return hashingForDeadlines;
	}

	/**
	 * Yields the name of the hashing algorithm used for the generation signature.
	 * 
	 * @return the name of the hashing algorithm
	 */
	public String getHashingForGenerations() {
		return hashingForGenerations;
	}

	@Override
	public Challenge unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		return new ChallengeImpl(this);
	}
}
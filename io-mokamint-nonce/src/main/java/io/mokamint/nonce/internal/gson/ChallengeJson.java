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

package io.mokamint.nonce.internal.gson;

import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.api.Challenge;

/**
 * The JSON representation of a {@link Challenge}.
 */
public abstract class ChallengeJson implements JsonRepresentation<Challenge> {
	private int scoopNumber;
	private String generationSignature;
	private String hashing;

	/**
	 * Used by Gson.
	 */
	protected ChallengeJson() {}

	protected ChallengeJson(Challenge challenge) {
		this.scoopNumber = challenge.getScoopNumber();
		this.generationSignature = Hex.toHexString(challenge.getGenerationSignature());
		this.hashing = challenge.getHashing().getName();
	}

	@Override
	public Challenge unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		try {
			return Challenges.of(scoopNumber, Hex.fromHexString(generationSignature), HashingAlgorithms.of(hashing));
		}
		catch (HexConversionException | IllegalArgumentException | NullPointerException e) {
			throw new InconsistentJsonException(e);
		}
	}
}
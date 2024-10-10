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

package io.mokamint.nonce;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.internal.ChallengeImpl;
import io.mokamint.nonce.internal.gson.ChallengeDecoder;
import io.mokamint.nonce.internal.gson.ChallengeEncoder;
import io.mokamint.nonce.internal.gson.ChallengeJson;

/**
 * A provider of challenges.
 */
public final class Challenges {

	private Challenges() {}

	/**
	 * Yields a challenge.
	 * 
	 * @param scoopNumber the number of the scoop of the nonce used to compute the deadline
	 * @param generationSignature the generation signature used to compute the deadline
	 * @param hashing the hashing algorithm used to compute the deadline and the nonce
	 * @return the challenge
	 */
	public static Challenge of(int scoopNumber, byte[] generationSignature, HashingAlgorithm hashing) {
		return new ChallengeImpl(scoopNumber, generationSignature, hashing);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends ChallengeEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends ChallengeDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
	public static class Json extends ChallengeJson {

    	/**
    	 * Used by Gson.
    	 */
		public Json() {}

		/**
    	 * Creates the Json representation for the given challenge.
    	 * 
    	 * @param challenge the challenge
    	 */
    	public Json(Challenge challenge) {
    		super(challenge);
    	}
    }
}
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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.hotmoka.websockets.beans.MappedDecoder;
import io.hotmoka.websockets.beans.MappedEncoder;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.internal.ChallengeImpl;
import io.mokamint.nonce.internal.json.ChallengeJson;

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
	 * @param hashingForDeadlines the hashing algorithm used to compute the deadline and the nonce
	 * @param hashingForGenerations the hashing algorithm used for the generation signatures
	 * @return the challenge
	 */
	public static Challenge of(int scoopNumber, byte[] generationSignature, HashingAlgorithm hashingForDeadlines, HashingAlgorithm hashingForGenerations) {
		return new ChallengeImpl(scoopNumber, generationSignature, hashingForDeadlines, hashingForGenerations);
	}

	/**
	 * Factory method that unmarshals a challenge from the given context.
	 * It assumes that the challenge was marshalled by using
	 * {@link Challenge#intoWithoutConfigurationData(io.hotmoka.marshalling.api.MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @param hashingForDeadlines the hashing algorithm for the deadlines
	 * @param hashingForGenerations the hashing algorithm used for the generation signatures
	 * @return the challenge
	 * @throws IOException if the challenge could not be unmarshalled
	 */
	public static Challenge from(UnmarshallingContext context, HashingAlgorithm hashingForDeadlines, HashingAlgorithm hashingForGenerations) throws IOException {
		return new ChallengeImpl(context, hashingForDeadlines, hashingForGenerations);
	}

	/**
	 * Factory method that unmarshals a challenge from the given context.
	 * It assumes that the challenge was marshalled by using
	 * {@link Challenge#into(io.hotmoka.marshalling.api.MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @return the challenge
	 * @throws IOException if the challenge could not be unmarshalled
	 * @throws NoSuchAlgorithmException if the challenge refers to an unknown cryptographic algorithm
	 */
	public static Challenge from(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		return new ChallengeImpl(context);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MappedEncoder<Challenge, Challenges.Json> {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {
			super(Json::new);
		}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends MappedDecoder<Challenge, Challenges.Json> {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {
			super(Json.class);
		}
	}

    /**
     * Json representation.
     */
	public static class Json extends ChallengeJson {

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
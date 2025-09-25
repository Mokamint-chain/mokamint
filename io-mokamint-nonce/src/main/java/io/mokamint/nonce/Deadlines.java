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
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.hotmoka.websockets.beans.MappedDecoder;
import io.hotmoka.websockets.beans.MappedEncoder;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.nonce.internal.DeadlineImpl;
import io.mokamint.nonce.internal.json.DeadlineJson;

/**
 * A provider of deadlines.
 */
public abstract class Deadlines {

	private Deadlines() {}

	/**
	 * Yields a deadline without application-specific data.
	 * 
	 * @param prolog the prolog of the nonce of the deadline
	 * @param progressive the progressive number of the nonce of the deadline
	 * @param value the value of the deadline
	 * @param challenge the challenge the deadline responds to
	 * @return the deadline
	 */
	public static Deadline of(Prolog prolog, long progressive, byte[] value, Challenge challenge) {
		return new DeadlineImpl(prolog, progressive, value, challenge);
	}

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
	public static Deadline of(Prolog prolog, long progressive, byte[] value, Challenge challenge, byte[] extra) {
		return new DeadlineImpl(prolog, progressive, value, challenge, extra);
	}

	/**
	 * Factory method that unmarshals a deadline from the given context. It assumes
	 * that the deadline was marshalled by using
	 * {@link Deadline#intoWithoutConfigurationData(io.hotmoka.marshalling.api.MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @param chainId the chain identifier of the node storing the deadline
	 * @param hashingForDeadlines the hashing algorithm for the deadlines
	 * @param hashingForGenerations the hashing algorithm for the generation signatures
	 * @param signatureForBlocks the signature algorithm for the blocks
	 * @param signatureForDeadlines the signature algorithm for the deadlines
	 * @return the deadline
	 * @throws IOException if the deadline could not be unmarshalled
	 */
	public static Deadline from(UnmarshallingContext context, String chainId, HashingAlgorithm hashingForDeadlines, HashingAlgorithm hashingForGenerations,
			SignatureAlgorithm signatureForBlocks, SignatureAlgorithm signatureForDeadlines) throws IOException {

		return new DeadlineImpl(context, chainId, hashingForDeadlines, hashingForGenerations, signatureForBlocks, signatureForDeadlines);
	}

	/**
	 * Factory method that unmarshals a deadline from the given context. It assumes
	 * that the deadline was marshalled by using
	 * {@link Deadline#into(io.hotmoka.marshalling.api.MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @return the deadline
	 * @throws IOException if the deadline could not be unmarshalled
	 * @throws NoSuchAlgorithmException if the deadline refers to an unknown cryptographic algorithm
	 */
	public static Deadline from(UnmarshallingContext context) throws IOException, NoSuchAlgorithmException {
		return new DeadlineImpl(context);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MappedEncoder<Deadline, Deadlines.Json> {

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
	public static class Decoder extends MappedDecoder<Deadline, Deadlines.Json> {

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
	public static class Json extends DeadlineJson {

		/**
    	 * Creates the Json representation for the given deadline.
    	 * 
    	 * @param deadline the deadline
    	 */
    	public Json(Deadline deadline) {
    		super(deadline);
    	}
    }
}
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.nonce.internal.DeadlineImpl;
import io.mokamint.nonce.internal.gson.DeadlineDecoder;
import io.mokamint.nonce.internal.gson.DeadlineEncoder;
import io.mokamint.nonce.internal.gson.DeadlineJson;

/**
 * A provider of deadlines.
 */
public final class Deadlines {

	private Deadlines() {}

	/**
	 * Yields a deadline.
	 * 
	 * @param prolog the prolog of the nonce of the deadline
	 * @param progressive the progressive number of the nonce of the deadline
	 * @param value the value of the deadline
	 * @param scoopNumber the number of the scoop of the nonce used to compute the deadline
	 * @param data the data used to compute the deadline
	 * @param hashing the hashing algorithm used to compute the deadline and the nonce
	 * @param privateKey the private key that will be used to sign the deadline; it must match the
	 *                   public key contained in the prolog
	 * @return the deadline
	 * @throws SignatureException if the signature of the deadline failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	// TODO: pass a DeadlineDescription instead of scoopNumber and data
	public static Deadline of(Prolog prolog, long progressive, byte[] value, int scoopNumber, byte[] data, HashingAlgorithm hashing, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		return new DeadlineImpl(hashing, prolog, progressive, value, scoopNumber, data, privateKey);
	}

	/**
	 * Yields a deadline.
	 * 
	 * @param prolog the prolog of the nonce of the deadline
	 * @param progressive the progressive number of the nonce of the deadline
	 * @param value the value of the deadline
	 * @param scoopNumber the number of the scoop of the nonce used to compute the deadline
	 * @param data the data used to compute the deadline
	 * @param hashing the hashing algorithm used to compute the deadline and the nonce
	 * @param signature the signature of the resulting deadline
	 * @return the deadline
	 * @throws IllegalArgumentException if some argument is illegal
	 */
	public static Deadline of(Prolog prolog, long progressive, byte[] value, int scoopNumber, byte[] data, HashingAlgorithm hashing, byte[] signature) throws IllegalArgumentException {
		return new DeadlineImpl(hashing, prolog, progressive, value, scoopNumber, data, signature);
	}

	/**
	 * Factory method that unmarshals a deadline from the given context.
	 * 
	 * @param context the unmarshalling context
	 * @return the request
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the deadline is unknown
	 * @throws IOException if the deadline could not be unmarshalled
	 */
	public static Deadline from(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		return new DeadlineImpl(context);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends DeadlineEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends DeadlineDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
	public static class Json extends DeadlineJson {

    	/**
    	 * Used by Gson.
    	 */
		public Json() {}
 
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
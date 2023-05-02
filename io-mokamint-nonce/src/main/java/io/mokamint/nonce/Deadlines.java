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

import io.hotmoka.exceptions.UncheckedNoSuchAlgorithmException;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.hotmoka.websockets.beans.AbstractEncoder;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.internal.DeadlineDecoder;
import io.mokamint.nonce.internal.DeadlineImpl;

/**
 * A provider of deadlines.
 */
public interface Deadlines {

	/**
	 * Yields a deadline.
	 * 
	 * @param prolog the prolog of the nonce of the deadline
	 * @param progressive the progressive number of the nonce of the deadline
	 * @param value the value of the deadline
	 * @param scoopNumber the number of the scoop of the nonce used to compute the deadline
	 * @param data the data used to compute the deadline
	 * @param hashingName the name of the hashing algorithm used to compute the deadline and the nonce
	 * @return the deadline
	 */
	static Deadline of(byte[] prolog, long progressive, byte[] value, int scoopNumber, byte[] data, String hashingName) {
		return new DeadlineImpl(prolog, progressive, value, scoopNumber, data, hashingName);
	}

	/**
	 * Factory method that unmarshals a deadline from the given context.
	 * 
	 * @param context the unmarshalling context
	 * @return the request
	 * @throws UncheckedNoSuchAlgorithmException if the hashing algorithm of the deadline is unknown
	 */
	static Deadline from(UnmarshallingContext context) {
		return new DeadlineImpl(context);
	}

	/**
	 * Gson encoder.
	 */
	static class Encoder extends AbstractEncoder<Deadline> {}

	/**
	 * Gson decoder.
	 */
    static class Decoder extends DeadlineDecoder {}
}
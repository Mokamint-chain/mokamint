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
import io.mokamint.nonce.api.DeadlineDescription;
import io.mokamint.nonce.internal.DeadlineDescriptionDecoder;
import io.mokamint.nonce.internal.DeadlineDescriptionEncoder;
import io.mokamint.nonce.internal.DeadlineDescriptionImpl;

/**
 * A provider of deadline descriptions.
 */
public interface DeadlineDescriptions {

	/**
	 * Yields a deadline description.
	 * 
	 * @param scoopNumber the number of the scoop of the nonce used to compute the deadline
	 * @param data the data used to compute the deadline
	 * @param hashing the hashing algorithm used to compute the deadline and the nonce
	 * @return the deadline description
	 */
	static DeadlineDescription of(int scoopNumber, byte[] data, HashingAlgorithm<byte[]> hashing) {
		return new DeadlineDescriptionImpl(scoopNumber, data, hashing);
	}

	/**
	 * Gson encoder.
	 */
	static class Encoder extends DeadlineDescriptionEncoder {}

	/**
	 * Gson decoder.
	 */
    static class Decoder extends DeadlineDescriptionDecoder {}
}
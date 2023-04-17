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
import io.mokamint.nonce.api.Deadline;
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
	 * @param hashing the hashing algorithm used to compute the deadline and the nonce
	 * @return the deadline
	 */
	static Deadline of(byte[] prolog, long progressive, byte[] value, int scoopNumber, byte[] data, HashingAlgorithm<byte[]> hashing) {
		return new DeadlineImpl(prolog, progressive, value, scoopNumber, data, hashing);
	}
}
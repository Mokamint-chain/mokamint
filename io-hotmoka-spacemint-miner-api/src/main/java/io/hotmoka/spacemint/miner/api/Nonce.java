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

package io.hotmoka.spacemint.miner.api;

/**
 * A nonce. Each nonce contains scoops.
 * Each scoop contains a pair of hashes.
 */
public interface Nonce {

	/**
	 * The number of scoops contained in a nonce.
	 */
	final static int SCOOPS_PER_NONCE = 4096;

	/**
	 * Yields the deadline of this nonce, for the given scoop number and data.
	 * 
	 * @param scoopNumber the number of the scoop to consider to compute the deadline
	 * @param data the data used to compute the deadline
	 * @return the deadline
	 */
	Deadline getDeadline(int scoopNumber, byte[] data);
}
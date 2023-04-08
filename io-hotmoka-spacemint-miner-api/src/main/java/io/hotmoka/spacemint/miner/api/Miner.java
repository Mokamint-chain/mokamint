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
 * A miner in an object that computes deadlines on request.
 * Implementations specify what happens when a deadline has been found:
 * typically, the construction method requires a consumer of the deadline
 * to be specified.
 */
public interface Miner {
	
	/**
	 * Request to the miner the computation of a deadline
	 * for the given scoop number and data.
	 * 
	 * @param scoopNumber the number of the scoop used for finding the deadline
	 * @param data the data
	 */
	void requestDeadline(int scoopNumber, byte[] data);
}
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

package io.mokamint.miner.api;

import java.util.UUID;
import java.util.function.Consumer;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;

/**
 * A miner in an object that computes deadlines on request.
 * Implementations specify what happens when a deadline has been found:
 * typically, the construction method requires a consumer of the deadline
 * to be specified.
 */
@ThreadSafe
public interface Miner extends AutoCloseable {

	/**
	 * Yields the mining specification of this miner.
	 * 
	 * @return the mining specification of this miner
	 */
	MiningSpecification getMiningSpecification();

	/**
	 * Request to the miner the computation of a deadline.
	 * 
	 * @param challenge the challenge for which the deadline is requested
	 * @param onDeadlineComputed the callback executed when a deadline has been found.
	 *                           Miners can call this once, many times or even never.
	 *                           It's up to the user of the miner to be ready for all these situations
	 */
	void requestDeadline(Challenge challenge, Consumer<Deadline> onDeadlineComputed);

	/**
	 * Closes the miner.
	 * 
	 * @throws MinerException if the miner is misbehaving
	 */
	@Override
	void close() throws MinerException;

	/**
	 * Yields the unique identifier of the miner.
	 * 
	 * @return the unique identifier
	 */
	UUID getUUID();

	@Override
	String toString();
}
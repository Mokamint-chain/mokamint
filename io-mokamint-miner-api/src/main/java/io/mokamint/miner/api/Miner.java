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

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;

/**
 * A miner in an object that computes deadlines on request.
 * Implementations specify what happens when a deadline has been found:
 * typically, the construction method requires a consumer of the deadline
 * to be specified.
 */
public interface Miner extends AutoCloseable {
	
	/**
	 * Request to the miner the computation of a deadline.
	 * 
	 * @param description the description of the requested deadline
	 * @param onDeadlineComputed the callback executed when a deadline has been found.
	 *                           It is called with the deadline computed and with miner itself.
	 *                           Miners can call this once, many times or even never.
	 *                           It's up to the user of the miner to be ready for all these situations
	 * @throws RejectedExecutionException if the miner cannot take care of the request at the moment
	 * @throws InterruptedException if the thread has been interrupted while requesting the deadline
	 * @throws TimeoutException if the request timed out
	 * @throws IOException if the is an I/O error
	 */
	void requestDeadline(DeadlineDescription description, BiConsumer<Deadline, Miner> onDeadlineComputed) throws RejectedExecutionException, InterruptedException, TimeoutException, IOException;

	@Override
	void close();
}
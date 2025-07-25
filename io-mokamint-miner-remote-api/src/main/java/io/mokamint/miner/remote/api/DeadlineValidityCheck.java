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

package io.mokamint.miner.remote.api;

import io.mokamint.nonce.api.Deadline;

/**
 * A check of validity for a deadline.
 */
public interface DeadlineValidityCheck {

	/**
	 * Performs the check. If and only if it fails, an exception is thrown.
	 * 
	 * @param deadline the deadline to check
	 * @throws IllegalDeadlineException if the deadline is invalid
	 * @throws InterruptedException if the current thread has been interrupted
	 * @throws DeadlineValidityCheckException if the check for the validity of {@code deadline} could not be accomplished for some reason
	 *                                        (which is not a request of interruption)
	 */
	void check(Deadline deadline) throws IllegalDeadlineException, InterruptedException, DeadlineValidityCheckException;
}
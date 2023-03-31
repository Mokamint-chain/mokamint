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

package io.hotmoka.spacemint.miner;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import io.hotmoka.spacemint.plotter.Deadline;

/**
 * A miner is an object that finds deadlines on-demand.
 */
public interface Miner {
	
	/**
	 * Yields a deadline for the given scoop number and data.
	 * 
	 * @param scoopNumber the number of the scoop used for finding the deadline
	 * @param data the data
	 * @return a deadline
	 * @throws IOException if some I/O error occurred inside the miner
	 * @throws TimeoutException if the miner took too long to provide an answer
	 */
	Deadline getDeadline(int scoopNumber, byte[] data) throws IOException, TimeoutException;
}
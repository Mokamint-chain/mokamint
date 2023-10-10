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

/**
 * 
 */
package io.mokamint.miner.local;

import java.security.KeyPair;

import io.mokamint.miner.local.internal.PlotAndKeyPairImpl;
import io.mokamint.plotter.api.Plot;

/**
 * Suppliers of pairs of plots and key pairs.
 */
public class PlotsAndKeyPairs {

	/**
	 * Yields a new pair of a plot and of a key pair.
	 * 
	 * @param plot the plot file
	 * @param keyPair the key pair
	 * @return the pair
	 * @throws IllegalArgumentException if the public key for signing deadlines of the plot file
	 *                                  does not coincide with the public key of the key pair
	 */
	public static PlotAndKeyPair of(Plot plot, KeyPair keyPair) throws IllegalArgumentException {
		return new PlotAndKeyPairImpl(plot, keyPair);
	}
}
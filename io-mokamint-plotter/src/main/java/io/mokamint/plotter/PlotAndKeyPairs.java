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

package io.mokamint.plotter;

import java.security.KeyPair;

import io.mokamint.plotter.api.Plot;
import io.mokamint.plotter.api.PlotAndKeyPair;
import io.mokamint.plotter.api.WrongKeyException;
import io.mokamint.plotter.internal.PlotAndKeyPairImpl;

/**
 * Suppliers of pairs of plots and key pairs.
 */
public abstract class PlotAndKeyPairs {

	private PlotAndKeyPairs() {}

	/**
	 * Yields a new pair of a plot and of the key pair for signing the deadlines generated with that plot.
	 * 
	 * @param plot the plot file
	 * @param keyPair the key pair
	 * @return the pair
	 * @throws WrongKeyException if the public key for signing deadlines of the plot file
	 *                           does not coincide with the public key of the key pair
	 */
	public static PlotAndKeyPair of(Plot plot, KeyPair keyPair) throws WrongKeyException {
		return new PlotAndKeyPairImpl(plot, keyPair);
	}
}
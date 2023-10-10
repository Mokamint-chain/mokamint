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
package io.mokamint.miner.local.internal;

import java.security.KeyPair;

import io.mokamint.miner.local.PlotAndKeyPair;
import io.mokamint.plotter.api.Plot;

/**
 * Implementation of a plot file and of the key pair for signing the deadlines
 * derived from that plot file.
 */
public class PlotAndKeyPairImpl implements PlotAndKeyPair {

	/**
	 * The plot file.
	 */
	private final Plot plot;

	/**
	 * The key pair.
	 */
	private final KeyPair keyPair;

	/**
	 * Creates a pair of a plot and of a key pair.
	 * 
	 * @param plot the plot file
	 * @param keyPair the key pair
	 * @throws IllegalArgumentException if the public key for signing deadlines of the plot file
	 *                                  does not coincide with the public key of the key pair
	 */
	public PlotAndKeyPairImpl(Plot plot, KeyPair keyPair) throws IllegalArgumentException {
		if (!plot.getProlog().getPublicKeyForSigningDeadlines().equals(keyPair.getPublic()))
			throw new IllegalArgumentException("The public key for signing the deadlines of the plot file does not coincide with the public key in the key pair");

		this.plot = plot;
		this.keyPair = keyPair;
	}

	@Override
	public Plot getPlot() {
		return plot;
	}

	@Override
	public KeyPair getKeyPair() {
		return keyPair;
	}
}
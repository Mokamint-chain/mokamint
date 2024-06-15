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
package io.mokamint.plotter.internal;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.Entropies;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.PlotAndKeyPairs;
import io.mokamint.plotter.api.PlotAndKeyPair;
import io.mokamint.plotter.api.PlotArgs;

/**
 * Partial implementation of the arguments specifying a plot, its key pair and the
 * associated password.
 */
public abstract class PlotArgsImpl implements PlotArgs {

	@Override
	public final PlotAndKeyPair load() throws IOException, NoSuchAlgorithmException {
		var plot = Plots.load(getPlot());
		var entropy = Entropies.load(getKeyPair());
		var passwordAsString = new String(getPassword());
		var keyPair = entropy.keys(passwordAsString, plot.getProlog().getSignatureForBlocks());

		if (!plot.getProlog().getPublicKeyForSigningDeadlines().equals(keyPair.getPublic()))
			throw new IllegalArgumentException("The public key for signing the deadlines of the plot file " + getPlot() +
				" does not coincide with the public key in the key pair " + getKeyPair());

		try {
			return PlotAndKeyPairs.of(plot, keyPair);
		}
		catch (IllegalArgumentException e) {
			throw new IOException(e.getMessage());
		}
		finally {
			passwordAsString = null;
		}
	}
}
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

package io.mokamint.miner.local;

import io.mokamint.miner.api.BalanceProvider;
import io.mokamint.miner.local.api.LocalMiner;
import io.mokamint.miner.local.internal.LocalMinerImpl;
import io.mokamint.plotter.api.PlotAndKeyPair;

/**
 * A provider of miners that work on the local machine.
 */
public abstract class LocalMiners {

	private LocalMiners() {}

	/**
	 * Yields a new local miner.
	 * 
	 * @param name the name of the specification of the miner
	 * @param description the description of the specification of the miner
	 * @param balanceProvider the provider of the balance of the public keys
	 * @param plotsAndKeyPairs the plot files used for mining and their associated key for signing the
	 *                         deadlines generated from them; this cannot be empty; all plots must be for
	 *                         the same mining specification (same chain identifier, same signature algorithms,
	 *                         same public key for the node etc.)
	 * @return the new local miner
	 */
	public static LocalMiner of(String name, String description, BalanceProvider balanceProvider, PlotAndKeyPair... plotsAndKeyPairs) {
		return new LocalMinerImpl(name, description, balanceProvider, plotsAndKeyPairs);
	}
}

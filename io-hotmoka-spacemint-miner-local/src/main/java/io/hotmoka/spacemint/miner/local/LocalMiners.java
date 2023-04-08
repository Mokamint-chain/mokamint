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

package io.hotmoka.spacemint.miner.local;

import io.hotmoka.spacemint.miner.api.Miner;
import io.hotmoka.spacemint.miner.local.internal.LocalMinerImpl;
import io.hotmoka.spacemint.plotter.api.Plot;

/**
 * A provider of miners that work on the local machine.
 */
public interface LocalMiners {

	/**
	 * Yields a new local miner.
	 * 
	 * @param plots the plot files used for mining
	 * @return the new local miner
	 */
	static Miner of(Plot... plots) {
		return new LocalMinerImpl(plots);
	}
}

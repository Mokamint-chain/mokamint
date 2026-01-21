/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.plotter;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.plotter.api.PlotSize;
import io.mokamint.plotter.internal.PlotSizeImpl;

/**
 * Provides of calculator of plot size.
 */
public abstract class PlotSizes {

	private PlotSizes() {}

	/**
	 * Yields the calculation of the size of a plot.
	 * 
	 * @param prolog generic data that identifies, for instance, the creator of the plot
	 * @param length the number of nonces of the plot file
	 * @param hashing the hashing algorithm to use for creating the nonces of the plot file
	 * @return the calculation of the size of the plot
	 */
	public static PlotSize of(Prolog prolog, long length, HashingAlgorithm hashing) {
		return new PlotSizeImpl(prolog, length, hashing);
	}
}
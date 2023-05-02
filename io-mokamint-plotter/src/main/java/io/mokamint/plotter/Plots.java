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

package io.mokamint.plotter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.IntConsumer;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.plotter.api.Plot;
import io.mokamint.plotter.internal.PlotImpl;

/**
 * Provider of a plot file.
 */
public interface Plots {

	/**
	 * Loads a plot file.
	 * 
	 * @param path the path to the file that contains the plot
	 * @throws IOException if the file of the plot cannot be read
	 */
	static Plot load(Path path) throws IOException {
		return new PlotImpl(path);
	}

	/**
	 * Creates a plot file containing sequential nonces for the given prolog.
	 * 
	 * @param path the path to the file where the plot must be dumped
	 * @param prolog generic data that identifies, for instance, the creator
	 *               of the plot. This can be really anything but cannot be {@code null}
	 * @param start the starting progressive number of the nonces in the plot.
	 *              This must be non-negative
	 * @param length the number of nonces to generate. This must be non-negative
	 * @param hashing the hashing algorithm to use for creating the nonces
	 * @param onNewPercent a handler called with the percent of work already done, for feedback
	 * @return the plot that has been created
	 * @throws IOException if the plot file could not be written into {@code path}
	 */
	static Plot create(Path path, byte[] prolog, long start, long length, HashingAlgorithm<byte[]> hashing, IntConsumer onNewPercent) throws IOException {
		return new PlotImpl(path, prolog, start, length, hashing, onNewPercent);
	}
}
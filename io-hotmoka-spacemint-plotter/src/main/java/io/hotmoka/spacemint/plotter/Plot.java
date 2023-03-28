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

package io.hotmoka.spacemint.plotter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.plotter.internal.PlotImpl;

/**
 */
public interface Plot extends AutoCloseable {

	/**
	 * The maximal length of the prolog of a plot, in bytes.
	 */
	public final int MAX_PROLOG_SIZE = 16777216; // 16 megabytes

	/**
	 * Loads a plot file.
	 * 
	 * @param path the path to the file that contains the plot
	 * @throws IOException if the file of the plot cannot be read
	 * @throws NoSuchAlgorithmException if the plot has been created with
	 *                                  a hashing algorithm that is not available
	 */
	static Plot load(Path path) throws IOException, NoSuchAlgorithmException {
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
	 * @return the plot that has been created
	 * @throws IOException if the plot file could not be written into {@code path}
	 */
	static Plot create(Path path, byte[] prolog, long start, long length, HashingAlgorithm<byte[]> hashing) throws IOException {
		return PlotImpl.create(path, prolog, start, length, hashing);
	}

	/**
	 * Yields the prolog of this plot.
	 * 
	 * @return the prolog
	 */
	byte[] getProlog();

	/**
	 * Yields the starting progressive number of the nonces inside this plot.
	 * 
	 * @return the starting progressive number
	 */
	long getStart();

	/**
	 * Yields the number of nonces in this plot.
	 * 
	 * @return the number of nonces
	 */
	long getLength();

	/**
	 * Yields the hashing algorithm used by this plot.
	 * 
	 * @return the hashing algorithm
	 */
	HashingAlgorithm<byte[]> getHashing();

	public static void main(String[] args) throws IOException {
		Path path = Paths.get("pippo.plot");
		Files.deleteIfExists(path);
		create(path, new byte[] { 11, 13, 24, 88 }, 65536L, 10000L, HashingAlgorithm.shabal256((byte[] bytes) -> bytes));
	}
}

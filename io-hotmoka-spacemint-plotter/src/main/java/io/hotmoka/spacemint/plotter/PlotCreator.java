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

import java.io.File;
import java.io.IOException;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.plotter.internal.PlotCreatorImpl;

/**
 * The creator of a plot file. A plot file contains sequential nonces.
 */
public interface PlotCreator {

	/**
	 * Creates a plot file containing sequential nonces for the given prolog.
	 * 
	 * @param where the file where the plot must be dumped
	 * @param prolog generic data that identifies, for instance, the creator
	 *               of the nonces. This can be really anything but cannot be {@code null}
	 * @param start the starting progressive number of the nonces. This must be non-negative
	 * @param length the number of nonces to generate. This must be non-negative
	 * @param hashing the hashing algorithm to use to create the nonces
	 * @throws IOException if the plot file could not be written into {@code where}
	 */
	public static PlotCreator of(File where, byte[] prolog, long start, long length, HashingAlgorithm<byte[]> hashing) throws IOException {
		return new PlotCreatorImpl(where, prolog, start, length, hashing);
	}

	public static void main(String[] args) throws IOException {
		File file = new File("pippo.plot");
		if (file.exists())
			file.delete();

		of(file, new byte[] { 11, 13, 24, 88 }, 65536L, 100L, HashingAlgorithm.shabal256((byte[] bytes) -> bytes));
	}
}
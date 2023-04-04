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
import io.hotmoka.spacemint.miner.api.Deadline;
import io.hotmoka.spacemint.plotter.internal.PlotImpl;

/**
 * A plot file, containing sequential nonces. Each nonce contains
 * a sequence of scoops. Each scoop contains a pair of hashes.
 */
public interface Plot extends io.hotmoka.spacemint.plotter.api.Plot {

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
		return new PlotImpl(path, prolog, start, length, hashing);
	}

	public static void main(String[] args) throws IOException {
		Path path = Paths.get("pippo.plot");
		Files.deleteIfExists(path);
		byte[] prolog = new byte[] { 11, 13, 24, 88 };
		long start = 65536L;
		long length = 1000L;
		var hashing = HashingAlgorithm.shabal256((byte[] bytes) -> bytes);
		int scoopNumber = 13;
		byte[] data = new byte[] { 1, 90, (byte) 180, (byte) 255, 11 };
		Deadline deadline1;
		try (Plot plot = create(path, prolog, start, length, hashing)) {
			deadline1 = plot.getSmallestDeadline(scoopNumber, data);
		}
		System.out.println(deadline1);
		Nonce nonce = Nonce.of(prolog, deadline1.getProgressive(), hashing);
		Deadline deadline2 = nonce.getDeadline(scoopNumber, data);
		System.out.println(deadline2);
	}
}

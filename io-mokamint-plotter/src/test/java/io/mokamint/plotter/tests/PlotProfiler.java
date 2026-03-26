/*
Copyright 2026 Fausto Spoto

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

package io.mokamint.plotter.tests;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Prologs;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.IncompatibleChallengeException;

/**
 * A profiler for plot creation and deadline creation for a challenge.
 */
public class PlotProfiler {

	/**
	 * Runs a profiling loop for the creation of plot files and their use to compute a deadline for a challenge.
	 */
	public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InterruptedException, IncompatibleChallengeException {
		System.out.println("Computing the time for computing a plot and for computing the best deadline for a challenge with that plot");
		Path path = Paths.get("plot.plot");
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		long start = 65536L;
		var hashingForDeadlines = HashingAlgorithms.shabal256();
		var hashingForGenerations = HashingAlgorithms.sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var challenge = Challenges.of(13, generationSignature, hashingForDeadlines, hashingForGenerations);

		for (long length = 1000; length < 1_000_000L; length *= 2) {
			long t1 = System.currentTimeMillis(), t2, t3;
			try (var plot = Plots.create(path, prolog, start, length, hashingForDeadlines, __ -> {})) {
				t2 = System.currentTimeMillis();
				// we compute the deadline from the challenge 100 times, in order to give a more reliable average time
				for (int i = 1; i <= 100; i++)
					plot.getSmallestDeadline(challenge);
				t3 = System.currentTimeMillis();
			}

			System.out.printf("Nonces: %d, bytes: %d, time for creation: %dms, time for challenge: %dms", length, path.toFile().length(), t2 - t1, (t3 - t2) / 100);
		}
	}
}
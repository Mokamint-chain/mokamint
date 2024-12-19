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

package io.mokamint.plotter.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.plotter.Plots;

public class PlotTests extends AbstractLoggedTests {

	@Test
	@DisplayName("plots are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForPlot(@TempDir Path dir) throws Exception {
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		long start = 65536L, length = 2L;
		var hashing = HashingAlgorithms.shabal256();

		try (var plot1 = Plots.create(dir.resolve("pippo.plot"), prolog, start, length, hashing, __ -> {})) {
			String encoded = new Plots.Encoder().encode(plot1);
			var plot2 = new Plots.Decoder().decode(encoded);
			assertEquals(plot1, plot2);
		}
	}

	@Test
	@DisplayName("the best deadline of a plot respects the challenge and is valid")
	public void testDeadlineFromPlotIsValid(@TempDir Path dir) throws Exception {
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		long start = 65536L, length = 100L;
		var hashingForDeadlines = HashingAlgorithms.shabal256();
		var hashingForGenerations = HashingAlgorithms.sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var challenge = Challenges.of(13, generationSignature, hashingForDeadlines, hashingForGenerations);

		try (var plot = Plots.create(dir.resolve("pippo.plot"), prolog, start, length, hashingForDeadlines, __ -> {})) {
			Deadline deadline = plot.getSmallestDeadline(challenge, plotKeyPair.getPrivate());
			deadline.getChallenge().matchesOrThrow(challenge, IllegalArgumentException::new);
			assertTrue(deadline.isValid());
		}
	}
}
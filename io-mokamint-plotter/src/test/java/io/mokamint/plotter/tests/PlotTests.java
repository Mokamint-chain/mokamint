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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.plotter.Plots;

public class PlotTests extends AbstractLoggedTests {

	@Test
	@DisplayName("the best deadline of a plot respects the description and is valid")
	public void testDeadlineFromPlotIsValid(@TempDir Path dir) throws IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, InterruptedException {
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		long start = 65536L, length = 100L;
		var hashing = HashingAlgorithms.shabal256();
		var description = DeadlineDescriptions.of(13, new byte[] { 1, 90, (byte) 180, (byte) 255, 11 }, hashing);

		try (var plot = Plots.create(dir.resolve("pippo.plot"), prolog, start, length, hashing, __ -> {})) {
			Deadline deadline = plot.getSmallestDeadline(description, plotKeyPair.getPrivate());
			deadline.matchesOrThrow(description, IllegalArgumentException::new);
			assertTrue(deadline.isValid());
		}
	}
}
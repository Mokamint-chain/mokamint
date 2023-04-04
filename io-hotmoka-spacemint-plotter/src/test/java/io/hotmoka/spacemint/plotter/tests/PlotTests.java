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

package io.hotmoka.spacemint.plotter.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.miner.api.Deadline;
import io.hotmoka.spacemint.miner.api.Nonce;
import io.hotmoka.spacemint.plotter.Plot;

public class PlotTests {

	@Test
	@DisplayName("selects the best deadline of a plot, recomputes the nonce and then the deadline again")
	public void testDeadlineRecomputation() throws IOException {
		Path path = Paths.get("pippo.plot");
		Files.deleteIfExists(path);
		byte[] prolog = new byte[] { 11, 13, 24, 88 };
		long start = 65536L;
		long length = 100L;
		var hashing = HashingAlgorithm.shabal256((byte[] bytes) -> bytes);
		int scoopNumber = 13;
		byte[] data = new byte[] { 1, 90, (byte) 180, (byte) 255, 11 };

		try {
			Deadline deadline1;
			try (Plot plot = Plot.create(path, prolog, start, length, hashing)) {
				deadline1 = plot.getSmallestDeadline(scoopNumber, data);
			}
			Nonce nonce = deadline1.toNonce();
			Deadline deadline2 = nonce.getDeadline(scoopNumber, data);
			Assertions.assertEquals(deadline1, deadline2);
		}
		finally {
			Files.deleteIfExists(path);
		}
	}
}

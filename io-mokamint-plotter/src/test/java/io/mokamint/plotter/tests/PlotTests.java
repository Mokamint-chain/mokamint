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

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.logging.LogManager;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Nonces;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.plotter.Plots;

public class PlotTests {

	@Test
	@DisplayName("selects the best deadline of a plot, recomputes the nonce and then the deadline again")
	public void testDeadlineRecomputation(@TempDir Path dir) throws IOException {
		var prolog = new byte[] { 11, 13, 24, 88 };
		long start = 65536L, length = 100L;
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var description = DeadlineDescriptions.of(13, new byte[] { 1, 90, (byte) 180, (byte) 255, 11 }, hashing);

		try (var plot = Plots.create(dir.resolve("pippo.plot"), prolog, start, length, hashing, __ -> {})) {
			Deadline deadline1 = plot.getSmallestDeadline(description);
			Deadline deadline2 = Nonces.from(deadline1).getDeadline(description);
			Assertions.assertEquals(deadline1, deadline2);
		}
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = PlotTests.class.getClassLoader().getResource("logging.properties");
			if (resource != null)
				try {
					LogManager.getLogManager().readConfiguration(resource.openStream());
				}
				catch (SecurityException | IOException e) {
					throw new RuntimeException("Cannot load logging.properties file", e);
				}
		}
	}
}
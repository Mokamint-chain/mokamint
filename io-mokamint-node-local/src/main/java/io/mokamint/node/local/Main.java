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

package io.mokamint.node.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.spacemint.miner.local.LocalMiners;
import io.mokamint.application.api.Application;
import io.mokamint.plotter.Plots;

/**
 * A temporary main test.
 */
public class Main {

	public static void main(String[] args) throws IOException {
		var path1 = Paths.get("pippo1.plot");
		Files.deleteIfExists(path1);
		var path2 = Paths.get("pippo2.plot");
		Files.deleteIfExists(path2);
		var path3 = Paths.get("pippo3.plot");
		Files.deleteIfExists(path3);
		var prolog = new byte[] { 11, 13, 24, 88 };
		var hashing = HashingAlgorithms.shabal256((byte[] bytes) -> bytes);

		try (var plot1 = Plots.create(path1, prolog, 65536L, 100L, hashing);
			 var plot2 = Plots.create(path2, prolog, 1024L, 10L, hashing);
	         var plot3 = Plots.create(path3, prolog, 2000L, 20L, hashing);
			 var miner1 = LocalMiners.of(plot1, plot2);
			 var miner2 = LocalMiners.of(plot3);
			 var node = LocalNodes.of(new TestApplication(), miner1, miner2))
		{
			
		}
	}

	private static class TestApplication implements Application {

		@Override
		public boolean prologIsValid(byte[] prolog) {
			return true;
		}
	}
}
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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.LogManager;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.plotter.Plots;

/**
 * A temporary main test.
 */
public class CreatePlots {

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = CreatePlots.class.getClassLoader().getResource("logging.properties");
			if (resource != null)
				try {
					LogManager.getLogManager().readConfiguration(resource.openStream());
				}
				catch (SecurityException | IOException e) {
					throw new RuntimeException("Cannot load logging.properties file", e);
				}
		}
	}

	public static void main(String[] args) throws IOException {
		var path1 = Paths.get("pippo1.plot");
		Files.deleteIfExists(path1);
		var path2 = Paths.get("pippo2.plot");
		Files.deleteIfExists(path2);
		var path3 = Paths.get("pippo3.plot");
		Files.deleteIfExists(path3);
		var prolog = new byte[] { 11, 13, 24, 88 };
		var hashing = HashingAlgorithms.shabal256((byte[] bytes) -> bytes);

		try (var plot1 = Plots.create(path1, prolog, 0L, 30000L, hashing);
			 var plot2 = Plots.create(path2, prolog, 30000L, 30000L, hashing);
	         var plot3 = Plots.create(path3, prolog, 60000L, 30000L, hashing))
		{
			
		}
	}
}
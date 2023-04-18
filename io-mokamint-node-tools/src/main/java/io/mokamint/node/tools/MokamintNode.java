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

package io.mokamint.node.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.logging.LogManager;

import io.mokamint.application.api.Application;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.plotter.Plots;

/**
 * A temporary main test.
 */
public class MokamintNode {

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = MokamintNode.class.getClassLoader().getResource("logging.properties");
			if (resource != null)
				try {
					LogManager.getLogManager().readConfiguration(resource.openStream());
				}
				catch (SecurityException | IOException e) {
					throw new RuntimeException("Cannot load logging.properties file", e);
				}
		}
	}

	public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
		var path1 = Paths.get("pippo1.plot");
		var path2 = Paths.get("pippo2.plot");
		var path3 = Paths.get("pippo3.plot");

		try (var plot1 = Plots.load(path1);
			 var plot2 = Plots.load(path2);
	         var plot3 = Plots.load(path3);
			 var miner1 = LocalMiners.of(plot1, plot2);
			 var miner2 = LocalMiners.of(plot3);
			 var node = LocalNodes.of(new TestApplication(), miner1, miner2))
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			System.out.println("Please press a key to stop the node.");
			reader.readLine();
		}
	}

	private static class TestApplication implements Application {

		@Override
		public boolean prologIsValid(byte[] prolog) {
			return true;
		}
	}
}
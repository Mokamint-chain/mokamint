/*
Copyright 2021 Fausto Spoto

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

package io.mokamint.node.tools.internal;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import io.mokamint.application.api.Application;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "start",
	description = "Start a new node.",
	showDefaultValues = true)
public class Start extends AbstractCommand {

	//@Option(names = "-plot", arity = "1..*", split=",", description = { "a comma-separated list of paths", "to plot files for local mining" } )
	@Parameters(description = { "plot files that will be used for local mining" })
	private Path[] plots;

	@Option(names = "--config", description = { "the toml config file of the node;", "if missing, defaults are used"})
	private Path config;

	@Override
	protected void execute() throws Exception {
		if (plots == null)
			plots = new Path[0];

		startNode(plots, 0, new Plot[plots.length]);
	}

	/**
	 * Loads the given plots, start a local miner on them and run a node
	 * with that miner.
	 * 
	 * @param paths the paths to the plots to load
	 * @param pos the index to the next plot to load
	 * @param plots the plots that are being loaded
	 * @throws IOException if some plot cannot be accessed
	 * @throws NoSuchAlgorithmException if the hashing algorithm of some plot is not available
	 */
	private void startNode(Path[] paths, int pos, Plot[] plots) throws NoSuchAlgorithmException, IOException {
		if (pos < paths.length) {
			try (var plot = plots[pos] = Plots.load(paths[pos])) {
				startNode(paths, pos + 1, plots);
			}
		}
		else if (paths.length > 0) {
			try (var miner = LocalMiners.of(plots);
				 var node = LocalNodes.of(getConfig(), new TestApplication(), miner);
				 var reader = new BufferedReader(new InputStreamReader(System.in))) {

				System.out.println(Ansi.AUTO.string("@|red Press any key to stop the node.|@"));
				reader.readLine();
			}
		}
		else {
			// if there are no plot files, we start the node without any miner
			try (var node = LocalNodes.of(getConfig(), new TestApplication());
				 var reader = new BufferedReader(new InputStreamReader(System.in))) {

				System.out.println(Ansi.AUTO.string("@|red Press any key to stop the node.|@"));
				reader.readLine();
			}
		}
	}

	private Config getConfig() throws FileNotFoundException {
		var config = getConfig2();
		System.out.println(config);
		return config;
	}

	private Config getConfig2() throws FileNotFoundException {
		if (config == null)
			return Config.Builder.defaults().build();
		else try {
			return Config.Builder.load(config).build();
		}
		catch (RuntimeException e) {
			// the toml4j library wraps the FileNotFoundException inside a RuntimeException...
			Throwable cause = e.getCause();
			if (cause instanceof FileNotFoundException)
				throw (FileNotFoundException) cause;
			else
				throw e;
		}
	}

	private static class TestApplication implements Application {

		@Override
		public boolean prologIsValid(byte[] prolog) {
			return true;
		}
	}
}
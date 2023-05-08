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

package io.mokamint.node.tools.internal;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.miner.remote.RemoteMiners;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;
import jakarta.websocket.DeploymentException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "start",
	description = "Start a new node.",
	showDefaultValues = true)
public class Start extends AbstractCommand {

	@Parameters(description = { "plot files that will be used for local mining" })
	private Path[] plots;

	@Option(names = "--config", description = { "the toml config file of the node;", "if missing, defaults are used"})
	private Path config;

	@Option(names = "--miner-port", description = { "the http port where a remote miner", "must be published" })
	private int[] minerPorts;

	@Override
	protected void execute() throws Exception {
		if (plots == null)
			plots = new Path[0];

		if (minerPorts == null)
			minerPorts = new int[0];

		loadPlotsPublishRemoteMinersAndStartNode(plots, 0, new Plot[plots.length]);
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
	 * @throws DeploymentException 
	 * @throws InterruptedException 
	 */
	private void loadPlotsPublishRemoteMinersAndStartNode(Path[] paths, int pos, Plot[] plots) throws IOException, DeploymentException, NoSuchAlgorithmException, InterruptedException {
		if (pos < paths.length)
			try (var plot = plots[pos] = Plots.load(paths[pos])) {
				loadPlotsPublishRemoteMinersAndStartNode(paths, pos + 1, plots);
			}
		else
			publishRemoteMinersAndStartNode(minerPorts, 0, new ArrayList<>(), plots);
	}

	private void publishRemoteMinersAndStartNode(int[] minerPorts, int pos, List<Miner> miners, Plot[] plots) throws IOException, DeploymentException, NoSuchAlgorithmException, InterruptedException {
		if (pos < minerPorts.length)
			try (var remote = RemoteMiners.of(minerPorts[pos])) {
				miners.add(remote);
				publishRemoteMinersAndStartNode(minerPorts, pos + 1, miners, plots);
			}
		else {
			if (plots.length > 0) {
				try (var miner = LocalMiners.of(plots)) {
					miners.add(miner);
					startNode(miners);
				}
			}
			else
				startNode(miners);
		}
	}

	private void startNode(List<Miner> miners) throws IOException, NoSuchAlgorithmException, InterruptedException {
		var config = getConfig();
		ensureExists(config.dir);

		try (var node = LocalNodes.of(config, new TestApplication(), miners.toArray(Miner[]::new))) {
			waitForKeyPress();
		}
	}

	/**
	 * Creates the given directory for the blockchain.
	 * If the directory exists, nothing will happen.
	 * 
	 * @param dir the directory to create, together with its parent directories, if any
	 * @throws IOException if the directory could not be created
	 */
	private void ensureExists(Path dir) throws IOException {
		if (Files.exists(dir)) {
			System.out.println(Ansi.AUTO.string("@|red The path \"" + dir + "\" already exists! Will restart the blockchain from the old database.|@"));
			System.out.println(Ansi.AUTO.string("@|red If you want to start the blockchain from scratch, delete that path and start again this node.|@"));
		}

		Files.createDirectories(dir);
	}

	private void waitForKeyPress() throws IOException {
		try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
			System.out.println(Ansi.AUTO.string("@|red Press any key to stop the node.|@"));
			reader.readLine();
		}
	}

	private Config getConfig() throws FileNotFoundException, NoSuchAlgorithmException {
		var config = getConfig2();
		System.out.println(config);
		return config;
	}

	private Config getConfig2() throws FileNotFoundException, NoSuchAlgorithmException {
		if (config == null)
			return Config.Builder.defaults().build();
		else
			try {
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
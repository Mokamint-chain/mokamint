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

package io.mokamint.miner.tools.internal;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import io.mokamint.miner.api.Miner;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.miner.service.MinerServices;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;
import jakarta.websocket.DeploymentException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "start",
	description = "Start a new mining service.",
	showDefaultValues = true)
public class Start extends AbstractCommand {

	@Parameters(description = { "plot files that will be used for mining" } , arity = "1..*" )
	private Path[] plots;

	@Option(names = "--uri", description = { "the address of the remote mining endpoint(s)", "default: ws://localhost:8025" })
	private URI[] uris;

	@Override
	protected void execute() throws Exception {
		if (uris == null || uris.length == 0)
			uris = new URI[] { new URI("ws://localhost:8025") };

		loadPlotsAndStartMiningServices(plots, 0, new Plot[plots.length]);
	}

	/**
	 * Loads the given plots, start a local miner on them and run a node
	 * with that miner.
	 * 
	 * @param paths the paths to the plots to load
	 * @param pos the index to the next plot to load
	 * @param plots the plots that are being loaded
	 * @throws IOException if some plot cannot be accessed
	 * @throws URISyntaxException 
	 * @throws NoSuchAlgorithmException if the hashing algorithm of some plot file is unknown
	 * @throws DeploymentException 
	 * @throws InterruptedException 
	 */
	private void loadPlotsAndStartMiningServices(Path[] paths, int pos, Plot[] plots) throws IOException, DeploymentException, URISyntaxException, InterruptedException, NoSuchAlgorithmException {
		if (pos < paths.length)
			try (var plot = plots[pos] = Plots.load(paths[pos])) {
				loadPlotsAndStartMiningServices(paths, pos + 1, plots);
			}
		else try (var miner = LocalMiners.of(plots)) {
			startMiningServices(uris, 0, miner);
		}
	}

	private void startMiningServices(URI[] uris, int pos, Miner miner) throws IOException, DeploymentException, URISyntaxException, InterruptedException {
		if (pos < uris.length) {
			System.out.print(Ansi.AUTO.string("@|blue Connecting to " + uris[pos] + "... |@"));

			try (var service = MinerServices.adapt(miner, uris[pos])) {
				System.out.println(Ansi.AUTO.string("@|blue done.|@"));
				startMiningServices(uris, pos + 1, miner);
				service.waitUntilDisconnected();
			}
		}
		else
			System.out.println(Ansi.AUTO.string("@|red Press CTRL+C to stop the miner.|@"));
	}
}
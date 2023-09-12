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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.mokamint.miner.api.Miner;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.miner.service.MinerServices;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;
import io.mokamint.tools.AbstractCommand;
import io.mokamint.tools.CommandException;
import jakarta.websocket.DeploymentException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "start",
	description = "Start a new miner service.",
	showDefaultValues = true)
public class Start extends AbstractCommand {

	@Parameters(description = { "plot files that will be used for mining" } , arity = "1..*" )
	private Path[] plots;

	@Option(names = "--uri", description = { "the address of the remote mining endpoint(s)", "default: ws://localhost:8025" })
	private URI[] uris;

	private final static Logger LOGGER = Logger.getLogger(Start.class.getName());

	@Override
	protected void execute() {
		if (uris == null || uris.length == 0)
			try {
				uris = new URI[] { new URI("ws://localhost:8025") };
			}
			catch (URISyntaxException e) {
				// impossible: the syntax of the URI is correct
				throw new CommandException("The default URI is unexpectedly illegal!", e);
			}

		loadPlotsAndStartMiningServices(0, new ArrayList<>());
	}

	/**
	 * Loads the given plots, start a local miner on them and run a node
	 * with that miner.
	 * 
	 * @param pos the index to the next plot to load
	 * @param plots the plots that are being loaded
	 */
	private void loadPlotsAndStartMiningServices(int pos, List<Plot> plots) {
		if (pos < this.plots.length) {
			System.out.print(Ansi.AUTO.string("@|blue Loading " + this.plots[pos] + "... |@"));
			try (var plot = Plots.load(this.plots[pos])) {
				System.out.println(Ansi.AUTO.string("@|blue done.|@"));
				plots.add(plot);
				loadPlotsAndStartMiningServices(pos + 1, plots);
			}
			catch (IOException e) {
				System.out.println(Ansi.AUTO.string("@|red I/O error! Are you sure the file exists and you have the access rights?|@"));
				LOGGER.log(Level.SEVERE, "I/O error while loading plot file \"" + this.plots[pos] + "\"", e);
				loadPlotsAndStartMiningServices(pos + 1, plots);
			}
			catch (NoSuchAlgorithmException e) {
				System.out.println(Ansi.AUTO.string("@|red failed since the plot file uses an unknown hashing algorithm!|@"));
				LOGGER.log(Level.SEVERE, "the plot file \"" + this.plots[pos] + "\" uses an unknown hashing algorithm", e);
				loadPlotsAndStartMiningServices(pos + 1, plots);
			}
		}
		else if (plots.isEmpty()) {
			throw new CommandException("No plot file could be loaded!");
		}
		else {
			try (var miner = LocalMiners.of(plots.toArray(Plot[]::new))) {
				startMiningServices(0, false, miner);
			}
			catch (IOException e) {
				throw new CommandException("Failed to close the local miner", e);
			}
		}
	}

	private void startMiningServices(int pos, boolean atLeastOne, Miner miner) {
		if (pos < uris.length) {
			System.out.print(Ansi.AUTO.string("@|blue Connecting to " + uris[pos] + "... |@"));

			try (var service = MinerServices.open(miner, uris[pos])) {
				System.out.println(Ansi.AUTO.string("@|blue done.|@"));
				startMiningServices(pos + 1, true, miner);
				service.waitUntilDisconnected();
			}
			catch (DeploymentException e) {
				System.out.println(Ansi.AUTO.string("@|red failed to deploy! Is " + uris[pos] + " up and reachable?|@"));
				LOGGER.log(Level.SEVERE, "cannot deploy a miner service bound to " + uris[pos], e);
				startMiningServices(pos + 1, atLeastOne, miner);
			}
			catch (IOException e) {
				System.out.println(Ansi.AUTO.string("@|red I/O failure! Is " + uris[pos] + " up and reachable?|@"));
				LOGGER.log(Level.SEVERE, "I/O error while deploying a miner service bound to " + uris[pos], e);
				startMiningServices(pos + 1, atLeastOne, miner);
			}
			catch (InterruptedException e) {
				// unexpected: who could interrupt this process?
				throw new CommandException("Unexpected interruption!", e);
			}
		}
		else if (atLeastOne)
			System.out.println(Ansi.AUTO.string("@|green Press CTRL+C to stop the miner.|@"));
	}
}
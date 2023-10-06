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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.mokamint.miner.api.Miner;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.miner.service.MinerServices;
import io.mokamint.miner.service.api.MinerService;
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

	@Parameters(description = "plot files that will be used for mining" , arity = "1..*")
	private Path[] plots;

	@Option(names = "--uri", description = "the address of the remote mining endpoint", defaultValue = "ws://localhost:8025")
	private URI uri;

	private final static Logger LOGGER = Logger.getLogger(Start.class.getName());

	@Override
	protected void execute() throws CommandException {
		loadPlotsAndStartMiningService(0, new ArrayList<>());
	}

	/**
	 * Loads the given plots, start a local miner on them and run a mining service.
	 * 
	 * @param pos the index to the next plot to load
	 * @param plots the plots that are being loaded
	 * @throws CommandException if something erroneous must be logged and the user must be informed
	 */
	private void loadPlotsAndStartMiningService(int pos, List<Plot> plots) throws CommandException {
		if (pos < this.plots.length) {
			System.out.print("Loading " + this.plots[pos] + "... ");
			try (var plot = Plots.load(this.plots[pos])) {
				System.out.println(Ansi.AUTO.string("@|blue done.|@"));
				plots.add(plot);
				loadPlotsAndStartMiningService(pos + 1, plots);
			}
			catch (IOException e) {
				System.out.println(Ansi.AUTO.string("@|red I/O error! Are you sure the file exists and you have the access rights?|@"));
				LOGGER.log(Level.SEVERE, "I/O error while loading plot file \"" + this.plots[pos] + "\"", e);
				loadPlotsAndStartMiningService(pos + 1, plots);
			}
			catch (NoSuchAlgorithmException e) {
				System.out.println(Ansi.AUTO.string("@|red failed since the plot file uses an unknown hashing algorithm!|@"));
				LOGGER.log(Level.SEVERE, "the plot file \"" + this.plots[pos] + "\" uses an unknown hashing algorithm", e);
				loadPlotsAndStartMiningService(pos + 1, plots);
			}
		}
		else if (plots.isEmpty()) {
			throw new CommandException("No plot file could be loaded!");
		}
		else {
			try (var miner = LocalMiners.of(plots.toArray(Plot[]::new))) {
				startMiningService(miner);
			}
			catch (IOException e) {
				throw new CommandException("Failed to close the local miner", e);
			}
		}
	}

	private void startMiningService(Miner miner) throws CommandException {
		System.out.print("Connecting to " + uri + "... ");

		try (var service = MinerServices.open(miner, uri)) {
			System.out.println(Ansi.AUTO.string("@|blue done.|@"));
			new Thread(() -> closeServiceIfKeyPressed(service)).start();
			System.out.println("Service terminated: " + service.waitUntilDisconnected());
		}
		catch (DeploymentException | IOException e) {
			throw new CommandException("Failed to deploy the miner. Is " + uri + " up and reachable?", e);
		}
		catch (InterruptedException e) {
			// unexpected: who could interrupt this process?
			throw new CommandException("Unexpected interruption", e);
		}
	}

	private void closeServiceIfKeyPressed(MinerService service) {
		try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
			System.out.println(Ansi.AUTO.string("@|green Press any key to stop the miner.|@"));
			reader.readLine();
		}
		catch (IOException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot access the standard input!|@"));
			LOGGER.log(Level.SEVERE, "cannot access the standard input", e);
		}

		try {
			service.close(); // this will unlock the waitUntilDisconnected() above
		}
		catch (IOException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot close the service!|@"));
			LOGGER.log(Level.SEVERE, "cannot close the service", e);
		}
	}
}
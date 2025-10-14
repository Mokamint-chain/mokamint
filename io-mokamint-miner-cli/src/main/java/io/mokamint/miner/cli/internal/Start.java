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

package io.mokamint.miner.cli.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.cli.AbstractCommand;
import io.hotmoka.cli.CommandException;
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.miner.service.ReconnectingMinerServices;
import io.mokamint.miner.service.api.MinerService;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "start",
	description = "Start a new miner service.",
	showDefaultValues = true)
public class Start extends AbstractCommand {

	@Parameters(index = "0", description = "the paths of the plots used for mining")
	private Path[] plots;

	@Option(names = "--uri", description = "the URI of the remote mining endpoint", defaultValue = "ws://localhost:8025")
	private URI uri;

	private final static Logger LOGGER = Logger.getLogger(Start.class.getName());

	@Override
	protected void execute() throws CommandException {
		new Run();
	}

	private class Run {
		private final List<Plot> plots = new ArrayList<>();

		private Run() throws CommandException {
			loadPlotsAndStartMiningService(0);
		}

		/**
		 * Loads the plot files, start a local miner on them and run a mining service.
		 * 
		 * @param pos the index to the next plot to load
		 * @throws CommandException if something erroneous must be logged and the user must be informed
		 */
		private void loadPlotsAndStartMiningService(int pos) throws CommandException {
			if (Start.this.plots == null)
				throw new CommandException("At least one plot file must be specified!");
			else if (pos < Start.this.plots.length) {
				var pathOfPlot = Start.this.plots[pos];
				System.out.print("Loading " + pathOfPlot + "... ");
				try (var plot = Plots.load(pathOfPlot)) {
					System.out.println(Ansi.AUTO.string("@|blue done.|@"));
					plots.add(plot);
					loadPlotsAndStartMiningService(pos + 1);
				}
				catch (IOException e) {
					System.out.println(Ansi.AUTO.string("@|red I/O error while accessing plot file " + pathOfPlot + "! " + e.getMessage() + "|@"));
					LOGGER.warning("I/O error while acccessing plot file \"" + pathOfPlot + "\" and its key pair: " + e.getMessage());
					loadPlotsAndStartMiningService(pos + 1);
				}
				catch (NoSuchAlgorithmException e) {
					System.out.println(Ansi.AUTO.string("@|red failed since the plot file " + pathOfPlot + " uses an unknown hashing algorithm!|@"));
					LOGGER.warning("the plot file \"" + pathOfPlot + "\" uses an unknown hashing algorithm: " + e.getMessage());
					loadPlotsAndStartMiningService(pos + 1);
				}
			}
			else if (plots.isEmpty())
				throw new CommandException("No plot file have been loaded!");
			else {
				try (var miner = LocalMiners.of("Internal miner", "A miner working for " + uri, (_signature, _publicKey) -> Optional.empty(), plots.toArray(Plot[]::new))) {
					startMiningService(miner);
				}
			}
		}

		private void startMiningService(Miner miner) throws CommandException {
			System.out.print("Connecting to " + uri + "... ");
		
			try (var service = ReconnectingMinerServices.of(miner, uri, 30_000, this::onConnectionFailed)) {
				System.out.println(Ansi.AUTO.string("@|blue done.|@"));
				new Thread(() -> closeServiceIfKeyPressed(service)).start();
				System.out.println("Service terminated: " + service.waitUntilClosed());
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new CommandException("Interrupted!", e);
			}
		}

		private void onConnectionFailed(FailedDeploymentException e) {
			System.out.println(Ansi.AUTO.string("@|red Failed to deploy the miner. Is " + uri + " up and reachable?|@"));
		}

		private void closeServiceIfKeyPressed(MinerService service) {
			try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
				System.out.print(Ansi.AUTO.string("@|green Press ENTER to stop the miner: |@"));
				reader.readLine();
			}
			catch (IOException e) {
				System.out.println(Ansi.AUTO.string("@|red Cannot access the standard input!|@"));
				LOGGER.log(Level.WARNING, "cannot access the standard input", e);
			}
		
			service.close(); // this will unlock the waitUntilDisconnected() above
		}
	}
}
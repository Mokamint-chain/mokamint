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
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.cli.AbstractCommand;
import io.hotmoka.cli.CommandException;
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.miner.service.MinerServices;
import io.mokamint.miner.service.api.MinerService;
import io.mokamint.plotter.AbstractPlotArgs;
import io.mokamint.plotter.api.PlotAndKeyPair;
import io.mokamint.plotter.api.WrongKeyException;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "start",
	description = "Start a new miner service.",
	showDefaultValues = true)
public class Start extends AbstractCommand {

	/**
	 * The args required for each plot file added to the miner.
	 */
	private static class PlotArgs extends AbstractPlotArgs {

		@Parameters(index = "0", description = "the file containing a plot")
		private Path plot;

		@Parameters(index = "1", description = "the file containing the key pair of the plot")
		private Path keyPair;

		@Option(names = "--plot-password", description = "the password of the key pair of the plot", interactive = true, defaultValue = "")
		private char[] password;

		@Override
		public Path getPlot() {
			return plot;
		}

		@Override
		public Path getKeyPair() {
			return keyPair;
		}

		@Override
		public char[] getPassword() {
			return password;
		}
	}

	@ArgGroup(exclusive = false, multiplicity = "1..*")
	private PlotArgs[] plotArgs;

	@Option(names = "--uri", description = "the URI of the remote mining endpoint", defaultValue = "ws://localhost:8025")
	private URI uri;

	private final static Logger LOGGER = Logger.getLogger(Start.class.getName());

	@Override
	protected void execute() throws CommandException {
		new Run();
	}

	private class Run {
		private final List<PlotAndKeyPair> plotsAndKeyPairs = new ArrayList<>();

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
			if (pos < plotArgs.length) {
				var plotArg = plotArgs[pos];
				System.out.print("Loading " + plotArg.plot + "... ");
				try (var plotAndKeyPair = plotArg.load()) {
					System.out.println(Ansi.AUTO.string("@|blue done.|@"));
					plotsAndKeyPairs.add(plotAndKeyPair);
					loadPlotsAndStartMiningService(pos + 1);
				}
				catch (IOException e) {
					System.out.println(Ansi.AUTO.string("@|red I/O error while accessing plot file " + plotArg.plot + " and its key pair " + plotArg.keyPair + "! " + e.getMessage() + "|@"));
					LOGGER.warning("I/O error while acccessing plot file \"" + plotArg.getPlot() + "\" and its key pair: " + e.getMessage());
					loadPlotsAndStartMiningService(pos + 1);
				}
				catch (NoSuchAlgorithmException e) {
					System.out.println(Ansi.AUTO.string("@|red failed since the plot file " + plotArg.plot + " uses an unknown hashing algorithm!|@"));
					LOGGER.warning("the plot file \"" + plotArg + "\" uses an unknown hashing algorithm: " + e.getMessage());
					loadPlotsAndStartMiningService(pos + 1);
				}
				catch (WrongKeyException e) {
					System.out.println(Ansi.AUTO.string("@|red failed since the plot file " + plotArg.plot + " uses a different key pair than " + plotArg.keyPair + "!|@"));
					LOGGER.warning("the plot file \"" + plotArg + "\" uses a different key pair than " + plotArg.keyPair + ": " + e.getMessage());
					loadPlotsAndStartMiningService(pos + 1);
				}
			}
			else if (plotsAndKeyPairs.isEmpty())
				throw new CommandException("No plot file could be loaded!");
			else {
				try (var miner = LocalMiners.of(plotsAndKeyPairs.toArray(PlotAndKeyPair[]::new))) {
					startMiningService(miner);
				}
			}
		}

		private void startMiningService(Miner miner) throws CommandException {
			System.out.print("Connecting to " + uri + "... ");
		
			try (var service = MinerServices.of(miner, uri, 30_000)) {
				System.out.println(Ansi.AUTO.string("@|blue done.|@"));
				new Thread(() -> closeServiceIfKeyPressed(service)).start();
				System.out.println("Service terminated: " + service.waitUntilClosed());
			}
			catch (FailedDeploymentException e) {
				throw new CommandException("Failed to deploy the miner. Is " + uri + " up and reachable?", e);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new CommandException("Interrupted!", e);
			}
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
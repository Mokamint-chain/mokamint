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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.crypto.Entropies;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.miner.local.PlotAndKeyPair;
import io.mokamint.miner.local.PlotsAndKeyPairs;
import io.mokamint.miner.service.MinerServices;
import io.mokamint.miner.service.api.MinerService;
import io.mokamint.plotter.Plots;
import io.mokamint.tools.AbstractCommand;
import io.mokamint.tools.CommandException;
import jakarta.websocket.DeploymentException;
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
	private static class PlotArgs {

		@Parameters(index = "0", description = "the file containing a plot")
		private Path plot;

		@Parameters(index = "1", description = "the file containing the key pair of the plot")
		private Path keyPair;

		@Option(names = "--password", description = "the password of the key pair of the plot", interactive = true, defaultValue = "")
		private char[] password;

		@Override
		public String toString() {
			return plot + "+" + keyPair + " (" + new String(password) + ")";
		}
	}

	@ArgGroup(exclusive = false, multiplicity = "1..*")
	private PlotArgs[] plotArgs;

	@Option(names = "--uri", description = "the address of the remote mining endpoint", defaultValue = "ws://localhost:8025")
	private URI uri;

	private final static Logger LOGGER = Logger.getLogger(Start.class.getName());

	@Override
	protected void execute() throws CommandException {
		new Run();
	}

	private class Run {
		private final List<PlotAndKeyPair> plotsAndKeyPairs = new ArrayList<>();

		private Run() throws CommandException {
			System.out.println(Arrays.toString(plotArgs));
			loadPlotsAndStartMiningService(0);
		}

		private KeyPair getPlotsKeyPair(Path keyPair, char[] password, SignatureAlgorithm signature) throws CommandException {
			String passwordAsString;
			try {
				var entropy = Entropies.load(keyPair);
				passwordAsString = new String(password);
				return entropy.keys(passwordAsString, signature);
			}
			catch (FileNotFoundException e) {
				throw new CommandException("File " + keyPair + " cannot be found!", e);
			}
			catch (IOException e) {
				throw new CommandException("The key pair could not be loaded from file " + keyPair + "!", e);
			}
			finally {
				passwordAsString = null;
				Arrays.fill(password, ' ');
			}
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
				try (var plot = Plots.load(plotArg.plot)) {
					var prolog = plot.getProlog();
					var keyPair = getPlotsKeyPair(plotArg.keyPair, plotArg.password, prolog.getSignatureForDeadlines());
					if (!prolog.getPublicKeyForSigningDeadlines().equals(keyPair.getPublic())) {
						System.out.println(Ansi.AUTO.string("@|red Illegal public key for signing the deadlines of plot file " + plotArg.plot + "!|@"));
						LOGGER.log(Level.SEVERE, "illegal public key for plot " + plotArg.plot);
					}
					else {
						System.out.println(Ansi.AUTO.string("@|blue done.|@"));
						plotsAndKeyPairs.add(PlotsAndKeyPairs.of(plot, keyPair));
					}
					
					loadPlotsAndStartMiningService(pos + 1);
				}
				catch (IOException e) {
					System.out.println(Ansi.AUTO.string("@|red I/O error! Are you sure the file exists and you have the access rights?|@"));
					LOGGER.log(Level.SEVERE, "I/O error while loading plot file \"" + plotArgs[pos] + "\"", e);
					loadPlotsAndStartMiningService(pos + 1);
				}
				catch (NoSuchAlgorithmException e) {
					System.out.println(Ansi.AUTO.string("@|red failed since the plot file uses an unknown hashing algorithm!|@"));
					LOGGER.log(Level.SEVERE, "the plot file \"" + plotArgs[pos] + "\" uses an unknown hashing algorithm", e);
					loadPlotsAndStartMiningService(pos + 1);
				}
			}
			else if (plotsAndKeyPairs.isEmpty()) {
				throw new CommandException("No plot file could be loaded!");
			}
			else {
				try (var miner = LocalMiners.of(plotsAndKeyPairs.toArray(PlotAndKeyPair[]::new))) {
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
}
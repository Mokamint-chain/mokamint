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

package io.mokamint.node.cli.internal;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.cli.AbstractCommand;
import io.hotmoka.cli.CommandException;
import io.hotmoka.crypto.Entropies;
import io.mokamint.application.ApplicationNotFoundException;
import io.mokamint.application.Applications;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.remote.RemoteApplications;
import io.mokamint.miner.api.MinerException;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.ApplicationTimeoutException;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.node.local.api.LocalNode;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.node.service.RestrictedNodeServices;
import io.mokamint.plotter.AbstractPlotArgs;
import io.mokamint.plotter.api.PlotAndKeyPair;
import io.mokamint.plotter.api.PlotException;
import io.mokamint.plotter.api.WrongKeyException;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "start", description = "Start a new node.")
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

	@ArgGroup(exclusive = false, multiplicity = "0..*")
	private PlotArgs[] plotArgs;

	@Option(names = "--application", description = "the name of the application that will run in the node")
	private String application;

	@Option(names = "--application-uri", description = "the URI where the application that will run in the node has been already published")
	private URI applicationUri;

	@Option(names = "--keys", description = "the file containing the key pair of the node, used to sign the blocks that it mines", required = true)
	private Path keyPair;

    @Option(names = "--password", description = "the password of the key pair of the node", interactive = true, defaultValue = "")
	private char[] password;

    @Option(names = "--config", description = "the toml config file of the node; if missing, defaults are used")
	private Path config;

	@Option(names = "--broadcast-interval", description = "the time interval (in milliseconds) between successive broadcasts of the public IP of the service to all its peers", defaultValue = "1800000")
	private int broadcastInterval;

	@Option(names = "--init", description = "create a genesis block at start-up and start mining", defaultValue = "false")
	private boolean init;

	@Option(names = "--uri", description = "the URI of the public API of the node, such as ws://my.machine.com:8030; if missing, the node will try to use its public IP and the public port numbers")
	private URI uri;

	@Option(names = "--public-port", description = "network ports where the public API of the node will be published")
	private int[] publicPorts;

	@Option(names = "--restricted-port", description = "network ports where the restricted API of the node will be published")
	private int[] restrictedPorts;

	private final static Logger LOGGER = Logger.getLogger(Start.class.getName());

	@Override
	protected void execute() throws CommandException {
		if (broadcastInterval < 1000L) {
			System.out.println(Ansi.AUTO.string("@|red broadcast-interval cannot be smaller than one second!|@"));
			return;
		}

		if ((application == null) == (applicationUri == null)) {
			System.out.println(Ansi.AUTO.string("@|red exactly one of the --application and --application-uri options must be specified!|@"));
			return;
		}

		if (plotArgs == null)
			plotArgs = new PlotArgs[0];

		if (publicPorts == null)
			publicPorts = new int[0];

		if (restrictedPorts == null)
			restrictedPorts = new int[0];

		new Run();
	}

	private LocalNodeConfig getConfig() throws FileNotFoundException, NoSuchAlgorithmException, URISyntaxException {
		return (config == null ? LocalNodeConfigBuilders.defaults() : LocalNodeConfigBuilders.load(config)).build();
	}

	private class Run {
		private final KeyPair keyPair;
		private final LocalNodeConfig config;
		private final List<PlotAndKeyPair> plotsAndKeyPairs = new ArrayList<>();
		private LocalNode node;

		private Run() throws CommandException {
			try {
				this.config = getConfig();
			}
			catch (NoSuchAlgorithmException e) {
				Arrays.fill(password, ' ');
				throw new CommandException("The configuration file \"" + Start.this.config + "\" refers to an unknown hashing algorithm!", e);
			}
			catch (FileNotFoundException e) {
				Arrays.fill(password, ' ');
				throw new CommandException("The configuration file \"" + Start.this.config + "\" does not exist!", e);
			}
			catch (URISyntaxException e) {
				Arrays.fill(password, ' ');
				throw new CommandException("The configuration file \"" + Start.this.config + "\" refers to a URI with wrong syntax!", e);
			}

			String passwordAsString;
			try {
				passwordAsString = new String(password);
				this.keyPair = Entropies.load(Start.this.keyPair).keys(passwordAsString, config.getSignatureForBlocks());
			}
			catch (IOException e) {
				throw new CommandException("Cannot read the key pair from file " + Start.this.keyPair + "!", e);
			}
			finally {
				passwordAsString = null;
				Arrays.fill(password, ' ');
			}

			loadPlotsStartNodeOpenLocalMinerAndPublishNodeServices(0);
		}

		/**
		 * Loads the plots, start a local miner on them and run a node with that miner.
		 * 
		 * @param pos the index to the next plot to load
		 * @throws CommandException if something erroneous must be logged and the user must be informed
		 */
		private void loadPlotsStartNodeOpenLocalMinerAndPublishNodeServices(int pos) throws CommandException {
			if (pos < plotArgs.length) {
				var plotArg = plotArgs[pos];
				System.out.print("Loading " + plotArg.plot + "... ");
				try (var plotAndKeyPair = plotArg.load()) {
					System.out.println(Ansi.AUTO.string("@|blue done.|@"));
					plotsAndKeyPairs.add(plotAndKeyPair);
					loadPlotsStartNodeOpenLocalMinerAndPublishNodeServices(pos + 1);
				}
				catch (IOException e) {
					System.out.println(Ansi.AUTO.string("@|red I/O error while accessing plot file " + plotArg.plot + " and its key pair " + plotArg.keyPair + "! " + e.getMessage() + "|@"));
					LOGGER.warning("I/O error while acccessing plot file \"" + plotArg.getPlot() + "\" and its key pair: " + e.getMessage());
					loadPlotsStartNodeOpenLocalMinerAndPublishNodeServices(pos + 1);
				}
				catch (NoSuchAlgorithmException e) {
					System.out.println(Ansi.AUTO.string("@|red failed since the plot file " + plotArg.plot + " uses an unknown hashing algorithm!|@"));
					LOGGER.warning("the plot file \"" + plotArg + "\" uses an unknown hashing algorithm: " + e.getMessage());
					loadPlotsStartNodeOpenLocalMinerAndPublishNodeServices(pos + 1);
				}
				catch (WrongKeyException e) {
					System.out.println(Ansi.AUTO.string("@|red failed since the plot file " + plotArg.plot + " uses a different key pair than " + plotArg.keyPair + "!|@"));
					LOGGER.warning("the plot file \"" + plotArg + "\" uses a different key pair than " + plotArg.keyPair + ": " + e.getMessage());
					loadPlotsStartNodeOpenLocalMinerAndPublishNodeServices(pos + 1);
				}
				catch (PlotException e) {
					System.out.println(Ansi.AUTO.string("@|red cannot close plot file " + plotArg.getPlot() + "!|@"));
					LOGGER.log(Level.SEVERE, "cannot close file \"" + plotArg.getPlot() + "\"", e);
				}
			}
			else
				startNodeOpenLocalMinerAndPublishNodeServices();
		}

		private void startNodeOpenLocalMinerAndPublishNodeServices() throws CommandException {
			try {
				createWorkingDirectory();
			}
			catch (IOException e) {
				throw new CommandException("Cannot create the working directory " + config.getDir() + "!", e);
			}
			catch (NoSuchAlgorithmException e) {
				throw new CommandException("The signature algorithm required for the key of the node is not available!", e);
			}
			catch (InvalidKeyException e) {
				throw new CommandException("The key of the node could not be encoded!", e);
			}

			try (var app = mkApplication()) {
				System.out.print("Starting a local node... ");
				try (var node = this.node = LocalNodes.of(config, keyPair, app, init)) {
					System.out.println(Ansi.AUTO.string("@|blue done.|@"));

					if (plotsAndKeyPairs.size() >= 1) {
						if (plotsAndKeyPairs.size() == 1)
							System.out.print("Starting a local miner with 1 plot... ");
						else
							System.out.print("Starting a local miner with " + plotsAndKeyPairs.size() + " plots... ");

						try (var miner = LocalMiners.of(plotsAndKeyPairs.toArray(PlotAndKeyPair[]::new))) {
							if (node.add(miner).isPresent()) {
								System.out.println(Ansi.AUTO.string("@|blue done.|@"));
								publishPublicAndRestrictedNodeServices(0);
							}
							else
								throw new CommandException("The miner has not been added!");
						}
						catch (NodeException e) {
							throw new CommandException("The node did not accept the miner!", e);
						}
					}
					else
						publishPublicAndRestrictedNodeServices(0);
				}
				catch (MinerException e) {
					throw new CommandException("Failed to close the local miner", e);
				}
				catch (AlreadyInitializedException e) {
					throw new CommandException("The node is already initialized: delete \"" + config.getDir() + "\" and start again with --init", e);
				}
				catch (NodeException e) {
					throw new CommandException("The node is misbehaving", e);
				}
			}
			catch (ApplicationException | ApplicationTimeoutException e) {
				throw new CommandException("The application is misbehaving", e);
			}
			catch (InterruptedException e) {
				// unexpected: who could interrupt this process?
				Thread.currentThread().interrupt();
				throw new CommandException("Unexpected interruption!", e);
			}
		}

		private void publishPublicAndRestrictedNodeServices(int pos) throws CommandException, InterruptedException {
			if (pos < publicPorts.length) {
				System.out.print("Opening a public node service at port " + publicPorts[pos] + " of localhost... ");
				try (var service = PublicNodeServices.open(node, publicPorts[pos], broadcastInterval, node.getConfig().getWhisperingMemorySize(), Optional.ofNullable(uri))) {
					System.out.println(Ansi.AUTO.string("@|blue done.|@"));
					publishPublicAndRestrictedNodeServices(pos + 1);
				}
				catch (NodeException | TimeoutException e) {
					System.out.println(Ansi.AUTO.string("@|red deployment failed: " + e.getMessage() + "|@"));
					LOGGER.warning("cannot deploy a node service at port " + publicPorts[pos] + ": " + e.getMessage());
					publishPublicAndRestrictedNodeServices(pos + 1);
				}
			}
			else
				publishRestrictedNodeServices(0);
		}

		private void publishRestrictedNodeServices(int pos) throws CommandException {
			if (pos < restrictedPorts.length) {
				System.out.print("Opening a restricted node service at port " + restrictedPorts[pos] + " of localhost... ");
				try (var service = RestrictedNodeServices.open(node, restrictedPorts[pos])) {
					System.out.println(Ansi.AUTO.string("@|blue done.|@"));
					publishRestrictedNodeServices(pos + 1);
				}
				catch (NodeException e) {
					System.out.println(Ansi.AUTO.string("@|red deployment failed: " + e.getMessage() + "|@"));
					LOGGER.warning("cannot deploy a node service at port " + restrictedPorts[pos] + ": " + e.getMessage());
					publishRestrictedNodeServices(pos + 1);
				}
			}
			else
				waitForKeyPress();
		}

		/**
		 * Creates the working directory for the node. If the directory exists, nothing will happen.
		 * 
		 * @throws IOException if the directory could not be created
		 * @throws NoSuchAlgorithmException if the signature algorithm required for the key of the node is not available
		 * @throws InvalidKeyException if the key of the node cannot be encoded
		 */
		private void createWorkingDirectory() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
			Path dir = config.getDir();

			if (Files.exists(dir)) {
				System.out.println(Ansi.AUTO.string("@|red The path \"" + dir + "\" already exists! Will restart the node from the current content of \"" + dir + "\".|@"));
				System.out.println(Ansi.AUTO.string("@|red If you want to start a blockchain from scratch, stop this process, delete \"" + dir + "\" and start again a node with --init.|@"));
			}
			else
				Files.createDirectories(dir);
		}

		private Application mkApplication() throws CommandException {
			Application app;

			if (application != null) {
				System.out.print("Creating the " + application + " application... ");

				try {
					app = Applications.load(application);
				}
				catch (ApplicationNotFoundException e) {
					throw new CommandException(e.getMessage());
				}
			}
			else {
				System.out.print("Connecting to the application at " + applicationUri + "... ");

				try {
					app = RemoteApplications.of(applicationUri, 5000);
					app.addOnCloseHandler(this::onRemoteApplicationClosed);
				}
				catch (ApplicationException e) {
					throw new CommandException("Cannot connect to the remote application at " + applicationUri + ": " + e.getMessage());
				}
			}
		
			System.out.println(Ansi.AUTO.string("@|blue done.|@"));
			return app;
		}

		private void onRemoteApplicationClosed() {
			System.out.println(Ansi.AUTO.string("\n@|red The remote application at " + applicationUri
				+ " has been closed!\nThis node is not able to mine and verify new blocks anymore.|@"));
			System.out.print(Ansi.AUTO.string("@|green Press any key to stop the node.|@"));
		}

		private void waitForKeyPress() throws CommandException {
			try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
				System.out.print(Ansi.AUTO.string("@|green Press any key to stop the node.|@"));
				reader.readLine();
			}
			catch (IOException e) {
				throw new CommandException("Cannot access the standard input!", e);
			}
		}
	}
}
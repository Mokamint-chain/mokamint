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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.crypto.Entropies;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.mokamint.application.api.Application;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.LocalNode;
import io.mokamint.node.local.LocalNodeConfig;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.node.service.RestrictedNodeServices;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;
import io.mokamint.tools.AbstractCommand;
import io.mokamint.tools.CommandException;
import jakarta.websocket.DeploymentException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "start", description = "Start a new node.")
public class Start extends AbstractCommand {

	@Parameters(index = "0", description = "the key of the node, used to sign the blocks that it mines")
	private Path key;

	@Parameters(index = "1..", description = "plot files that will be used for local mining")
	private Path[] plots;

	@Option(names = "--config", description = "the toml config file of the node; if missing, defaults are used")
	private Path config;

	@Option(names = "--broadcast-interval", description = "the time interval (in milliseconds) between successive broadcasts of the public IP of the service to all its peers", defaultValue = "1800000")
	private long broadcastInterval;

	@Option(names = "--init", description = "create a genesis block at start-up and start mining", defaultValue = "false")
	private boolean init;

	@Option(names = "--uri", description = "the URI of the node, such as ws://my.machine.com:8030; if missing, the node will try to use its public IP")
	private URI uri;

	@Option(names = "--public-port", description = "network ports where the public API of the node will be published")
	private int[] publicPorts;

	@Option(names = "--restricted-port", description = "network ports where the restricted API of the node will be published")
	private int[] restrictedPorts;

	@Option(names = "--password", description = "the password of the key of the node; if not specified, it will be asked interactively")
    private String password;

	private final static Logger LOGGER = Logger.getLogger(Start.class.getName());

	@Override
	protected void execute() {
		if (broadcastInterval < 1000L) {
			System.out.println(Ansi.AUTO.string("@|red broadcast-interval cannot be smaller than one second!|@"));
			return;
		}

		if (plots == null)
			plots = new Path[0];

		if (publicPorts == null)
			publicPorts = new int[0];

		if (restrictedPorts == null)
			restrictedPorts = new int[0];

		if (password == null)
			password = askForPassword();

		new Run();
	}

	private String askForPassword() {
		System.out.print("Please insert the password of the new key (press ENTER to use the empty string): ");
		return new String(System.console().readPassword());
	}

	private LocalNodeConfig getConfig() throws FileNotFoundException, NoSuchAlgorithmException, URISyntaxException {
		return (config == null ? LocalNodeConfigBuilders.defaults() : LocalNodeConfigBuilders.load(config)).build();
	}

	private class Run {
		private final KeyPair keyPair;
		private final LocalNodeConfig config;
		private final List<Plot> plots = new ArrayList<>();
		private LocalNode node;

		private Run() {
			try {
				this.keyPair = Entropies.load(key).keys(password, SignatureAlgorithms.ed25519(Function.identity()));
			}
			catch (IOException e) {
				throw new CommandException("Cannot read the pem file " + key + "!", e);
			}
			catch (NoSuchAlgorithmException e) {
				throw new CommandException("The ed25519 signature algorithm is not available!", e);
			}

			try {
				this.config = getConfig();
			}
			catch (NoSuchAlgorithmException e) {
				throw new CommandException("The configuration file \"" + Start.this.config + "\" refers to an unknown hashing algorithm!", e);
			}
			catch (FileNotFoundException e) {
				throw new CommandException("The configuration file \"" + Start.this.config + "\" does not exist!", e);
			}
			catch (URISyntaxException e) {
				throw new CommandException("The configuration file \"" + Start.this.config + "\" refers to a URI with wrong syntax!", e);
			}

			loadPlotsStartNodeOpenLocalMinerAndPublishNodeServices(0);
		}

		/**
		 * Loads the plots, start a local miner on them and run a node with that miner.
		 * 
		 * @param pos the index to the next plot to load
		 */
		private void loadPlotsStartNodeOpenLocalMinerAndPublishNodeServices(int pos) {
			if (pos < Start.this.plots.length) {
				System.out.print("Loading " + Start.this.plots[pos] + "... ");
				try (var plot = Plots.load(Start.this.plots[pos])) {
					System.out.println("done.");
					plots.add(plot);
					loadPlotsStartNodeOpenLocalMinerAndPublishNodeServices(pos + 1);
				}
				catch (IOException e) {
					System.out.println(Ansi.AUTO.string("@|red I/O error! Are you sure the file exists, it is not corrupted and you have the access rights?|@"));
					LOGGER.log(Level.SEVERE, "I/O error while loading plot file \"" + Start.this.plots[pos] + "\"", e);
					loadPlotsStartNodeOpenLocalMinerAndPublishNodeServices(pos + 1);
				}
				catch (NoSuchAlgorithmException e) {
					System.out.println(Ansi.AUTO.string("@|red failed since the plot file uses an unknown hashing algorithm!|@"));
					LOGGER.log(Level.SEVERE, "the plot file \"" + Start.this.plots[pos] + "\" uses an unknown hashing algorithm", e);
					loadPlotsStartNodeOpenLocalMinerAndPublishNodeServices(pos + 1);
				}
			}
			else
				startNodeOpenLocalMinerAndPublishNodeServices();
		}

		private void startNodeOpenLocalMinerAndPublishNodeServices() {
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

			System.out.print("Starting a local node... ");
			try (var node = this.node = LocalNodes.of(config, keyPair, new TestApplication(), init)) {
				System.out.println("done.");

				if (plots.size() >= 1) {
					if (plots.size() == 1)
						System.out.print("Starting a local miner with 1 plot... ");
					else
						System.out.print("Starting a local miner with " + plots.size() + " plots... ");

					try {
						if (node.add(LocalMiners.of(plots.toArray(Plot[]::new))))
							System.out.println("done.");
						else
							System.out.println("no miner has been created.");
					}
					catch (ClosedNodeException e) {
						// unexpected: who could have closed the node?
						throw new CommandException("The node has been unexpectedly closed", e);
					}
				}

				publishPublicAndRestrictedNodeServices(0);
			}
			catch (NoSuchAlgorithmException e) {
				throw new CommandException("The database refers to an unknown hashing algorithm!", e);
			}
			catch (DatabaseException | IOException e) {
				throw new CommandException("The database seems corrupted!", e);
			}
			catch (InterruptedException e) {
				// unexpected: who could interrupt this process?
				throw new CommandException("Unexpected interruption!", e);
			}
			catch (AlreadyInitializedException e) {
				throw new CommandException("The node is already initialized: delete \"" + config.getDir() + "\" and start again with --init", e);
			}
		}

		private void publishPublicAndRestrictedNodeServices(int pos) {
			if (pos < publicPorts.length) {
				System.out.print("Opening a public node service at port " + publicPorts[pos] + " of localhost... ");
				try (var service = PublicNodeServices.open(node, publicPorts[pos], broadcastInterval, node.getConfig().getWhisperingMemorySize(), Optional.ofNullable(uri))) {
					System.out.println("done.");
					publishPublicAndRestrictedNodeServices(pos + 1);
				}
				catch (IOException e) {
					System.out.println(Ansi.AUTO.string("@|red I/O error!|@"));
					LOGGER.log(Level.SEVERE, "I/O error while creating a node service at port " + publicPorts[pos], e);
					publishPublicAndRestrictedNodeServices(pos + 1);
				}
				catch (DeploymentException e) {
					System.out.println(Ansi.AUTO.string("@|red failed to deploy!|@"));
					LOGGER.log(Level.SEVERE, "cannot deploy a node service at port " + publicPorts[pos], e);
					publishPublicAndRestrictedNodeServices(pos + 1);
				}
				catch (IllegalArgumentException e) {
					// for instance, the port number is illegal
					System.out.println(Ansi.AUTO.string("@|red " + e.getMessage() + "|@"));
					LOGGER.log(Level.SEVERE, "cannot deploy a node service at port " + publicPorts[pos], e);
					publishPublicAndRestrictedNodeServices(pos + 1);
				}
				catch (InterruptedException e) {
					throw new CommandException("The close operation of the service at port \" + publicPorts[pos] + \" got interrupted!", e);
				}
			}
			else
				publishRestrictedNodeServices(0);
		}

		private void publishRestrictedNodeServices(int pos) {
			if (pos < restrictedPorts.length) {
				System.out.print("Opening a restricted node service at port " + restrictedPorts[pos] + " of localhost... ");
				try (var service = RestrictedNodeServices.open(node, restrictedPorts[pos])) {
					System.out.println("done.");
					publishRestrictedNodeServices(pos + 1);
				}
				catch (IOException e) {
					System.out.println(Ansi.AUTO.string("@|red I/O error!|@"));
					LOGGER.log(Level.SEVERE, "I/O error while creating a node service at port " + restrictedPorts[pos], e);
					publishRestrictedNodeServices(pos + 1);
				}
				catch (DeploymentException e) {
					System.out.println(Ansi.AUTO.string("@|red failed to deploy!|@"));
					LOGGER.log(Level.SEVERE, "cannot deploy a node service at port " + restrictedPorts[pos], e);
					publishRestrictedNodeServices(pos + 1);
				}
				catch (IllegalArgumentException e) {
					// for instance, the port number is illegal
					System.out.println(Ansi.AUTO.string("@|red " + e.getMessage() + "|@"));
					LOGGER.log(Level.SEVERE, "cannot deploy a node service at port " + restrictedPorts[pos], e);
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
				System.out.println(Ansi.AUTO.string("@|yellow The path \"" + dir + "\" already exists! Will restart the node from the current content of \"" + dir + "\".|@"));
				System.out.println(Ansi.AUTO.string("@|yellow If you want to start a blockchain from scratch, stop this process, delete \"" + dir + "\" and start again a node with --init.|@"));
			}
			else
				Files.createDirectories(dir);
		}

		private void waitForKeyPress() {
			try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
				System.out.println(Ansi.AUTO.string("@|green Press any key to stop the node.|@"));
				reader.readLine();
			}
			catch (IOException e) {
				throw new CommandException("Cannot access the standard input!", e);
			}
		}
	}

	private static class TestApplication implements Application {

		@Override
		public boolean prologExtraIsValid(byte[] extra) {
			return true;
		}
	}
}
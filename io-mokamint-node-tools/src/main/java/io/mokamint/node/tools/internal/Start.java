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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.miner.remote.RemoteMiners;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNode;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.node.service.RestrictedNodeServices;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;
import io.mokamint.tools.AbstractCommand;
import jakarta.websocket.DeploymentException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "start", description = "Start a new node.")
public class Start extends AbstractCommand {

	@Parameters(description = { "plot files that will be used for local mining" })
	private Path[] plots;

	@Option(names = "--config", description = { "the toml config file of the node; if missing, defaults are used"})
	private Path config;

	@Option(names = "--broadcast-interval", description = { "the time interval (in milliseconds) between successive broadcasts of the public IP of the service to all its peers" }, defaultValue = "1800000")
	private long broadcastInterval;

	@Option(names = { "--init" }, description = { "requires to mine a genesis block if the chain is empty; otheriwse, it is ignored" }, defaultValue = "false")
	private boolean init;

	@Option(names = "--uri", description = { "the URI of the node, such as \"ws://my.machine.com:8030\"; if missing, the node will try to use its public IP"})
	private URI uri;

	@Option(names = "--miner-port", description = { "network ports where a remote miner must be published" })
	private int[] minerPorts;

	@Option(names = "--public-port", description = { "network ports where the public API of the node must be published" })
	private int[] publicPorts;

	@Option(names = "--restricted-port", description = { "network ports where the restricted API of the node must be published" })
	private int[] restrictedPorts;

	private final static Logger LOGGER = Logger.getLogger(Start.class.getName());

	@Override
	protected void execute() {
		if (broadcastInterval < 1000L) {
			System.out.println(Ansi.AUTO.string("@|red broadcast-interval cannot be smaller than one second!|@"));
			return;
		}

		if (plots == null)
			plots = new Path[0];

		if (minerPorts == null)
			minerPorts = new int[0];

		if (publicPorts == null)
			publicPorts = new int[0];

		if (restrictedPorts == null)
			restrictedPorts = new int[0];

		loadPlotsCreateLocalMinerPublishRemoteMinersStartNodeAndPublishNodeServices(plots, 0, new ArrayList<>());
	}

	/**
	 * Loads the given plots, start a local miner on them and run a node with that miner.
	 * 
	 * @param paths the paths to the plots to load
	 * @param pos the index to the next plot to load
	 * @param plots the plots that are being loaded
	 */
	private void loadPlotsCreateLocalMinerPublishRemoteMinersStartNodeAndPublishNodeServices(Path[] paths, int pos, List<Plot> plots) {
		if (pos < paths.length) {
			System.out.print(Ansi.AUTO.string("@|blue Loading " + paths[pos] + "... |@"));
			try (var plot = Plots.load(paths[pos])) {
				System.out.println(Ansi.AUTO.string("@|blue done.|@"));
				plots.add(plot);
				loadPlotsCreateLocalMinerPublishRemoteMinersStartNodeAndPublishNodeServices(paths, pos + 1, plots);
			}
			catch (IOException e) {
				System.out.println(Ansi.AUTO.string("@|red I/O error! Are you sure the file exists and you have the access rights?|@"));
				LOGGER.log(Level.SEVERE, "I/O error while loading plot file \"" + paths[pos] + "\"", e);
				loadPlotsCreateLocalMinerPublishRemoteMinersStartNodeAndPublishNodeServices(paths, pos + 1, plots);
			}
			catch (NoSuchAlgorithmException e) {
				System.out.println(Ansi.AUTO.string("@|red failed since the plot file uses an unknown hashing algorithm!|@"));
				LOGGER.log(Level.SEVERE, "the plot file \"" + paths[pos] + "\" uses an unknown hashing algorithm", e);
				loadPlotsCreateLocalMinerPublishRemoteMinersStartNodeAndPublishNodeServices(paths, pos + 1, plots);
			}
		}
		else
			createLocalMinerPublishRemoteMinersStartNodeAndPublishNodeServices(plots);
	}

	private void createLocalMinerPublishRemoteMinersStartNodeAndPublishNodeServices(List<Plot> plots) {
		var miners = new ArrayList<Miner>();

		if (plots.isEmpty())
			publishRemoteMinersStartNodeAndPublishNodeServices(0, miners);
		else {
			if (plots.size() == 1)
				System.out.print(Ansi.AUTO.string("@|blue Starting a local miner with 1 plot... |@"));
			else
				System.out.print(Ansi.AUTO.string("@|blue Starting a local miner with " + plots.size() + " plots... |@"));

			try (var miner = LocalMiners.of(plots.toArray(Plot[]::new))) {
				System.out.println(Ansi.AUTO.string("@|blue done.|@"));
				miners.add(miner);
				publishRemoteMinersStartNodeAndPublishNodeServices(0, miners);
			}
		}
	}

	private void publishRemoteMinersStartNodeAndPublishNodeServices(int pos, List<Miner> miners) {
		if (pos < minerPorts.length) {
			System.out.print(Ansi.AUTO.string("@|blue Starting a remote miner listening at port " + minerPorts[pos] + " of localhost... |@"));
			try (var remote = RemoteMiners.of(minerPorts[pos])) {
				System.out.println(Ansi.AUTO.string("@|blue done.|@"));
				miners.add(remote);
				publishRemoteMinersStartNodeAndPublishNodeServices(pos + 1, miners);
			}
			catch (IOException e) {
				System.out.println(Ansi.AUTO.string("@|red I/O error!|@"));
				LOGGER.log(Level.SEVERE, "I/O error while creating a remote miner at port " + minerPorts[pos], e);
				publishRemoteMinersStartNodeAndPublishNodeServices(pos + 1, miners);
			}
			catch (DeploymentException e) {
				System.out.println(Ansi.AUTO.string("@|red failed to deploy!|@"));
				LOGGER.log(Level.SEVERE, "cannot deploy a remote miner at port " + minerPorts[pos], e);
				publishRemoteMinersStartNodeAndPublishNodeServices(pos + 1, miners);
			}
			catch (IllegalArgumentException e) {
				// for instance, the port number is illegal
				System.out.println(Ansi.AUTO.string("@|red " + e.getMessage() + "|@"));
				LOGGER.log(Level.SEVERE, "cannot deploy a remote miner at port " + minerPorts[pos], e);
				publishRemoteMinersStartNodeAndPublishNodeServices(pos + 1, miners);
			}
		}
		else
			startNodeAndPublishNodeServices(miners);
	}

	private void startNodeAndPublishNodeServices(List<Miner> miners) {
		Config config;

		try {
			config = getConfig();
		}
		catch (NoSuchAlgorithmException e) {
			System.out.println(Ansi.AUTO.string("@|red The configuration file \"" + this.config + "\" refers to an unknown hashing algorithm!|@"));
			LOGGER.log(Level.SEVERE, "the configuration file refers to an unknown hashing algorithm", e);
			return;
		}
		catch (FileNotFoundException e) {
			System.out.println(Ansi.AUTO.string("@|red The configuration file \"" + this.config + "\" does not exist!|@"));
			LOGGER.log(Level.SEVERE, "the configuration file \"" + this.config + "\" does not exist", e);
			return;
		}
		catch (URISyntaxException e) {
			System.out.println(Ansi.AUTO.string("@|red The configuration file \"" + this.config + "\" refers to a URI with wrong syntax!|@"));
			LOGGER.log(Level.SEVERE, "the configuration file \"" + this.config + "\" refers to a URI with wrong syntax", e);
			return;
		}

		try {
			ensureExists(config.dir);
		}
		catch (IOException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot create the directory " + config.dir + "|@"));
			LOGGER.log(Level.SEVERE, "cannot create the directory " + config.dir, e);
			return;
		}

		System.out.print(Ansi.AUTO.string("@|blue Starting a local node... |@"));
		try (var node = LocalNodes.of(config, new TestApplication(), init, miners.toArray(Miner[]::new))) {
			System.out.println(Ansi.AUTO.string("@|blue done.|@"));
			publishPublicAndRestrictedNodeServices(0, node);
		}
		catch (URISyntaxException e) {
			System.out.println(Ansi.AUTO.string("@|red The database refers to an illegal URI!|@"));
			LOGGER.log(Level.SEVERE, "the database refers to an illegal URI", e);
		}
		catch (NoSuchAlgorithmException e) {
			System.out.println(Ansi.AUTO.string("@|red The database refers to an unknown hashing algorithm!|@"));
			LOGGER.log(Level.SEVERE, "the database refers to an unknown hashing algorithm", e);
		}
		catch (DatabaseException | IOException e) {
			System.out.println(Ansi.AUTO.string("@|red the database seems corrupted!|@"));
			LOGGER.log(Level.SEVERE, "the database seems corrupted", e);
		}
		catch (InterruptedException e) {
			// unexpected: who could interrupt this process?
			System.out.println(Ansi.AUTO.string("@|red The process has been interrupted!|@"));
			LOGGER.log(Level.SEVERE, "unexpected interruption", e);
		}
	}

	private void publishPublicAndRestrictedNodeServices(int pos, LocalNode node) {
		if (pos < publicPorts.length) {
			System.out.print(Ansi.AUTO.string("@|blue Starting a public node service at port " + publicPorts[pos] + " of localhost... |@"));
			try (var service = PublicNodeServices.open(node, publicPorts[pos], broadcastInterval, node.getConfig().whisperingMemorySize, Optional.ofNullable(uri))) {
				System.out.println(Ansi.AUTO.string("@|blue done.|@"));
				publishPublicAndRestrictedNodeServices(pos + 1, node);
			}
			catch (IOException e) {
				System.out.println(Ansi.AUTO.string("@|red I/O error!|@"));
				LOGGER.log(Level.SEVERE, "I/O error while creating a node service at port " + publicPorts[pos], e);
				publishPublicAndRestrictedNodeServices(pos + 1, node);
			}
			catch (DeploymentException e) {
				System.out.println(Ansi.AUTO.string("@|red failed to deploy!|@"));
				LOGGER.log(Level.SEVERE, "cannot deploy a node service at port " + publicPorts[pos], e);
				publishPublicAndRestrictedNodeServices(pos + 1, node);
			}
			catch (IllegalArgumentException e) {
				// for instance, the port number is illegal
				System.out.println(Ansi.AUTO.string("@|red " + e.getMessage() + "|@"));
				LOGGER.log(Level.SEVERE, "cannot deploy a node service at port " + publicPorts[pos], e);
				publishPublicAndRestrictedNodeServices(pos + 1, node);
			}
			catch (InterruptedException e) {
				System.out.println(Ansi.AUTO.string("@|red close interrupted!|@"));
				LOGGER.log(Level.SEVERE, "the close operation of the service at port " + publicPorts[pos] + " got interrupted", e);
			}
		}
		else
			publishRestrictedNodeServices(0, node);
	}

	private void publishRestrictedNodeServices(int pos, LocalNode node) {
		if (pos < restrictedPorts.length) {
			System.out.print(Ansi.AUTO.string("@|blue Starting a restricted node service at port " + restrictedPorts[pos] + " of localhost... |@"));
			try (var service = RestrictedNodeServices.open(node, restrictedPorts[pos])) {
				System.out.println(Ansi.AUTO.string("@|blue done.|@"));
				publishRestrictedNodeServices(pos + 1, node);
			}
			catch (IOException e) {
				System.out.println(Ansi.AUTO.string("@|red I/O error!|@"));
				LOGGER.log(Level.SEVERE, "I/O error while creating a node service at port " + restrictedPorts[pos], e);
				publishRestrictedNodeServices(pos + 1, node);
			}
			catch (DeploymentException e) {
				System.out.println(Ansi.AUTO.string("@|red failed to deploy!|@"));
				LOGGER.log(Level.SEVERE, "cannot deploy a node service at port " + restrictedPorts[pos], e);
				publishRestrictedNodeServices(pos + 1, node);
			}
			catch (IllegalArgumentException e) {
				// for instance, the port number is illegal
				System.out.println(Ansi.AUTO.string("@|red " + e.getMessage() + "|@"));
				LOGGER.log(Level.SEVERE, "cannot deploy a node service at port " + restrictedPorts[pos], e);
				publishRestrictedNodeServices(pos + 1, node);
			}
		}
		else
			waitForKeyPress();
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
			System.out.println(Ansi.AUTO.string("@|yellow The path \"" + dir + "\" already exists! Will restart the blockchain from the old database.|@"));
			System.out.println(Ansi.AUTO.string("@|yellow If you want to start the blockchain from scratch, delete that path and start again this node.|@"));
		}

		Files.createDirectories(dir);
	}

	private void waitForKeyPress() {
		try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
			System.out.println(Ansi.AUTO.string("@|green Press any key to stop the node.|@"));
			reader.readLine();
		}
		catch (IOException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot access the standard input!|@"));
			LOGGER.log(Level.SEVERE, "cannot access the standard input", e);
		}
	}

	private Config getConfig() throws FileNotFoundException, NoSuchAlgorithmException, URISyntaxException {
		return (config == null ? Config.Builder.defaults() : Config.Builder.load(config)).build();
	}

	private static class TestApplication implements Application {

		@Override
		public boolean prologIsValid(byte[] prolog) {
			return true;
		}
	}
}
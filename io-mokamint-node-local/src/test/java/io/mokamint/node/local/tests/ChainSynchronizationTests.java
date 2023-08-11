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

package io.mokamint.node.local.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.Database.BlockAddedEvent;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.blockchain.MineNewBlockTask;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.plotter.Plots;
import jakarta.websocket.DeploymentException;

/**
 * Tests about the synchronization of the chain from the peers.
 */
public class ChainSynchronizationTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	@BeforeAll
	public static void beforeAll() {
		app = mock(Application.class);
		when(app.prologIsValid(any())).thenReturn(true);
	}

	@Test
	@DisplayName("a node without mining capacity synchronizes from its peer")
	public void nodeWithoutMinerFollowsPeer(@TempDir Path chain1, @TempDir Path chain2)
			throws URISyntaxException, NoSuchAlgorithmException, InterruptedException,
				   DatabaseException, IOException, DeploymentException, TimeoutException, IncompatiblePeerException, ClosedNodeException, AlreadyInitializedException {

		// how many blocks must be mined by node2 and synchronized/whispered into node1
		final var howMany = 20;

		var port2 = 8034;
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));

		var config1 = Config.Builder.defaults()
			.setDir(chain1)
			.setTargetBlockCreationTime(300L)
			.setInitialAcceleration(1000000000000000L)
			.build();

		var config2 = Config.Builder.defaults()
			.setDir(chain2)
			.setTargetBlockCreationTime(300L)
			.setInitialAcceleration(1000000000000000L)
			.build();

		var semaphore1 = new Semaphore(0);
		var blocksOfNode1 = new HashSet<Block>();

		class MyLocalNode1 extends LocalNodeImpl {

			private MyLocalNode1(Config config) throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(config, app, false); // <--- does not start mining by itself
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof BlockAddedEvent bae) { // these can only come by whispering from node2
					blocksOfNode1.add(bae.block);
					semaphore1.release();
				}

				super.onComplete(event);
			}
		}

		var semaphore2 = new Semaphore(0);
		var blocksOfNode2 = new HashSet<Block>();

		class MyLocalNode2 extends LocalNodeImpl {

			private MyLocalNode2(Config config, Miner... miners) throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(config, app, true, miners); // <--- starts mining by itself
			}

			@Override
			public void submit(Task task) {
				// node2 stops mining at height howMany
				if (task instanceof MineNewBlockTask mnbt && mnbt.previous.isPresent() && mnbt.previous.get().getHeight() >= howMany - 1)
					return;

				super.submit(task);
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof BlockAddedEvent bae) {
					blocksOfNode2.add(bae.block);
					semaphore2.release();
				}

				super.onComplete(event);
			}
		}

		var prolog = new byte[] { 11, 13, 24, 88 };
		long start = 65536L;
		long length = 50L;
		var hashing = HashingAlgorithms.shabal256(Function.identity());

		try (var plot2 = Plots.create(chain2.resolve("plot2.plot"), prolog, start, length, hashing, __ -> {});
			 var miner2 = LocalMiners.of(plot2);
			 var node1 = new MyLocalNode1(config1);
			 var node2 = new MyLocalNode2(config2, miner2);
			 var service2 = PublicNodeServices.open(node2, port2)) {

			// we give node2 the time to mine howMany / 2 blocks
			assertTrue(semaphore2.tryAcquire(howMany / 2, 20, TimeUnit.SECONDS));

			// by adding node2 as peer of node1, the latter will synchronize and then follow
			// the other howMany / 2 blocks by whispering
			node1.addPeer(peer2);

			assertTrue(semaphore1.tryAcquire(howMany, 20, TimeUnit.SECONDS));
			assertTrue(semaphore2.tryAcquire(howMany - howMany / 2, 20, TimeUnit.SECONDS));
			assertEquals(blocksOfNode1, blocksOfNode2);
		}
	}

	@Test
	@DisplayName("a node without mining capacity, once stopped and restarted, synchronizes from its peer")
	public void nodeWithoutMinerStopRestartFollowsPeer(@TempDir Path chain1, @TempDir Path chain2)
			throws URISyntaxException, NoSuchAlgorithmException, InterruptedException,
				   DatabaseException, IOException, DeploymentException, TimeoutException, IncompatiblePeerException, ClosedNodeException, AlreadyInitializedException {

		// how many blocks must be mined by node2 and synchronized/whispered into node1
		final var howMany = 20;

		var port2 = 8034;
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));

		var config1 = Config.Builder.defaults()
			.setDir(chain1)
			.setTargetBlockCreationTime(300L)
			.setInitialAcceleration(1000000000000000L)
			.build();

		var config2 = Config.Builder.defaults()
			.setDir(chain2)
			.setTargetBlockCreationTime(300L)
			.setInitialAcceleration(1000000000000000L)
			.build();

		var semaphore1 = new Semaphore(0);
		var blocksOfNode1 = new HashSet<Block>();

		class MyLocalNode1 extends LocalNodeImpl {

			private MyLocalNode1(Config config) throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(config, app, false); // <--- does not start mining by itself
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof BlockAddedEvent bae) { // these can only come by whispering from node2
					blocksOfNode1.add(bae.block);
					semaphore1.release();
				}

				super.onComplete(event);
			}
		}

		var semaphore2 = new Semaphore(0);
		var blocksOfNode2 = new HashSet<Block>();

		class MyLocalNode2 extends LocalNodeImpl {

			private MyLocalNode2(Config config, Miner... miners) throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(config, app, true, miners); // <--- starts mining by itself
			}

			@Override
			public void submit(Task task) {
				// node2 stops mining at height howMany
				if (task instanceof MineNewBlockTask mnbt && mnbt.previous.isPresent() && mnbt.previous.get().getHeight() >= howMany - 1)
					return;

				super.submit(task);
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof BlockAddedEvent bae) {
					blocksOfNode2.add(bae.block);
					semaphore2.release();
				}

				super.onComplete(event);
			}
		}

		var prolog = new byte[] { 11, 13, 24, 88 };
		long start = 65536L;
		long length = 50L;
		var hashing = HashingAlgorithms.shabal256(Function.identity());

		try (var plot2 = Plots.create(chain2.resolve("plot2.plot"), prolog, start, length, hashing, __ -> {});
			 var miner2 = LocalMiners.of(plot2);
			 var node2 = new MyLocalNode2(config2, miner2);
			 var service2 = PublicNodeServices.open(node2, port2)) {

			try (var node1 = new MyLocalNode1(config1)) {
				// we give node2 the time to mine howMany / 8 blocks
				assertTrue(semaphore2.tryAcquire(howMany / 8, 20, TimeUnit.SECONDS));

				// by adding node2 as peer of node1, the latter will synchronize and then follow the other blocks by whispering
				node1.addPeer(peer2);

				// we wait until node1 has received howMany / 4 blocks
				assertTrue(semaphore1.tryAcquire(howMany / 4, 20, TimeUnit.SECONDS));

				// then we turn node1 off
			}

			// we wait until node2 has mined howMany / 2 blocks
			assertTrue(semaphore2.tryAcquire(howMany / 2 - howMany / 8, 20, TimeUnit.SECONDS));

			// we turn node1 on again
			try (var node1 = new MyLocalNode1(config1)) {
				// we wait until node1 has received all blocks
				assertTrue(semaphore1.tryAcquire(howMany - howMany / 4, 20, TimeUnit.SECONDS));
			}

			// we wait until node2 has received all blocks
			assertTrue(semaphore2.tryAcquire(howMany - howMany / 2 - howMany / 8, 20, TimeUnit.SECONDS));

			assertEquals(blocksOfNode1, blocksOfNode2);
		}
	}

	@Test
	@Disabled
	@DisplayName("a node without mining capacity, once disconnected and reconnected, synchronizes from its peer")
	public void nodeWithoutMinerDisconnectConnectFollowsPeer(@TempDir Path chain1, @TempDir Path chain2)
			throws URISyntaxException, NoSuchAlgorithmException, InterruptedException,
				   DatabaseException, IOException, DeploymentException, TimeoutException, IncompatiblePeerException, ClosedNodeException, AlreadyInitializedException {

		// how many blocks must be mined by node2 and synchronized/whispered into node1
		final var howMany = 20;

		var port2 = 8034;
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));

		var config1 = Config.Builder.defaults()
			.setDir(chain1)
			.setTargetBlockCreationTime(300L)
			.setInitialAcceleration(1000000000000000L)
			.build();

		var config2 = Config.Builder.defaults()
			.setDir(chain2)
			.setTargetBlockCreationTime(300L)
			.setInitialAcceleration(1000000000000000L)
			.build();

		var semaphore1 = new Semaphore(0);
		var blocksOfNode1 = new HashSet<Block>();

		class MyLocalNode1 extends LocalNodeImpl {

			private MyLocalNode1(Config config) throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(config, app, false); // <--- does not start mining by itself
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof BlockAddedEvent bae) { // these can only come by whispering from node2
					blocksOfNode1.add(bae.block);
					System.out.println("node1 " + bae.block.getHeight());
					semaphore1.release();
				}

				super.onComplete(event);
			}
		}

		var semaphore2 = new Semaphore(0);
		var blocksOfNode2 = new HashSet<Block>();

		class MyLocalNode2 extends LocalNodeImpl {

			private MyLocalNode2(Config config, Miner... miners) throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(config, app, true, miners); // <--- starts mining by itself
			}

			@Override
			public void submit(Task task) {
				// node2 stops mining at height howMany
				if (task instanceof MineNewBlockTask mnbt && mnbt.previous.isPresent() && mnbt.previous.get().getHeight() >= howMany - 1)
					return;

				super.submit(task);
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof BlockAddedEvent bae) {
					blocksOfNode2.add(bae.block);
					System.out.println("node2 " + bae.block.getHeight());
					semaphore2.release();
				}

				super.onComplete(event);
			}
		}

		var prolog = new byte[] { 11, 13, 24, 88 };
		long start = 65536L;
		long length = 50L;
		var hashing = HashingAlgorithms.shabal256(Function.identity());

		try (var plot2 = Plots.create(chain2.resolve("plot2.plot"), prolog, start, length, hashing, __ -> {});
			 var miner2 = LocalMiners.of(plot2);
			 var node2 = new MyLocalNode2(config2, miner2);
			 var service2 = PublicNodeServices.open(node2, port2);
			 var node1 = new MyLocalNode1(config1)) {
				// we give node2 the time to mine howMany / 8 blocks
				assertTrue(semaphore2.tryAcquire(howMany / 8, 20, TimeUnit.SECONDS));

				// by adding node2 as peer of node1, the latter will synchronize and then follow the other blocks by whispering
				node1.addPeer(peer2);

				// we wait until node1 has received howMany / 4 blocks
				assertTrue(semaphore1.tryAcquire(howMany / 4, 20, TimeUnit.SECONDS));

				// then we disconnect the two peers
				node1.removePeer(peer2);

				System.out.println("peers of node1: "  + node1.getPeerInfos().map(PeerInfo::getPeer).map(Peer::toString).collect(Collectors.joining(", ")));
				System.out.println("peers of node2: "  + node2.getPeerInfos().map(PeerInfo::getPeer).map(Peer::toString).collect(Collectors.joining(", ")));

				// we wait until node2 has mined howMany / 2 blocks
				assertTrue(semaphore2.tryAcquire(howMany / 2 - howMany / 8, 20, TimeUnit.SECONDS));

				// we reconnect node1 to peer2
				node1.addPeer(peer2);
				System.out.println("peers of node1: "  + node1.getPeerInfos().map(PeerInfo::getPeer).map(Peer::toString).collect(Collectors.joining(", ")));
				System.out.println("peers of node2: "  + node2.getPeerInfos().map(PeerInfo::getPeer).map(Peer::toString).collect(Collectors.joining(", ")));

				// we wait until node1 has received all blocks
				assertTrue(semaphore1.tryAcquire(howMany - howMany / 4, 20, TimeUnit.SECONDS));

			// we wait until node2 has received all blocks
			assertTrue(semaphore2.tryAcquire(howMany - howMany / 2 - howMany / 8, 20, TimeUnit.SECONDS));
		}

		System.out.println("blocksOfNode1: " + blocksOfNode1.stream().map(Block::getHeight).sorted().map(Object::toString).collect(Collectors.joining(",")));
		System.out.println("blocksOfNode2: " + blocksOfNode2.stream().map(Block::getHeight).sorted().map(Object::toString).collect(Collectors.joining(",")));
		assertEquals(blocksOfNode1, blocksOfNode2);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = ChainSynchronizationTests.class.getClassLoader().getResource("logging.properties");
			if (resource != null)
				try (var is = resource.openStream()) {
					LogManager.getLogManager().readConfiguration(is);
				}
				catch (SecurityException | IOException e) {
					throw new RuntimeException("Cannot load logging.properties file", e);
				}
		}
	}
}
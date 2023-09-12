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
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.LogManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.mokamint.application.api.Application;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.blockchain.Blockchain.BlockAddedEvent;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.nonce.Prologs;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;
import jakarta.websocket.DeploymentException;

/**
 * Tests about the synchronization of the chain from the peers.
 */
public class ChainSynchronizationTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	/**
	 * The plot used by the mining node.
	 */
	private static Plot plot;

	private static KeyPair nodeKey;

	/**
	 * The number of blocks that must be mined.
	 */
	private final static int HOW_MANY = 20;

	private volatile Semaphore miningSemaphore;
	private volatile Set<Block> miningBlocks;
	private volatile Semaphore nonMiningSemaphore;
	private volatile Set<Block> nonMiningBlocks;

	@BeforeAll
	public static void beforeAll(@TempDir Path plotDir) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
		app = mock(Application.class);
		when(app.prologExtraIsValid(any())).thenReturn(true);
		var ed25519 = SignatureAlgorithms.ed25519(Function.identity());
		nodeKey = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", nodeKey.getPublic(), ed25519.getKeyPair().getPublic(), new byte[0]);
		long start = 65536L;
		long length = 50L;
		var hashing = HashingAlgorithms.shabal256(Function.identity());

		plot = Plots.create(plotDir.resolve("plot.plot"), prolog, start, length, hashing, __ -> {});
	}

	@AfterAll
	public static void afterAll() throws IOException {
		plot.close();
	}

	@BeforeEach
	public void beforeEach() {
		miningSemaphore = new Semaphore(0);
		miningBlocks = ConcurrentHashMap.newKeySet();
		nonMiningSemaphore = new Semaphore(0);
		nonMiningBlocks = ConcurrentHashMap.newKeySet();
	}

	private Config mkConfig(Path chainDir) throws NoSuchAlgorithmException {
		return Config.Builder.defaults()
			.setDir(chainDir)
			.setChainId("octopus")
			.setTargetBlockCreationTime(300L)
			.setInitialAcceleration(1000000000000000L)
			.build();
	}

	private class MiningNode extends LocalNodeImpl {

		private MiningNode(Config config) throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
			super(config, nodeKey, app, true);

			try {
				add(LocalMiners.of(plot));
			}
			catch (ClosedNodeException e) {
				// impossible, the node is not closed at this stage!
			}
		}

		@Override
		protected void onComplete(Event event) {
			if (event instanceof BlockAddedEvent bae && bae.block.getHeight() < HOW_MANY) {
				miningBlocks.add(bae.block);
				miningSemaphore.release();
			}

			super.onComplete(event);
		}
	}

	private class NonMiningNode extends LocalNodeImpl {

		private NonMiningNode(Config config) throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
			super(config, nodeKey, app, false); // <--- does not start mining by itself
		}

		@Override
		protected void onComplete(Event event) {
			if (event instanceof BlockAddedEvent bae && bae.block.getHeight() < HOW_MANY) { // these can only come by whispering from the mining node
				nonMiningBlocks.add(bae.block);
				nonMiningSemaphore.release();
			}

			super.onComplete(event);
		}
	}

	@Test
	@DisplayName("a node without mining capacity synchronizes from its peer")
	public void nodeWithoutMinerFollowsPeer(@TempDir Path chain1, @TempDir Path chain2)
			throws URISyntaxException, NoSuchAlgorithmException, InterruptedException,
				   DatabaseException, IOException, DeploymentException, TimeoutException, PeerRejectedException, ClosedNodeException, AlreadyInitializedException {

		var port2 = 8034;
		var miningPeer = Peers.of(new URI("ws://localhost:" + port2));

		try (var nonMiningNode = new NonMiningNode(mkConfig(chain1));
			 var miningNode = new MiningNode(mkConfig(chain2)); var miningService = PublicNodeServices.open(miningNode, port2)) {

			// we give miningNode the time to mine HOW_MANY / 2 blocks
			assertTrue(miningSemaphore.tryAcquire(HOW_MANY / 2, 20, TimeUnit.SECONDS));

			// by adding miningPeer as peer of nonMiningNode, the latter will synchronize and then follow
			// the other howMany / 2 blocks by whispering
			nonMiningNode.add(miningPeer);

			assertTrue(nonMiningSemaphore.tryAcquire(HOW_MANY, 20, TimeUnit.SECONDS));
			assertTrue(miningSemaphore.tryAcquire(HOW_MANY - HOW_MANY / 2, 20, TimeUnit.SECONDS));
			assertEquals(nonMiningBlocks, miningBlocks);
		}
	}

	@Test
	@DisplayName("a node without mining capacity, once stopped and restarted, synchronizes from its peer")
	public void nodeWithoutMinerStopRestartFollowsPeer(@TempDir Path chain1, @TempDir Path chain2)
			throws URISyntaxException, NoSuchAlgorithmException, InterruptedException,
				   DatabaseException, IOException, DeploymentException, TimeoutException, PeerRejectedException, ClosedNodeException, AlreadyInitializedException {

		var port2 = 8034;
		var miningPeer = Peers.of(new URI("ws://localhost:" + port2));

		try (var miningNode = new MiningNode(mkConfig(chain2)); var miningNodeService = PublicNodeServices.open(miningNode, port2)) {
			try (var nonMiningNode = new NonMiningNode(mkConfig(chain1))) {
				// we give miningNode the time to mine HOW_MANY / 8 blocks
				assertTrue(miningSemaphore.tryAcquire(HOW_MANY / 8, 20, TimeUnit.SECONDS));

				// by adding miningNode as peer of nonMiningNode, the latter will synchronize and then follow the other blocks by whispering
				nonMiningNode.add(miningPeer);

				// we wait until nonMiningNode has received HOW_MANY / 4 blocks
				assertTrue(nonMiningSemaphore.tryAcquire(HOW_MANY / 4, 20, TimeUnit.SECONDS));

				// then we turn nonMiningNode off
			}

			// we wait until miningNode has mined HOW_MANY / 2 blocks
			assertTrue(miningSemaphore.tryAcquire(HOW_MANY / 2 - HOW_MANY / 8, 20, TimeUnit.SECONDS));

			// we turn nonMiningNode on again
			try (var nonMiningNode = new NonMiningNode(mkConfig(chain1))) {
				// we wait until nonMiningNode has received all blocks
				assertTrue(nonMiningSemaphore.tryAcquire(HOW_MANY - HOW_MANY / 4, 20, TimeUnit.SECONDS));
			}

			// we wait until miningNode has received all blocks
			assertTrue(miningSemaphore.tryAcquire(HOW_MANY - HOW_MANY / 2 - HOW_MANY / 8, 20, TimeUnit.SECONDS));

			assertEquals(nonMiningBlocks, miningBlocks);
		}
	}

	@Test
	@DisplayName("a node without mining capacity, once disconnected and reconnected, synchronizes from its peer")
	public void nodeWithoutMinerDisconnectConnectFollowsPeer(@TempDir Path chain1, @TempDir Path chain2)
			throws URISyntaxException, NoSuchAlgorithmException, InterruptedException,
				   DatabaseException, IOException, DeploymentException, TimeoutException, PeerRejectedException, ClosedNodeException, AlreadyInitializedException {

		var port2 = 8034;
		var miningPeer = Peers.of(new URI("ws://localhost:" + port2));

		try (var miningNode = new MiningNode(mkConfig(chain2)); var miningService = PublicNodeServices.open(miningNode, port2);
			 var nonMiningNode = new NonMiningNode(mkConfig(chain1))) {
				// we give miningNode the time to mine HOW_MANY / 8 blocks
				assertTrue(miningSemaphore.tryAcquire(HOW_MANY / 8, 20, TimeUnit.SECONDS));

				// by adding miningNode as peer of nonMiningNode, the latter will synchronize and then follow the other blocks by whispering
				nonMiningNode.add(miningPeer);

				// we wait until nonMiningNode has received HOW_MANY / 4 blocks
				assertTrue(nonMiningSemaphore.tryAcquire(HOW_MANY / 4, 20, TimeUnit.SECONDS));

				// then we disconnect the two peers
				nonMiningNode.remove(miningPeer);

				// we wait until miningNode has mined HOW_MANY / 2 blocks
				assertTrue(miningSemaphore.tryAcquire(HOW_MANY / 2 - HOW_MANY / 8, 20, TimeUnit.SECONDS));

				// we reconnect nonMiningNode to miningNode
				nonMiningNode.add(miningPeer);

				// we wait until nonMiningNode has received all blocks
				assertTrue(nonMiningSemaphore.tryAcquire(HOW_MANY - HOW_MANY / 4, 20, TimeUnit.SECONDS));

			// we wait until miningNode has received all blocks
			assertTrue(miningSemaphore.tryAcquire(HOW_MANY - HOW_MANY / 2 - HOW_MANY / 8, 20, TimeUnit.SECONDS));
		}

		//System.out.println("nonMiningBlocks: " + nonMiningBlocks.stream().map(Block::getHeight).sorted().map(Object::toString).collect(Collectors.joining(",")));
		//	System.out.println("miningBlocks: " + miningBlocks.stream().map(Block::getHeight).sorted().map(Object::toString).collect(Collectors.joining(",")));
		assertEquals(nonMiningBlocks, miningBlocks);
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
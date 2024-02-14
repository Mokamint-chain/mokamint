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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.Application;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.nonce.Prologs;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.PlotsAndKeyPairs;
import io.mokamint.plotter.api.Plot;
import jakarta.websocket.DeploymentException;

/**
 * Tests about the synchronization of the chain from the peers.
 */
public class ChainSynchronizationTests extends AbstractLoggedTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	/**
	 * The plot used by the mining node.
	 */
	private static Plot plot;

	/**
	 * The keys if the node.
	 */
	private static KeyPair nodeKeys;

	/**
	 * The keys of the plot file.
	 */
	private static KeyPair plotKeys;

	/**
	 * The number of blocks that must be mined.
	 */
	private final static int HOW_MANY = 20;

	private volatile Semaphore miningSemaphore;
	private volatile Set<Block> miningBlocks;
	private volatile Semaphore nonMiningSemaphore;
	private volatile Set<Block> nonMiningBlocks;

	@BeforeAll
	public static void beforeAll(@TempDir Path plotDir) throws IOException, NoSuchAlgorithmException, InvalidKeyException, TimeoutException, InterruptedException {
		app = mock(Application.class);
		when(app.checkPrologExtra(any())).thenReturn(true);
		var stateHash = new byte[] { 1, 2, 3 };
		when(app.getInitialStateId()).thenReturn(stateHash);
		when(app.endBlock(anyInt(), any())).thenReturn(stateHash);
		var ed25519 = SignatureAlgorithms.ed25519();
		nodeKeys = ed25519.getKeyPair();
		plotKeys = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeys.getPublic(), ed25519, plotKeys.getPublic(), new byte[0]);
		long start = 65536L;
		long length = 50L;

		plot = Plots.create(plotDir.resolve("plot.plot"), prolog, start, length, HashingAlgorithms.shabal256(), __ -> {});
	}

	@AfterAll
	public static void afterAll() throws IOException, InterruptedException {
		plot.close();
	}

	@BeforeEach
	public void beforeEach() {
		miningSemaphore = new Semaphore(0);
		miningBlocks = ConcurrentHashMap.newKeySet();
		nonMiningSemaphore = new Semaphore(0);
		nonMiningBlocks = ConcurrentHashMap.newKeySet();
	}

	private LocalNodeConfig mkConfig(Path chainDir) throws NoSuchAlgorithmException {
		return LocalNodeConfigBuilders.defaults()
			.setDir(chainDir)
			.setChainId("octopus")
			.setTargetBlockCreationTime(300L)
			.setInitialAcceleration(1000000000000000L)
			.build();
	}

	private class MiningNode extends LocalNodeImpl {

		private MiningNode(LocalNodeConfig config) throws IOException, DatabaseException, InterruptedException, AlreadyInitializedException, InvalidKeyException, SignatureException, TimeoutException {
			super(config, nodeKeys, app, true);

			try {
				add(LocalMiners.of(PlotsAndKeyPairs.of(plot, plotKeys)));
			}
			catch (ClosedNodeException e) {
				// impossible, the node is not closed at this stage!
			}
		}

		@Override
		protected void onAdded(Block block) {
			super.onAdded(block);

			if (block.getDescription().getHeight() < HOW_MANY) {
				miningBlocks.add(block);
				miningSemaphore.release();
			}
		}
	}

	private class NonMiningNode extends LocalNodeImpl {

		private NonMiningNode(LocalNodeConfig config) throws IOException, DatabaseException, InterruptedException, AlreadyInitializedException, InvalidKeyException, SignatureException, TimeoutException {
			super(config, nodeKeys, app, false); // <--- does not start mining by itself
		}

		@Override
		protected void onAdded(Block block) {
			super.onAdded(block);

			if (block.getDescription().getHeight() < HOW_MANY) { // these can only come by whispering from the mining node
				nonMiningBlocks.add(block);
				nonMiningSemaphore.release();
			}
		}
	}

	@Test
	@DisplayName("a node without mining capacity synchronizes from its peer")
	public void nodeWithoutMinerFollowsPeer(@TempDir Path chain1, @TempDir Path chain2)
			throws URISyntaxException, NoSuchAlgorithmException, InterruptedException,
				   DatabaseException, IOException, DeploymentException, TimeoutException, PeerRejectedException, ClosedNodeException, AlreadyInitializedException, InvalidKeyException, SignatureException, NodeException {

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
				   DatabaseException, IOException, DeploymentException, TimeoutException, PeerRejectedException, ClosedNodeException, AlreadyInitializedException, InvalidKeyException, SignatureException, NodeException {

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
				   DatabaseException, IOException, DeploymentException, TimeoutException, PeerRejectedException, ClosedNodeException, AlreadyInitializedException, InvalidKeyException, SignatureException, NodeException {

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
}
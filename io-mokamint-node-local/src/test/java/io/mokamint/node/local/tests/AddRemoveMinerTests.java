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

import java.net.URI;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.Application;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.miner.service.MinerServices;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.local.AbstractLocalNode;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.nonce.Prologs;
import io.mokamint.plotter.PlotAndKeyPairs;
import io.mokamint.plotter.Plots;

/**
 * Tests about the addition and removal of a miner from a network of nodes.
 */
public class AddRemoveMinerTests extends AbstractLoggedTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	/**
	 * The key of the first node.
	 */
	private static KeyPair node1Keys;

	/**
	 * The key of the second node.
	 */
	private static KeyPair node2Keys;

	/**
	 * The key of the miner.
	 */
	private static KeyPair plotKeys;

	@BeforeAll
	public static void beforeAll() throws Exception {
		app = mock(Application.class);
		when(app.checkPrologExtra(any())).thenReturn(true);
		var stateHash = new byte[] { 1, 2, 3 };
		when(app.getInitialStateId()).thenReturn(stateHash);
		when(app.endBlock(anyInt(), any())).thenReturn(stateHash);
		var id25519 = SignatureAlgorithms.ed25519();
		node1Keys = id25519.getKeyPair();
		node2Keys = id25519.getKeyPair();
		plotKeys = id25519.getKeyPair();
	}

	@Test
	@DisplayName("the addition of a miner to a network of nodes lets them start mining, its removal stops mining")
	public void addMinerStartsMiningThenRemovalStopsMining(@TempDir Path chain1, @TempDir Path chain2) throws Exception {
		var port1 = 8030;
		var port2 = 8032;
		var peer1 = Peers.of(URI.create("ws://localhost:" + port1));
		var peer2 = Peers.of(URI.create("ws://localhost:" + port2));
		var config1 = LocalNodeConfigBuilders.defaults()
				.setDir(chain1)
				.setTargetBlockCreationTime(500)
				.setSynchronizationInterval(5000) // to guarantee a quick synchronization
				.build();
		var config2 = config1.toBuilder().setDir(chain2).build();
		var miningPort = 8025;

		// the prolog of the plot file must be compatible with node1 (same key and same chain id)
		var ed25519 = SignatureAlgorithms.ed25519();
		var prolog = Prologs.of(config1.getChainId(), ed25519, node1Keys.getPublic(), ed25519, plotKeys.getPublic(), new byte[0]);

		var node1CannotMine = new Semaphore(0);
		var node2HasConnectedToNode1 = new Semaphore(0);
		var node2HasAddedBlock = new Semaphore(0);

		class MyLocalNode1 extends AbstractLocalNode {

			private MyLocalNode1() throws InterruptedException {
				super(config1, node1Keys, app, true);
			}

			protected void onNoDeadlineFound(Block previous) {
				super.onNoDeadlineFound(previous);
				node1CannotMine.release();
			}

			protected void onNoMinersAvailable() {
				super.onNoMinersAvailable();
				node1CannotMine.release();
			}
		}

		class MyLocalNode2 extends AbstractLocalNode {

			private MyLocalNode2() throws InterruptedException {
				super(config2, node2Keys, app, false);
			}

			@Override
			protected void onConnected(Peer peer) {
				super.onConnected(peer);
				if (peer.equals(peer1))
					node2HasConnectedToNode1.release();
			}

			@Override
			protected void onAdded(Block block) {
				super.onAdded(block);
				node2HasAddedBlock.release();
			}
		}

		try (var node1 = new MyLocalNode1(); var node2 = new MyLocalNode2();
			 var service1 = PublicNodeServices.open(node1, port1, 10000, config1.getWhisperingMemorySize(), Optional.of(peer1.getURI()));
             var service2 = PublicNodeServices.open(node2, port2, 10000, config2.getWhisperingMemorySize(), Optional.of(peer2.getURI()));
			 var plot = Plots.create(chain1.resolve("small.plot"), prolog, 1000, 500, config1.getHashingForDeadlines(), __ -> {});
			 var miner = LocalMiners.of(PlotAndKeyPairs.of(plot, plotKeys))) {

			// without any miner, eventually node1 will realize that it cannot mine
			assertTrue(node1CannotMine.tryAcquire(1, 20, TimeUnit.SECONDS));

			// we connect node1 and node2 with each other
			assertTrue(node1.add(peer2).isPresent());

			// we wait until node2 has added node1 as peer
			assertTrue(node2HasConnectedToNode1.tryAcquire(1, 2000, TimeUnit.MILLISECONDS));

			// we open a remote miner on node1
			Optional<MinerInfo> infoOfNewMiner = node1.openMiner(miningPort);
			assertTrue(infoOfNewMiner.isPresent());

			// we get the UUID of the only miner of the node
			var uuid = infoOfNewMiner.get().getUUID();

			// we connect the local miner to the mining service of node1
			try (var service = MinerServices.of(miner, URI.create("ws://localhost:" + miningPort), 30_000)) {
				// miner works for node1, which whispers block to node2: eventually node2 will receive 5 blocks
				assertTrue(node2HasAddedBlock.tryAcquire(5, 1, TimeUnit.MINUTES));
				node1.removeMiner(uuid);
			}

			// we wait until node1 stops mining, since it has no more miners
			assertTrue(node1CannotMine.tryAcquire(1, 20, TimeUnit.SECONDS));

			// typically, node1 could have added 5 blocks only, but it might happen that
			// more blocks are added before closing the miner above: better ask node1 then
			// and wait until any extra block has reached node2 as well
			var node1ChainInfo = node1.getChainInfo();
			assertTrue(node2HasAddedBlock.tryAcquire((int) (node1ChainInfo.getLength() - 5), 20, TimeUnit.SECONDS));

			// both chain should coincide now
			assertEquals(node1ChainInfo, node2.getChainInfo());
		}
	}
}
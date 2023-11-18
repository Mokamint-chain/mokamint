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
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.api.TaskInfo;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.nonce.Prologs;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.PlotsAndKeyPairs;
import jakarta.websocket.DeploymentException;

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
	public static void beforeAll() throws NoSuchAlgorithmException, InvalidKeyException {
		app = mock(Application.class);
		when(app.checkPrologExtra(any())).thenReturn(true);
		var id25519 = SignatureAlgorithms.ed25519();
		node1Keys = id25519.getKeyPair();
		node2Keys = id25519.getKeyPair();
		plotKeys = id25519.getKeyPair();
	}

	@Test
	@DisplayName("the addition of a miner to a network of nodes lets them start mining, its removal stops mining")
	public void addMinerStartsMiningThenRemovalStopsMining(@TempDir Path chain1, @TempDir Path chain2)
			throws URISyntaxException, NoSuchAlgorithmException, InterruptedException,
				   DatabaseException, IOException, DeploymentException, TimeoutException, PeerRejectedException, ClosedNodeException, AlreadyInitializedException, InvalidKeyException, SignatureException {

		var port1 = 8030;
		var port2 = 8032;
		var peer1 = Peers.of(new URI("ws://localhost:" + port1));
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));
		var config1 = LocalNodeConfigBuilders.defaults().setDir(chain1).setInitialAcceleration(50000000000000L).setTargetBlockCreationTime(500).build();
		var config2 = config1.toBuilder().setDir(chain2).build();
		var miningPort = 8025;

		// the prolog of the plot file must be compatible with node1 (same key and same chain id)
		var ed25519 = SignatureAlgorithms.ed25519();
		var prolog = Prologs.of(config1.getChainId(), ed25519, node1Keys.getPublic(), ed25519, plotKeys.getPublic(), new byte[0]);

		var node1CannotMine = new Semaphore(0);
		var node2HasConnectedToNode1 = new Semaphore(0);
		var node2HasAddedBlock = new Semaphore(0);

		class MyLocalNode1 extends LocalNodeImpl {

			private MyLocalNode1() throws IOException, DatabaseException, InterruptedException, AlreadyInitializedException, InvalidKeyException, SignatureException {
				super(config1, node1Keys, app, true);
			}

			protected void onNoDeadlineFound(io.mokamint.node.api.Block previous) {
				super.onNoDeadlineFound(previous);
				node1CannotMine.release();
			}

			protected void onNoMinersAvailable() {
				super.onNoMinersAvailable();
				node1CannotMine.release();
			}
		}

		class MyLocalNode2 extends LocalNodeImpl {

			private MyLocalNode2() throws IOException, DatabaseException, InterruptedException, AlreadyInitializedException, InvalidKeyException, SignatureException {
				super(config2, node2Keys, app, false);
			}

			@Override
			protected void onPeerConnected(Peer peer) {
				super.onPeerConnected(peer);
				if (peer.equals(peer1))
					node2HasConnectedToNode1.release();
			}

			@Override
			protected void onBlockAdded(Block block) {
				super.onBlockAdded(block);
				node2HasAddedBlock.release();
			}
		}

		try (var node1 = new MyLocalNode1(); var node2 = new MyLocalNode2();
			 var service1 = PublicNodeServices.open(node1, port1, 10000, node1.getConfig().getWhisperingMemorySize(), Optional.of(peer1.getURI()));
             var service2 = PublicNodeServices.open(node2, port2, 10000, node2.getConfig().getWhisperingMemorySize(), Optional.of(peer2.getURI()));
			 var plot = Plots.create(chain1.resolve("small.plot"), prolog, 1000, 500, config1.getHashingForDeadlines(), __ -> {});
			 var miner = LocalMiners.of(PlotsAndKeyPairs.of(plot, plotKeys))) {

			// without any miner, eventually node1 will realize that it cannot mine
			assertTrue(node1CannotMine.tryAcquire(1, 20, TimeUnit.SECONDS));

			// we check that node1 and node2 are not mining at this stage
			assertTrue(node1.getTaskInfos().map(TaskInfo::getDescription).noneMatch(description -> description.contains("mining")));
			assertTrue(node2.getTaskInfos().map(TaskInfo::getDescription).noneMatch(description -> description.contains("mining")));

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
			try (var service = MinerServices.open(miner, new URI("ws://localhost:" + miningPort))) {
				// miner works for node1, which whispers block to node2: eventually node2 will receive 5 blocks
				assertTrue(node2HasAddedBlock.tryAcquire(5, 1, TimeUnit.MINUTES));
				node1.closeMiner(uuid);
			}

			// we wait until node1 stops mining, since it has no more miners
			assertTrue(node1CannotMine.tryAcquire(1, 20, TimeUnit.SECONDS));

			// typically, node1 could have added 5 blocks only, but it might happen that
			// more blocks are added before closing the miner above: better ask node1 then
			// and wait until any extra block has reached node2 as well
			var node1ChainInfo = node1.getChainInfo();
			assertTrue(node2HasAddedBlock.tryAcquire((int) (node1ChainInfo.getLength() - 5), 20, TimeUnit.SECONDS));

			// both chain should coincide now
			var node2ChainInfo = node2.getChainInfo();

			assertEquals(node1ChainInfo, node2ChainInfo);
		}
	}
}
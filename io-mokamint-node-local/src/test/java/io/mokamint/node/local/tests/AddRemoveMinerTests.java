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
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

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
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.NodePeers.PeerConnectedEvent;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.nonce.Prologs;
import io.mokamint.plotter.Plots;
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
	private static KeyPair nodeKey1;

	/**
	 * The key of the second node.
	 */
	private static KeyPair nodeKey2;

	/**
	 * The key of the miner.
	 */
	private static KeyPair minerKey;

	@BeforeAll
	public static void beforeAll() throws NoSuchAlgorithmException, InvalidKeyException {
		app = mock(Application.class);
		when(app.prologExtraIsValid(any())).thenReturn(true);
		var id25519 = SignatureAlgorithms.ed25519(Function.identity());
		nodeKey1 = id25519.getKeyPair();
		nodeKey2 = id25519.getKeyPair();
		minerKey = id25519.getKeyPair();
	}

	@Test
	@DisplayName("the addition of a miner to a network of nodes lets them start mining, its removal stops mining")
	//@Timeout(10)
	public void addMinerStartsMiningThenRemovalStopsMining(@TempDir Path chain1, @TempDir Path chain2)
			throws URISyntaxException, NoSuchAlgorithmException, InterruptedException,
				   DatabaseException, IOException, DeploymentException, TimeoutException, PeerRejectedException, ClosedNodeException, AlreadyInitializedException, InvalidKeyException {

		var port1 = 8030;
		var port2 = 8032;
		var peer1 = Peers.of(new URI("ws://localhost:" + port1));
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));
		var config1 = LocalNodeConfigBuilders.defaults().setDir(chain1).build();
		var config2 = LocalNodeConfigBuilders.defaults().setDir(chain2).build();
		var miningPort = 8025;

		// the prolog of the plot file must be compatible with node1 (same key and same chain id)
		var prolog = Prologs.of(config1.getChainId(), nodeKey1.getPublic(), minerKey.getPublic(), new byte[0]);

		var node2HasConnectedToNode1 = new Semaphore(0);

		class MyLocalNode1 extends LocalNodeImpl {

			private MyLocalNode1() throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(config1, nodeKey1, app, true);
			}

			@Override
			protected void onComplete(Event event) {
				//if (event instanceof PeerDisconnectedEvent pde && pde.getPeer().equals(peer2))
					//semaphore.release();
			}
		}

		class MyLocalNode2 extends LocalNodeImpl {

			private MyLocalNode2() throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(config2, nodeKey2, app, false);
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof PeerConnectedEvent pce && pce.getPeer().equals(peer1))
					node2HasConnectedToNode1.release();
			}
		}

		try (var node1 = new MyLocalNode1(); var node2 = new MyLocalNode2();
			 var service1 = PublicNodeServices.open(node1, port1, 10000, node1.getConfig().getWhisperingMemorySize(), Optional.of(peer1.getURI()));
             var service2 = PublicNodeServices.open(node2, port2, 10000, node2.getConfig().getWhisperingMemorySize(), Optional.of(peer2.getURI()));
			 var plot = Plots.create(chain1.resolve("small.plot"), prolog, 1000, 500, config1.getHashingForDeadlines(), __ -> {});
			 var miner = LocalMiners.of(plot)) {

			// we connect node1 and node2 with each other
			assertTrue(node1.add(peer2));

			// we wait until node2 has added node1 as peer
			assertTrue(node2HasConnectedToNode1.tryAcquire(1, 1000, TimeUnit.MILLISECONDS));

			// we open a remote miner on node1
			assertTrue(node1.openMiner(miningPort));

			// TODO: it would be great to check that node1 and node2 are not mining at this stage

			// we connect the local miner to the mining service of node1
			try (var service = MinerServices.open(miner, new URI("ws://localhost:" + miningPort))) {
			}
		}
	}
}
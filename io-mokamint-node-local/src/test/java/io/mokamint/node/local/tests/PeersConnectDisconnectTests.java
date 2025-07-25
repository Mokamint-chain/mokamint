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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.Application;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.local.AbstractLocalNode;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.service.PublicNodeServices;

/**
 * Tests about the connection and disconnection of peers in a network of nodes.
 */
public class PeersConnectDisconnectTests extends AbstractLoggedTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	/**
	 * The key of the node.
	 */
	private static KeyPair nodeKey;

	@BeforeAll
	public static void beforeAll() throws Exception {
		app = mock(Application.class);
		when(app.checkPrologExtra(any())).thenReturn(true);
		var stateHash = new byte[] { 1, 2, 4 };
		when(app.endBlock(anyInt(), any())).thenReturn(stateHash);
		nodeKey = SignatureAlgorithms.ed25519().getKeyPair();
	}

	@Test
	@DisplayName("if a peer disconnects, its remote gets removed from the peers table")
	@Timeout(15)
	public void ifPeerDisconnectsThenRemoteRemoved(@TempDir Path chain1, @TempDir Path chain2, @TempDir Path chain3) throws Exception {
		var port2 = 8032;
		var port3 = 8034;
		var uri2 = URI.create("ws://localhost:" + port2);
		var peer2 = Peers.of(uri2);
		var uri3 = URI.create("ws://localhost:" + port3);
		var peer3 = Peers.of(uri3);
		var config1 = LocalNodeConfigBuilders.defaults().setDir(chain1).build();
		var config2 = LocalNodeConfigBuilders.defaults().setDir(chain2).build();
		var config3 = LocalNodeConfigBuilders.defaults().setDir(chain3).build();

		var disconnectionSemaphore = new Semaphore(0);

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode(LocalNodeConfig config) throws InterruptedException {
				super(config, nodeKey, app, false);
			}

			@Override
			protected void onDisconnected(Peer peer) {
				super.onDisconnected(peer);
				if (peer.equals(peer2))
					disconnectionSemaphore.release();
			}
		}

		try (var node1 = new MyLocalNode(config1); var node2 = LocalNodes.of(config2, nodeKey, app, false);  var node3 = LocalNodes.of(config3, nodeKey, app, false);
			 var service2 = PublicNodeServices.open(node2, port2, 1800000, 1000, Optional.of(uri2));
			 var service3 = PublicNodeServices.open(node3, port3, 1800000, 1000, Optional.of(uri3))) {

			// node1 has peer2 and peer3 as peers
			assertTrue(node1.add(peer2).isPresent());
			assertTrue(node1.add(peer3).isPresent());

			// at this point, node1 has both its peers connected
			assertTrue(node1.getPeerInfos().allMatch(PeerInfo::isConnected));

			// peer2 gets closed and disconnects
			node2.close();

			assertTrue(disconnectionSemaphore.tryAcquire(1, 2, TimeUnit.SECONDS));

			// at this point, the peers are always the same, but peer2 is disconnected
			assertEquals(2L, node1.getPeerInfos().count());
			assertTrue(node1.getPeerInfos().anyMatch(info -> info.isConnected() && peer3.equals(info.getPeer())));
			assertTrue(node1.getPeerInfos().anyMatch(info -> !info.isConnected() && peer2.equals(info.getPeer())));
		}
	}

	@Test
	@DisplayName("if a peer disconnects and reconnects, its network is reconstructed")
	public void ifPeerDisconnectsThenConnectsItIsBackInNetwork(@TempDir Path chain1, @TempDir Path chain2) throws Exception {
		var port1 = 8030;
		var port2 = 8032;
		var uri1 = URI.create("ws://localhost:" + port1);
		var uri2 = URI.create("ws://localhost:" + port2);
		var peer1 = Peers.of(uri1);
		var peer2 = Peers.of(uri2);
		var config1 = LocalNodeConfigBuilders.defaults()
				.setDir(chain1)
				.build();
		var config2 = LocalNodeConfigBuilders.defaults()
				.setDir(chain2)
				// this test requires to broadcast the services quickly,
				// or otherwise it would last too much
				.setServiceBroadcastInterval(2000)
				.build();

		var connections = new Semaphore(0);
		var disconnections = new Semaphore(0);
		var reconnections = new Semaphore(0);
		var phase = new AtomicInteger(0);

		class MyLocalNode1 extends AbstractLocalNode {

			private MyLocalNode1(LocalNodeConfig config) throws InterruptedException {
				super(config, nodeKey, app, false);
			}

			@Override
			protected void onAdded(Peer peer) {
				super.onAdded(peer);
				if (phase.get() == 1 && peer.equals(peer2))
					connections.release();
			}

			@Override
			protected void onConnected(Peer peer) {
				super.onConnected(peer);
				if (phase.get() == 3 && peer.equals(peer2))
					reconnections.release();
			}

			@Override
			protected void onDisconnected(Peer peer) {
				super.onDisconnected(peer);
				if (phase.get() == 2 && peer.equals(peer2))
					disconnections.release();
			}
		}

		class MyLocalNode2 extends AbstractLocalNode {

			private MyLocalNode2(LocalNodeConfig config) throws InterruptedException {
				super(config, nodeKey, app, false);
			}

			@Override
			protected void onAdded(Peer peer) {
				super.onAdded(peer);
				if (phase.get() == 1 && peer.equals(peer1))
					connections.release();
			}

			@Override
			protected void onConnected(Peer peer) {
				super.onConnected(peer);
				if (phase.get() == 3 && peer.equals(peer1))
					reconnections.release();
			}
		}

		phase.set(1);

		try (var node1 = new MyLocalNode1(config1); var service1 = PublicNodeServices.open(node1, port1, 500, 1000, Optional.of(uri1))) {

			try (var node2 = new MyLocalNode2(config2); var service2 = PublicNodeServices.open(node2, port2, 500, 1000, Optional.of(uri2))) {
				// node1 has node2 as peer
				node1.add(peer2);

				// eventually, both know each other
				assertTrue(connections.tryAcquire(2, 5, TimeUnit.SECONDS));

				// at this point, node1 is connected to node2 and vice versa
				assertTrue(node1.getPeerInfos().anyMatch(info -> info.isConnected() && info.getPeer().equals(peer2)));
				assertTrue(node2.getPeerInfos().anyMatch(info -> info.isConnected() && info.getPeer().equals(peer1)));

				phase.set(2);

				// node2 gets closed and disconnects
			}

			// eventually, node1 sees node2 disconnected
			assertTrue(disconnections.tryAcquire(1, 3, TimeUnit.SECONDS));

			// at this point, node1 has still node2 as peer but marked as disconnected
			assertTrue(node1.getPeerInfos().anyMatch(info -> !info.isConnected() && info.getPeer().equals(peer2)));

			phase.set(3);

			// node2 resurrects
			try (var node2 = new MyLocalNode2(config2); var service2 = PublicNodeServices.open(node2, port2, 500, 1000, Optional.of(uri2))) {
				// eventually, both know each other again
				assertTrue(reconnections.tryAcquire(2, 5, TimeUnit.SECONDS));

				// at this point, node1 is connected to node2 and vice versa
				assertTrue(node1.getPeerInfos().anyMatch(info -> info.isConnected() && info.getPeer().equals(peer2)));
				assertTrue(node2.getPeerInfos().anyMatch(info -> info.isConnected() && info.getPeer().equals(peer1)));
			}
		}
	}
}
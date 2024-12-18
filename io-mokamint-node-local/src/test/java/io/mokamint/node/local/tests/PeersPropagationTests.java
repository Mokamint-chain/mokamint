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
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.node.Peers;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.local.AbstractLocalNode;
import io.mokamint.node.local.ApplicationTimeoutException;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.service.PublicNodeServices;
import jakarta.websocket.DeploymentException;

/**
 * Tests about the propagation of the peers in a network of nodes.
 */
public class PeersPropagationTests extends AbstractLoggedTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	/**
	 * The key of the node.
	 */
	private static KeyPair nodeKey;

	@BeforeAll
	public static void beforeAll() throws NoSuchAlgorithmException, InvalidKeyException, TimeoutException, InterruptedException, ApplicationException {
		app = mock(Application.class);
		when(app.checkPrologExtra(any())).thenReturn(true);
		nodeKey = SignatureAlgorithms.ed25519().getKeyPair();
	}

	@Test
	@DisplayName("a peer added to a clique is broadcast to all nodes in the clique")
	@Timeout(20)
	public void peerAddedToCliqueIsBroadcast(@TempDir Path chain1, @TempDir Path chain2, @TempDir Path chain3, @TempDir Path chain4)
			throws NoSuchAlgorithmException, InterruptedException, IOException, DeploymentException, ApplicationTimeoutException, PeerRejectedException, NodeException, TimeoutException {

		var port1 = 8032;
		var port2 = 8034;
		var port3 = 8036;
		var port4 = 8038;
		var peer1 = Peers.of(URI.create("ws://localhost:" + port1));
		var peer2 = Peers.of(URI.create("ws://localhost:" + port2));
		var peer3 = Peers.of(URI.create("ws://localhost:" + port3));
		var peer4 = Peers.of(URI.create("ws://localhost:" + port4));
		var config1 = LocalNodeConfigBuilders.defaults().setDir(chain1).build();
		var config2 = LocalNodeConfigBuilders.defaults().setDir(chain2).build();
		var config3 = LocalNodeConfigBuilders.defaults().setDir(chain3).build();
		var config4 = LocalNodeConfigBuilders.defaults().setDir(chain4).build();

		var semaphore = new Semaphore(0);

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode(LocalNodeConfig config) throws InterruptedException, NodeException, ApplicationTimeoutException {
				super(config, nodeKey, app, false);
			}

			@Override
			protected void onAdded(Peer peer) {
				super.onAdded(peer);
				if (peer4.equals(peer))
					semaphore.release();
			}
		}

		try (var node1 = new MyLocalNode(config1); var node2 = new MyLocalNode(config2);
			 var node3 = new MyLocalNode(config3); var node4 = new MyLocalNode(config4);
			 var service1 = PublicNodeServices.open(node1, port1, 1800000, 1000, Optional.of(peer1.getURI()));
			 var service2 = PublicNodeServices.open(node2, port2, 1800000, 1000, Optional.of(peer2.getURI()));
			 var service3 = PublicNodeServices.open(node3, port3, 1800000, 1000, Optional.of(peer3.getURI()));
			 var service4 = PublicNodeServices.open(node4, port4, 1800000, 1000, Optional.of(peer4.getURI()))) {

			node1.add(peer2);
			node2.add(peer3);
			node3.add(peer1);

			// at this point, each node is a peer of the next in the sequence
			// (there might also be views of the same peer through its local IP address)
			assertTrue(node1.getPeerInfos().map(PeerInfo::getPeer).anyMatch(peer2::equals));
			assertTrue(node2.getPeerInfos().map(PeerInfo::getPeer).anyMatch(peer3::equals));
			assertTrue(node3.getPeerInfos().map(PeerInfo::getPeer).anyMatch(peer1::equals));

			// we add peer4 as peer of peer1 now
			node1.add(peer4);

			// we wait for three events of addition of peer4 as peer
			assertTrue(semaphore.tryAcquire(3, 5, TimeUnit.SECONDS));

			// peer4 is a peer of node1, node2 and node3 now
			assertTrue(node1.getPeerInfos().map(PeerInfo::getPeer).anyMatch(peer4::equals));
			assertTrue(node2.getPeerInfos().map(PeerInfo::getPeer).anyMatch(peer4::equals));
			assertTrue(node3.getPeerInfos().map(PeerInfo::getPeer).anyMatch(peer4::equals));
		}
	}

	@Test
	@DisplayName("a peer added to a node eventually propagates all its peers")
	@Timeout(20)
	public void peerAddedToNodePropagatesItsPeers(@TempDir Path chain1, @TempDir Path chain2, @TempDir Path chain3, @TempDir Path chain4)
			throws NoSuchAlgorithmException, InterruptedException, IOException, DeploymentException, ApplicationTimeoutException, PeerRejectedException, NodeException, TimeoutException {

		var port1 = 8032;
		var port2 = 8034;
		var port3 = 8036;
		var peer1 = Peers.of(URI.create("ws://localhost:" + port1));
		var peer2 = Peers.of(URI.create("ws://localhost:" + port2));
		var peer3 = Peers.of(URI.create("ws://localhost:" + port3));
		var allPeers = Set.of(peer1, peer2, peer3);
		Set<Peer> stillToRemove = ConcurrentHashMap.newKeySet();
		stillToRemove.addAll(allPeers);
		var config1 = LocalNodeConfigBuilders.defaults().setDir(chain1).build();
		var config2 = LocalNodeConfigBuilders.defaults().setDir(chain2).build();
		var config3 = LocalNodeConfigBuilders.defaults().setDir(chain3).build();
		var config4 = LocalNodeConfigBuilders.defaults().setDir(chain4)
			.setPeerPingInterval(2000) // we must make peer propagation fast
			.build();

		var semaphore = new Semaphore(0);

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode(LocalNodeConfig config) throws InterruptedException, NodeException, ApplicationTimeoutException {
				super(config, nodeKey, app, false);
			}

			@Override
			protected void onAdded(Peer peer) {
				super.onAdded(peer);
				stillToRemove.remove(peer);
				if (stillToRemove.isEmpty())
					semaphore.release();
			}
		}

		try (var node1 = LocalNodes.of(config1, nodeKey, app, false); var node2 = LocalNodes.of(config2, nodeKey, app, false);
			 var node3 = LocalNodes.of(config3, nodeKey, app, false); var node4 = new MyLocalNode(config4);
			 var service1 = PublicNodeServices.open(node1, port1, 1800000, 1000, Optional.of(peer1.getURI()));
			 var service2 = PublicNodeServices.open(node2, port2, 1800000, 1000, Optional.of(peer2.getURI()));
			 var service3 = PublicNodeServices.open(node3, port3, 1800000, 1000, Optional.of(peer3.getURI()))) {

			// node1 has peer2 and peer3 as peers
			node1.add(peer2);
			node1.add(peer3);

			// at this point, node4 has still no peers
			assertTrue(node4.getPeerInfos().count() == 0L);

			// we add peer1 as peer of node4 now
			node4.add(peer1);

			// we wait until peer1, peer2 and peer3 get propagated to node4
			assertTrue(semaphore.tryAcquire(1, 10, TimeUnit.SECONDS));
			assertEquals(allPeers, node4.getPeerInfos().map(PeerInfo::getPeer).collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("if a peer adds another peer, eventually to end up being a peer of each other")
	public void ifPeerAddsPeerThenTheyKnowEachOther(@TempDir Path chain1, @TempDir Path chain2)
			throws NoSuchAlgorithmException, InterruptedException, IOException, DeploymentException, ApplicationTimeoutException, PeerRejectedException, NodeException, TimeoutException {

		var port1 = 8032;
		var port2 = 8034;
		var uri1 = URI.create("ws://localhost:" + port1);
		var uri2 = URI.create("ws://localhost:" + port2);
		var peer1 = Peers.of(uri1);
		var peer2 = Peers.of(uri2);
		var config1 = LocalNodeConfigBuilders.defaults().setDir(chain1).build();
		var config2 = LocalNodeConfigBuilders.defaults().setDir(chain2).build();
		var semaphore = new Semaphore(0);

		class MyLocalNode extends AbstractLocalNode {
			private final Peer expected;

			private MyLocalNode(LocalNodeConfig config, Peer expected) throws InterruptedException, NodeException, ApplicationTimeoutException {
				super(config, nodeKey, app, false);
				
				this.expected = expected;
			}

			@Override
			protected void onAdded(Peer peer) {
				super.onAdded(peer);
				if (expected.equals(peer))
					semaphore.release();
			}
		}

		try (var node1 = new MyLocalNode(config1, peer2); var node2 = new MyLocalNode(config2, peer1);
			 var service1 = PublicNodeServices.open(node1, port1, 100, 1000, Optional.of(uri1));
			 var service2 = PublicNodeServices.open(node2, port2, 100, 1000, Optional.of(uri2))) {

			node1.add(peer2);

			assertTrue(semaphore.tryAcquire(2, 4, TimeUnit.SECONDS));
		}
	}
}
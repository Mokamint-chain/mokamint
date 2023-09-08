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
import java.net.URL;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.LogManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import io.mokamint.application.api.Application;
import io.mokamint.node.Peers;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.NodePeers.PeerConnectedEvent;
import io.mokamint.node.local.internal.NodePeers.PeerDisconnectedEvent;
import io.mokamint.node.service.PublicNodeServices;
import jakarta.websocket.DeploymentException;

/**
 * Tests about the connection and disconnection of peers in a network of nodes.
 */
public class PeersConnectDisconnectTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	@BeforeAll
	public static void beforeAll() {
		app = mock(Application.class);
		when(app.prologExtraIsValid(any())).thenReturn(true);
	}

	@Test
	@DisplayName("if a peer disconnects, its remote gets removed from the peers table")
	@Timeout(10)
	public void ifPeerDisconnectsThenRemoteRemoved(@TempDir Path chain1, @TempDir Path chain2, @TempDir Path chain3)
			throws URISyntaxException, NoSuchAlgorithmException, InterruptedException,
				   DatabaseException, IOException, DeploymentException, TimeoutException, PeerRejectedException, ClosedNodeException, AlreadyInitializedException {

		var port2 = 8032;
		var port3 = 8034;
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));
		var peer3 = Peers.of(new URI("ws://localhost:" + port3));
		var config1 = Config.Builder.defaults().setDir(chain1).build();
		var config2 = Config.Builder.defaults().setDir(chain2).build();
		var config3 = Config.Builder.defaults().setDir(chain3).build();

		var semaphore = new Semaphore(0);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode(Config config) throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(config, app, false);
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof PeerDisconnectedEvent pde && pde.getPeer().equals(peer2))
					semaphore.release();
			}
		}

		try (var node1 = new MyLocalNode(config1); var node2 = LocalNodes.of(config2, app, false);  var node3 = LocalNodes.of(config3, app, false);
			 var service2 = PublicNodeServices.open(node2, port2); var service3 = PublicNodeServices.open(node3, port3)) {

			// node1 has peer2 and peer3 as peers
			node1.add(peer2);
			node1.add(peer3);

			// at this point, node1 has both its peers connected
			assertTrue(node1.getPeerInfos().allMatch(PeerInfo::isConnected));
			assertTrue(node1.getPeerInfos().map(PeerInfo::getPeer).allMatch(Predicate.isEqual(peer2).or(Predicate.isEqual(peer3))));

			// peer2 gets closed and disconnects
			node2.close();

			assertTrue(semaphore.tryAcquire(1, 2, TimeUnit.SECONDS));

			// at this point, the peers are always the same, but peer2 is disconnected
			assertTrue(node1.getPeerInfos().count() == 2);
			assertTrue(node1.getPeerInfos().anyMatch(info -> info.isConnected() && info.getPeer().equals(peer3)));
			assertTrue(node1.getPeerInfos().anyMatch(info -> !info.isConnected() && info.getPeer().equals(peer2)));
		}
	}

	@Test
	@DisplayName("if a peer disconnects and reconnects, its network is reconstructed")
	public void ifPeerDisconnectsThenConnectsItIsBackInNetwork(@TempDir Path chain1, @TempDir Path chain2)
			throws URISyntaxException, NoSuchAlgorithmException, InterruptedException,
				   DatabaseException, IOException, DeploymentException, TimeoutException, PeerRejectedException, ClosedNodeException, AlreadyInitializedException {

		var port1 = 8030;
		var port2 = 8032;
		var uri1 = new URI("ws://localhost:" + port1);
		var uri2 = new URI("ws://localhost:" + port2);
		var peer1 = Peers.of(uri1);
		var peer2 = Peers.of(uri2);
		var config1 = Config.Builder.defaults()
				.setDir(chain1)
				.build();
		var config2 = Config.Builder.defaults()
				.setDir(chain2)
				.build();

		var connections = new Semaphore(0);
		var disconnections = new Semaphore(0);
		var reconnections = new Semaphore(0);
		var phase = new AtomicInteger(0);

		class MyLocalNode1 extends LocalNodeImpl {

			private MyLocalNode1(Config config) throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(config, app, false);
			}

			@Override
			protected void onComplete(Event event) {
				if (phase.get() == 1 && event instanceof PeerConnectedEvent pce && pce.getPeer().equals(peer2))
					connections.release();

				if (phase.get() == 2 && event instanceof PeerDisconnectedEvent pde && pde.getPeer().equals(peer2))
					disconnections.release();

				if (phase.get() == 3 && event instanceof PeerConnectedEvent pce && pce.getPeer().equals(peer2))
					reconnections.release();
			}
		}

		class MyLocalNode2 extends LocalNodeImpl {

			private MyLocalNode2(Config config) throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(config, app, false);
			}

			@Override
			protected void onComplete(Event event) {
				if (phase.get() == 1 && event instanceof PeerConnectedEvent pce && pce.getPeer().equals(peer1))
					connections.release();

				if (phase.get() == 3 && event instanceof PeerConnectedEvent pce && pce.getPeer().equals(peer1))
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
			assertTrue(node1.getPeerInfos().anyMatch(info -> !info.isConnected() && info.getPeer().equals(peer2) && info.getPoints() < config1.peerInitialPoints));

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

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = PeersConnectDisconnectTests.class.getClassLoader().getResource("logging.properties");
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
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
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.application.api.Application;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.Versions;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.Version;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.messages.GetInfoMessage;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.service.AbstractPublicNodeService;
import io.mokamint.node.service.PublicNodeServices;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

/**
 * Tests about the propagation of the peers in a network of nodes.
 */
public class PeerPropagationTests {

	/**
	 * The node information of the nodes used in the tests.
	 */
	private static NodeInfo info = NodeInfos.of(mkVersion(), UUID.randomUUID());

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	/**
	 * Extracts the version of Mokamint from the pom.xml file.
	 * 
	 * @return the version
	 */
	private static Version mkVersion() {
		// reads the version from the property in the Maven pom.xml
		try {
			return Versions.current();
		}
		catch (IOException e) {
			throw new RuntimeException("Cannot load the maven.properties file", e);
		}
	}

	/**
	 * Test server implementation.
	 */
	@ThreadSafe
	private static class PublicTestServer extends AbstractPublicNodeService {

		/**
		 * Creates a new test server.
		 * 
		 * @param port the port where the server is published
		 * @throws DeploymentException if the service cannot be deployed
		 * @throws IOException if an I/O error occurs
		 */
		private PublicTestServer(int port) throws DeploymentException, IOException {
			super(port);
			deploy();
		}

		@Override
		protected void onGetInfo(GetInfoMessage message, Session session) {
			super.onGetInfo(message, session);
			sendObjectAsync(session, GetInfoResultMessages.of(info, message.getId()));
		};
	}

	@BeforeAll
	public static void beforeAll() {
		createApplication();
	}

	private static void createApplication() {
		app = mock(Application.class);
		when(app.prologIsValid(any())).thenReturn(true);
	}

	@Test
	@DisplayName("a peer added to a clique is broadcast to all nodes in the clique")
	//@Timeout(10)
	public void peerAddedToCliqueIsBroadcast(@TempDir Path chain1, @TempDir Path chain2, @TempDir Path chain3, @TempDir Path chain4)
			throws URISyntaxException, NoSuchAlgorithmException, InterruptedException,
				   DatabaseException, IOException, DeploymentException, TimeoutException, IncompatiblePeerException {

		var port1 = 8032;
		var port2 = 8034;
		var port3 = 8036;
		var port4 = 8038;
		var peer1 = Peers.of(new URI("ws://localhost:" + port1));
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));
		var peer3 = Peers.of(new URI("ws://localhost:" + port3));
		var peer4 = Peers.of(new URI("ws://localhost:" + port4));

		var config1 = Config.Builder.defaults().setDir(chain1).setPeerTimeout(1000).build();
		var config2 = Config.Builder.defaults().setDir(chain2).setPeerTimeout(1000).build();
		var config3 = Config.Builder.defaults().setDir(chain3).setPeerTimeout(1000).build();
		var config4 = Config.Builder.defaults().setDir(chain4).setPeerTimeout(1000).build();

		var semaphore = new Semaphore(0);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode(Config config) throws NoSuchAlgorithmException, IOException, DatabaseException {
				super(config, app);
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof PeersAddedEvent pae && pae.getPeers().anyMatch(peer4::equals))
					semaphore.release();
			}
		}

		try (var node1 = new MyLocalNode(config1); var node2 = new MyLocalNode(config2);
			 var node3 = new MyLocalNode(config3); var node4 = new MyLocalNode(config4);
			 var service1 = PublicNodeServices.open(node1, port1); var service2 = PublicNodeServices.open(node2, port2);
			 var service3 = PublicNodeServices.open(node3, port3); var service4 = PublicNodeServices.open(node4, port4)) {

			node1.addPeer(peer2);
			node2.addPeer(peer3);
			node3.addPeer(peer1);

			// at this point, each node is a peer of the next in the sequence
			// (there might also be views of the same peer through its local IP address)
			assertTrue(node1.getPeers().map(PeerInfo::getPeer).anyMatch(peer2::equals));
			assertTrue(node2.getPeers().map(PeerInfo::getPeer).anyMatch(peer3::equals));
			assertTrue(node3.getPeers().map(PeerInfo::getPeer).anyMatch(peer1::equals));

			// we add peer4 as peer of peer1 now
			node1.addPeer(peer4);

			// we wait to three events of addition of peer4 as peer
			semaphore.tryAcquire(3, 2, TimeUnit.SECONDS);

			// peer4 is a peer of node1, node2 and node3 now
			assertTrue(node1.getPeers().map(PeerInfo::getPeer).anyMatch(peer4::equals));
			assertTrue(node2.getPeers().map(PeerInfo::getPeer).anyMatch(peer4::equals));
		}
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = PeerPropagationTests.class.getClassLoader().getResource("logging.properties");
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
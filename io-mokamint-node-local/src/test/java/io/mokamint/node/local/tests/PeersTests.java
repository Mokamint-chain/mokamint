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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.application.api.Application;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.Versions;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.Version;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.tasks.AddPeersTask;
import io.mokamint.node.messages.GetInfoMessage;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.service.AbstractPublicNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

public class PeersTests {

	/**
	 * The configuration of the node used for testing.
	 */
	private static Config config;

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

	@BeforeEach
	public void beforeEach() throws IOException, NoSuchAlgorithmException {
		createConfiguration();
		deleteChainDirectiory();
	}

	private static void createConfiguration() throws NoSuchAlgorithmException {
		config = Config.Builder.defaults()
			.setDeadlineWaitTimeout(1000) // a short time is OK for testing
			.build();
	}

	private static void deleteChainDirectiory() throws IOException {
		try {
			Files.walk(config.dir)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
		}
		catch (NoSuchFileException e) {
			// OK, it happens for the first test
		}
	}
	
	private static void createApplication() {
		app = mock(Application.class);
		when(app.prologIsValid(any())).thenReturn(true);
	}

	@Test
	@DisplayName("seeds are used as peers")
	@Timeout(10)
	public void seedsAreUsedAsPeers() throws URISyntaxException, NoSuchAlgorithmException, InterruptedException, IOException, TimeoutException, DeploymentException, DatabaseException {
		var port1 = 8032;
		var port2 = 8034;
		var peer1 = Peers.of(new URI("ws://localhost:" + port1));
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));
		var allPeers = Set.of(peer1, peer2);
		var stillToAccept = new HashSet<>(allPeers);

		config = Config.Builder.defaults()
				.addSeed(peer1.getURI())
				.addSeed(peer2.getURI())
				.build();

		var semaphore = new Semaphore(0);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, IOException, DatabaseException {
				super(config, app);
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof PeersAddedEvent pae)
					pae.getPeers()
						.filter(stillToAccept::remove)
						.forEach(_peer -> semaphore.release());
			}
		}

		try (var service1 = new PublicTestServer(port1); var service2 = new PublicTestServer(port2); var node = new MyLocalNode()) {
			semaphore.acquire(2);
			assertEquals(allPeers, node.getPeers().map(PeerInfo::getPeer).collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("if peers are added to a node, they are saved into the database and used at the next start-up")
	@Timeout(10)
	public void addedPeersAreUsedAtNextStart() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException, TimeoutException, DeploymentException, IncompatiblePeerException, DatabaseException {
		var port1 = 8032;
		var port2 = 8034;
		var peer1 = Peers.of(new URI("ws://localhost:" + port1));
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));
		var allPeers = Set.of(peer1, peer2);
		var allowAddPeers = new AtomicBoolean(false);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException {
				super(config, app);
			}

			@Override
			protected void onSchedule(Task task) {
				if (!allowAddPeers.get() && task instanceof AddPeersTask)
					fail();
			}
		}

		try (var service1 = new PublicTestServer(port1); var service2 = new PublicTestServer(port2)) {
			try (var node = new MyLocalNode()) {
				assertTrue(node.getPeers().count() == 0L);
				allowAddPeers.set(true);
				node.addPeer(peer1);
				node.addPeer(peer2);
				assertEquals(allPeers, node.getPeers().map(PeerInfo::getPeer).collect(Collectors.toSet()));
			}

			allowAddPeers.set(false);

			try (var node = new MyLocalNode()) {
				assertEquals(allPeers, node.getPeers().map(PeerInfo::getPeer).collect(Collectors.toSet()));
			}
		}
	}

	@Test
	@DisplayName("if a peer is removed from a node, the database is updated and the seed is not used at the next start-up")
	@Timeout(10)
	public void removedPeerIsNotUsedAtNextStart() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException, TimeoutException, DeploymentException, DatabaseException {
		var port1 = 8032;
		var port2 = 8034;
		var peer1 = Peers.of(new URI("ws://localhost:" + port1));
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));
		var allPeers = new HashSet<Peer>();
		allPeers.add(peer1);
		allPeers.add(peer2);
		var stillToAccept = new HashSet<>(allPeers);

		config = Config.Builder.defaults()
				.addSeed(peer1.getURI())
				.addSeed(peer2.getURI())
				.build();

		var allowAddPeers = new AtomicBoolean(true);

		var semaphore = new Semaphore(0);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException {
				super(config, app);
			}

			@Override
			protected void onSchedule(Task task) {
				if (!allowAddPeers.get() && task instanceof AddPeersTask)
					fail();
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof PeersAddedEvent pae)
					pae.getPeers()
						.filter(stillToAccept::remove)
						.forEach(_peer -> semaphore.release());
			}
		}

		try (var service1 = new PublicTestServer(port1); var service2 = new PublicTestServer(port2)) {
			try (var node = new MyLocalNode()) {
				semaphore.acquire(2);
				assertEquals(allPeers, node.getPeers().map(PeerInfo::getPeer).collect(Collectors.toSet()));
				node.removePeer(peer1);
				allPeers.remove(peer1);
				assertEquals(allPeers, node.getPeers().map(PeerInfo::getPeer).collect(Collectors.toSet()));
			}

			config = Config.Builder.defaults()
					.build();

			allowAddPeers.set(false);

			try (var node = LocalNodes.of(config, app)) {
				assertEquals(allPeers, node.getPeers().map(PeerInfo::getPeer).collect(Collectors.toSet()));
			}
		}
	}

	@Test
	@DisplayName("two peers that differ for the patch version only can work together")
	public void addPeerWorksIfPatchVersionIsDifferent() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException, TimeoutException, DeploymentException, IncompatiblePeerException, DatabaseException {
		var port = 8032;
		var peer = Peers.of(new URI("ws://localhost:" + port));
		var allPeers = Set.of(peer);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException {
				super(config, app);
			}

			@Override
			public NodeInfo getInfo() {
				var version = info.getVersion();
				return NodeInfos.of(Versions.of(version.getMajor(), version.getMinor(), version.getPatch() + 3), UUID.randomUUID());
			}
		}

		try (var service = new PublicTestServer(port); var node = new MyLocalNode()) {
			node.addPeer(peer);
			assertEquals(allPeers, node.getPeers().map(PeerInfo::getPeer).collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("two peers that differ for the minor version cannot work together")
	public void addPeerFailsIfMinorVersionIsDifferent() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException, TimeoutException, DeploymentException, DatabaseException {
		var port = 8032;
		var peer = Peers.of(new URI("ws://localhost:" + port));

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException {
				super(config, app);
			}

			@Override
			public NodeInfo getInfo() {
				var version = info.getVersion();
				return NodeInfos.of(Versions.of(version.getMajor(), version.getMinor() + 3, version.getPatch()), UUID.randomUUID());
			}
		}

		try (var service = new PublicTestServer(port); var node = new MyLocalNode()) {
			assertThrows(IncompatiblePeerException.class, () -> node.addPeer(peer));
		}
	}

	@Test
	@DisplayName("two peers that differ for the major version cannot work together")
	public void addPeerFailsIfMajorVersionIsDifferent() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException, TimeoutException, DeploymentException, DatabaseException {
		var port = 8032;
		var peer = Peers.of(new URI("ws://localhost:" + port));

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException {
				super(config, app);
			}

			@Override
			public NodeInfo getInfo() {
				var version = info.getVersion();
				return NodeInfos.of(Versions.of(version.getMajor() + 1, version.getMinor(), version.getPatch()), UUID.randomUUID());
			}
		}

		try (var service = new PublicTestServer(port); var node = new MyLocalNode()) {
			assertThrows(IncompatiblePeerException.class, () -> node.addPeer(peer));
		}
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = PeersTests.class.getClassLoader().getResource("logging.properties");
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
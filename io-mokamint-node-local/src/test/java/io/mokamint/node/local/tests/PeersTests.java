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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.application.api.Application;
import io.mokamint.node.Chains;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.PublicNodeInternals;
import io.mokamint.node.Versions;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.PeerAdditionRejectedException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.Version;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.NodePeers.PeersAddedEvent;
import io.mokamint.node.service.internal.PublicNodeServiceImpl;
import jakarta.websocket.DeploymentException;

public class PeersTests {

	/**
	 * The node information of the nodes used in the tests.
	 */
	private static NodeInfo info = NodeInfos.of(mkVersion(), UUID.randomUUID(), LocalDateTime.now(ZoneId.of("UTC")));

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

	private static PublicNodeInternals mkNode() throws TimeoutException, InterruptedException, ClosedNodeException, DatabaseException {
		PublicNodeInternals result = mock();
		when(result.getInfo()).thenReturn(info);
		when(result.getChain(anyLong(), anyLong())).thenReturn(Chains.of(Stream.empty()));
		return result;
	}

	/**
	 * Test server implementation.
	 */
	@ThreadSafe
	private static class PublicTestServer extends PublicNodeServiceImpl {

		/**
		 * Creates a new test server.
		 * 
		 * @param port the port where the server is published
		 * @throws DeploymentException if the service cannot be deployed
		 */
		private PublicTestServer(int port) throws DeploymentException, IOException, TimeoutException, InterruptedException, ClosedNodeException, DatabaseException {
			super(mkNode(), port, 180000L, 1000, Optional.empty());
		}
	}

	@BeforeAll
	public static void beforeAll() {
		app = mock(Application.class);
		when(app.prologIsValid(any())).thenReturn(true);
	}

	private static Config mkConfig(Path dir) throws NoSuchAlgorithmException {
		return Config.Builder.defaults()
			.setDir(dir)
			.setDeadlineWaitTimeout(1000) // a short time is OK for testing
			.build();
	}

	@Test
	@DisplayName("seeds are used as peers")
	@Timeout(10)
	public void seedsAreUsedAsPeers(@TempDir Path dir) throws URISyntaxException, NoSuchAlgorithmException, InterruptedException, IOException, TimeoutException, DeploymentException, DatabaseException, ClosedNodeException, AlreadyInitializedException {
		var port1 = 8032;
		var port2 = 8034;
		var peer1 = Peers.of(new URI("ws://localhost:" + port1));
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));
		var allPeers = Set.of(peer1, peer2);
		var stillToAccept = new HashSet<>(allPeers);

		Config config = Config.Builder.defaults()
				.addSeed(peer1.getURI())
				.addSeed(peer2.getURI())
				.setDeadlineWaitTimeout(1000)
				.setDir(dir)
				.build();

		var semaphore = new Semaphore(0);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(config, app, false);
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
			assertEquals(allPeers, node.getPeerInfos().map(PeerInfo::getPeer).collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("if peers are added to a node, they are saved into the database and used at the next start-up")
	@Timeout(10)
	public void addedPeersAreUsedAtNextStart(@TempDir Path dir) throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException, TimeoutException, DeploymentException, PeerAdditionRejectedException, DatabaseException, ClosedNodeException, AlreadyInitializedException {
		var port1 = 8032;
		var port2 = 8034;
		var peer1 = Peers.of(new URI("ws://localhost:" + port1));
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));
		var allPeers = Set.of(peer1, peer2);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException {
				super(mkConfig(dir), app, false);
			}
		}

		try (var service1 = new PublicTestServer(port1); var service2 = new PublicTestServer(port2)) {
			try (var node = new MyLocalNode()) {
				assertTrue(node.getPeerInfos().count() == 0L);
				node.addPeer(peer1);
				node.addPeer(peer2);
				assertEquals(allPeers, node.getPeerInfos().map(PeerInfo::getPeer).collect(Collectors.toSet()));
			}

			try (var node = new MyLocalNode()) {
				assertEquals(allPeers, node.getPeerInfos().map(PeerInfo::getPeer).collect(Collectors.toSet()));
			}
		}
	}

	@Test
	@DisplayName("if a peer is removed from a node, the database is updated and the seed is not used at the next start-up")
	@Timeout(10)
	public void removedPeerIsNotUsedAtNextStart(@TempDir Path dir) throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException, TimeoutException, DeploymentException, DatabaseException, ClosedNodeException, AlreadyInitializedException {
		var port1 = 8032;
		var port2 = 8034;
		var peer1 = Peers.of(new URI("ws://localhost:" + port1));
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));
		var allPeers = new HashSet<Peer>();
		allPeers.add(peer1);
		allPeers.add(peer2);
		var stillToAccept = new HashSet<>(allPeers);

		Config config = Config.Builder.defaults()
				.addSeed(peer1.getURI())
				.addSeed(peer2.getURI())
				.setDir(dir)
				.setDeadlineWaitTimeout(1000)
				.build();

		var semaphore = new Semaphore(0);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException {
				super(config, app, false);
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
				assertEquals(allPeers, node.getPeerInfos().map(PeerInfo::getPeer).collect(Collectors.toSet()));
				node.removePeer(peer1);
				allPeers.remove(peer1);
				assertEquals(allPeers, node.getPeerInfos().map(PeerInfo::getPeer).collect(Collectors.toSet()));
			}

			try (var node = LocalNodes.of(mkConfig(dir), app, false)) {
				assertEquals(allPeers, node.getPeerInfos().map(PeerInfo::getPeer).collect(Collectors.toSet()));
			}
		}
	}

	@Test
	@DisplayName("two peers that differ for the patch version only can work together")
	public void addPeerWorksIfPatchVersionIsDifferent(@TempDir Path dir) throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException, TimeoutException, DeploymentException, PeerAdditionRejectedException, DatabaseException, ClosedNodeException, AlreadyInitializedException {
		var port = 8032;
		var peer = Peers.of(new URI("ws://localhost:" + port));
		var allPeers = Set.of(peer);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException {
				super(mkConfig(dir), app, false);
			}

			@Override
			public NodeInfo getInfo() {
				var version = info.getVersion();
				return NodeInfos.of(Versions.of(version.getMajor(), version.getMinor(), version.getPatch() + 3), UUID.randomUUID(), info.getLocalDateTimeUTC());
			}
		}

		try (var service = new PublicTestServer(port); var node = new MyLocalNode()) {
			node.addPeer(peer);
			assertEquals(allPeers, node.getPeerInfos().map(PeerInfo::getPeer).collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("two peers that differ for the minor version cannot work together")
	public void addPeerFailsIfMinorVersionIsDifferent(@TempDir Path dir) throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException, TimeoutException, DeploymentException, DatabaseException, ClosedNodeException, AlreadyInitializedException {
		var port = 8032;
		var peer = Peers.of(new URI("ws://localhost:" + port));

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException {
				super(mkConfig(dir), app, false);
			}

			@Override
			public NodeInfo getInfo() {
				var version = info.getVersion();
				return NodeInfos.of(Versions.of(version.getMajor(), version.getMinor() + 3, version.getPatch()), UUID.randomUUID(), info.getLocalDateTimeUTC());
			}
		}

		try (var service = new PublicTestServer(port); var node = new MyLocalNode()) {
			assertThrows(PeerAdditionRejectedException.class, () -> node.addPeer(peer));
		}
	}

	@Test
	@DisplayName("two peers that differ for the major version cannot work together")
	public void addPeerFailsIfMajorVersionIsDifferent(@TempDir Path dir) throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException, TimeoutException, DeploymentException, DatabaseException, ClosedNodeException, AlreadyInitializedException {
		var port = 8032;
		var peer = Peers.of(new URI("ws://localhost:" + port));

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException {
				super(mkConfig(dir), app, false);
			}

			@Override
			public NodeInfo getInfo() {
				var version = info.getVersion();
				return NodeInfos.of(Versions.of(version.getMajor() + 1, version.getMinor(), version.getPatch()), UUID.randomUUID(), info.getLocalDateTimeUTC());
			}
		}

		try (var service = new PublicTestServer(port); var node = new MyLocalNode()) {
			assertThrows(PeerAdditionRejectedException.class, () -> node.addPeer(peer));
		}
	}

	@Test
	@DisplayName("two peers whose local times are too far away cannot work together")
	public void addPeerFailsIfLocalTimesAreTooFarAway(@TempDir Path dir) throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException, TimeoutException, DeploymentException, DatabaseException, ClosedNodeException, AlreadyInitializedException {
		var port = 8032;
		var peer = Peers.of(new URI("ws://localhost:" + port));

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException {
				super(mkConfig(dir), app, false);
			}

			@Override
			public NodeInfo getInfo() {
				return NodeInfos.of(info.getVersion(), UUID.randomUUID(), info.getLocalDateTimeUTC().plus(getConfig().peerMaxTimeDifference + 1000L, ChronoUnit.MILLIS));
			}
		}

		try (var service = new PublicTestServer(port); var node = new MyLocalNode()) {
			assertThrows(PeerAdditionRejectedException.class, () -> node.addPeer(peer));
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
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.stubbing.OngoingStubbing;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.ChainPortions;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.Versions;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.api.Version;
import io.mokamint.node.local.AbstractLocalNode;
import io.mokamint.node.local.ApplicationTimeoutException;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.service.internal.PublicNodeServiceImpl;
import jakarta.websocket.DeploymentException;

public class PeersTests extends AbstractLoggedTests {

	/**
	 * The node information of the nodes used in the tests.
	 */
	private final static NodeInfo info = NodeInfos.of(mkVersion(), UUID.randomUUID(), LocalDateTime.now(ZoneId.of("UTC")));

	/**
	 * The chain information of the nodes used in the tests.
	 */
	private final static ChainInfo chainInfo = ChainInfos.of(2L, Optional.of(new byte[] { 1, 2, 3, 4 }), Optional.of(new byte[] { 5, 6, 7, 8 }), Optional.of(new byte[] { 13, 17, 19 }));

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

	private static PublicNode mkNode(NodeInfo info) throws NoSuchAlgorithmException {
		try {
			PublicNode node = mock();
			when(node.getInfo()).thenReturn(info);
			when(node.getChainInfo()).thenReturn(chainInfo);
			when(node.getChainPortion(anyLong(), anyInt())).thenReturn(ChainPortions.of(Stream.empty()));
			OngoingStubbing<ConsensusConfig<?,?>> w = when(node.getConfig());
			w.thenReturn(LocalNodeConfigBuilders.defaults().build());
			return node;
		}
		catch (TimeoutException | InterruptedException | NodeException e) {
			// fake, it's just out of stubbing
			throw new RuntimeException("Unexpected exception", e);
		}
	}

	/**
	 * Test server implementation.
	 */
	@ThreadSafe
	private static class PublicTestServer extends PublicNodeServiceImpl {

		/**
		 * Creates a new test server.
		 * 
		 * @param info the information about the node
		 * @param port the port where the server is published
		 * @throws DeploymentException if the service cannot be deployed
		 * @throws NoSuchAlgorithmException if some hashing algorithm is missing
		 */
		private PublicTestServer(NodeInfo info, int port) throws DeploymentException, IOException, NoSuchAlgorithmException {
			super(mkNode(info), port, 180000, 1000, Optional.of(URI.create("ws://localhost:" + port)));
		}
	}

	private static LocalNodeConfig mkConfig(Path dir) throws NoSuchAlgorithmException {
		return LocalNodeConfigBuilders.defaults()
			.setDir(dir)
			.setDeadlineWaitTimeout(1000) // a short time is OK for testing
			.build();
	}

	@Test
	@DisplayName("seeds are used as peers")
	@Timeout(10)
	public void seedsAreUsedAsPeers(@TempDir Path dir) throws NoSuchAlgorithmException, InterruptedException, IOException, ApplicationTimeoutException, DeploymentException, NodeException {
		var port1 = 8032;
		var port2 = 8034;
		var peer1 = Peers.of(URI.create("ws://localhost:" + port1));
		var peer2 = Peers.of(URI.create("ws://localhost:" + port2));
		var allPeers = Set.of(peer1, peer2);
		var stillToAccept = new HashSet<>(allPeers);

		LocalNodeConfig config = LocalNodeConfigBuilders.defaults()
				.addSeed(peer1.getURI())
				.addSeed(peer2.getURI())
				.setDeadlineWaitTimeout(1000)
				.setDir(dir)
				.build();

		var semaphore = new Semaphore(0);

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode() throws IOException, InterruptedException, NodeException, ApplicationTimeoutException {
				super(config, nodeKey, app, false);
			}

			@Override
			protected void onAdded(Peer peer) {
				super.onAdded(peer);
				if (stillToAccept.remove(peer))
					semaphore.release();
			}
		}

		try (var service1 = new PublicTestServer(info, port1); var service2 = new PublicTestServer(info, port2); var node = new MyLocalNode()) {
			semaphore.acquire(2);
			assertEquals(allPeers, node.getPeerInfos().map(PeerInfo::getPeer).collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("if peers are added to a node, they are saved into the database and used at the next start-up")
	@Timeout(10)
	public void addedPeersAreUsedAtNextStart(@TempDir Path dir) throws NoSuchAlgorithmException, IOException, InterruptedException, ApplicationTimeoutException, DeploymentException, PeerRejectedException, NodeException, TimeoutException {
		var port1 = 8032;
		var port2 = 8034;
		var peer1 = Peers.of(URI.create("ws://localhost:" + port1));
		var peer2 = Peers.of(URI.create("ws://localhost:" + port2));
		var allPeers = Set.of(peer1, peer2);

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode() throws NoSuchAlgorithmException, InterruptedException, NodeException, ApplicationTimeoutException {
				super(mkConfig(dir), nodeKey, app, false);
			}
		}

		try (var service1 = new PublicTestServer(info, port1); var service2 = new PublicTestServer(info, port2)) {
			try (var node = new MyLocalNode()) {
				assertTrue(node.getPeerInfos().count() == 0L);
				node.add(peer1);
				node.add(peer2);
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
	public void removedPeerIsNotUsedAtNextStart(@TempDir Path dir) throws NoSuchAlgorithmException, IOException, InterruptedException, ApplicationTimeoutException, DeploymentException, NodeException, TimeoutException {
		var port1 = 8032;
		var port2 = 8034;
		var peer1 = Peers.of(URI.create("ws://localhost:" + port1));
		var peer2 = Peers.of(URI.create("ws://localhost:" + port2));
		var allPeers = new HashSet<Peer>();
		allPeers.add(peer1);
		allPeers.add(peer2);
		var stillToAccept = new HashSet<>(allPeers);

		var config = LocalNodeConfigBuilders.defaults()
				.addSeed(peer1.getURI())
				.addSeed(peer2.getURI())
				.setDir(dir)
				.setDeadlineWaitTimeout(1000)
				.build();

		var semaphore = new Semaphore(0);

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode() throws InterruptedException, NodeException, ApplicationTimeoutException {
				super(config, nodeKey, app, false);
			}

			@Override
			protected void onAdded(Peer peer) {
				super.onAdded(peer);
				if (stillToAccept.remove(peer))
					semaphore.release();
			}
		}

		try (var service1 = new PublicTestServer(info, port1); var service2 = new PublicTestServer(info, port2)) {
			try (var node = new MyLocalNode()) {
				assertTrue(semaphore.tryAcquire(2, 10, TimeUnit.SECONDS));
				assertEquals(allPeers, node.getPeerInfos().map(PeerInfo::getPeer).collect(Collectors.toSet()));
				node.remove(peer1);
				allPeers.remove(peer1);
				assertEquals(allPeers, node.getPeerInfos().map(PeerInfo::getPeer).collect(Collectors.toSet()));
			}

			try (var node = LocalNodes.of(mkConfig(dir), nodeKey, app, false)) {
				assertEquals(allPeers, node.getPeerInfos().map(PeerInfo::getPeer).collect(Collectors.toSet()));
			}
		}
	}

	@Test
	@DisplayName("two peers that differ for the patch version only can work together")
	public void addPeerWorksIfPatchVersionIsDifferent(@TempDir Path dir) throws NoSuchAlgorithmException, IOException, InterruptedException, ApplicationTimeoutException, DeploymentException, PeerRejectedException, NodeException, TimeoutException {
		var port = 8032;
		var peer = Peers.of(URI.create("ws://localhost:" + port));
		var allPeers = Set.of(peer);

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode() throws NoSuchAlgorithmException, InterruptedException, NodeException, ApplicationTimeoutException {
				super(mkConfig(dir), nodeKey, app, false);
			}
		}

		var version = info.getVersion();
		var otherInfo = NodeInfos.of(Versions.of(version.getMajor(), version.getMinor(), version.getPatch() + 3), UUID.randomUUID(), info.getLocalDateTimeUTC());
		try (var service = new PublicTestServer(otherInfo, port); var node = new MyLocalNode()) {
			node.add(peer);
			assertEquals(allPeers, node.getPeerInfos().map(PeerInfo::getPeer).collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("two peers that differ for the minor version cannot work together")
	public void addPeerFailsIfMinorVersionIsDifferent(@TempDir Path dir) throws NoSuchAlgorithmException, IOException, InterruptedException, ApplicationTimeoutException, DeploymentException, NodeException {
		var port = 8032;
		var peer = Peers.of(URI.create("ws://localhost:" + port));

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode() throws NoSuchAlgorithmException, InterruptedException, NodeException, ApplicationTimeoutException {
				super(mkConfig(dir), nodeKey, app, false);
			}
		}

		var version = info.getVersion();
		var otherInfo = NodeInfos.of(Versions.of(version.getMajor(), version.getMinor() + 3, version.getPatch()), UUID.randomUUID(), info.getLocalDateTimeUTC());
		try (var service = new PublicTestServer(otherInfo, port); var node = new MyLocalNode()) {
			PeerRejectedException e = assertThrows(PeerRejectedException.class, () -> node.add(peer));
			assertTrue(e.getMessage().startsWith("Peer version " + otherInfo.getVersion() + " is incompatible with this node's version " + info.getVersion()));
		}
	}

	@Test
	@DisplayName("two peers that differ for the major version cannot work together")
	public void addPeerFailsIfMajorVersionIsDifferent(@TempDir Path dir) throws NoSuchAlgorithmException, InterruptedException, ApplicationTimeoutException, DeploymentException, NodeException, IOException {
		var port = 8032;
		var peer = Peers.of(URI.create("ws://localhost:" + port));

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode() throws NoSuchAlgorithmException, InterruptedException, NodeException, ApplicationTimeoutException {
				super(mkConfig(dir), nodeKey, app, false);
			}
		}

		var version = info.getVersion();
		var otherInfo = NodeInfos.of(Versions.of(version.getMajor() + 1, version.getMinor(), version.getPatch()), UUID.randomUUID(), info.getLocalDateTimeUTC());
		try (var service = new PublicTestServer(otherInfo, port); var node = new MyLocalNode()) {
			PeerRejectedException e = assertThrows(PeerRejectedException.class, () -> node.add(peer));
			assertTrue(e.getMessage().startsWith("Peer version " + otherInfo.getVersion() + " is incompatible with this node's version " + info.getVersion()));
		}
	}

	@Test
	@DisplayName("two peers whose local times are too far away cannot work together")
	public void addPeerFailsIfLocalTimesAreTooFarAway(@TempDir Path dir) throws NoSuchAlgorithmException, IOException, InterruptedException, ApplicationTimeoutException, DeploymentException, NodeException {
		var port = 8032;
		var peer = Peers.of(URI.create("ws://localhost:" + port));

		var config = mkConfig(dir);

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode() throws InterruptedException, ApplicationTimeoutException, NodeException {
				super(config, nodeKey, app, false);
			}
		}

		var otherInfo = NodeInfos.of(info.getVersion(), UUID.randomUUID(), info.getLocalDateTimeUTC().minus(config.getPeerMaxTimeDifference() + 1000L, ChronoUnit.MILLIS));
		try (var service = new PublicTestServer(otherInfo, port); var node = new MyLocalNode()) {
			PeerRejectedException e = assertThrows(PeerRejectedException.class, () -> node.add(peer));
			assertTrue(e.getMessage().startsWith("The time of the peer is more than " + node.getConfig().getPeerMaxTimeDifference() + " ms away"));
		}
	}

	@Test
	@DisplayName("two peers whose genesis block is different cannot work together")
	public void addPeerFailsIfGenesisBlocksAreDifferent(@TempDir Path dir) throws NoSuchAlgorithmException, IOException, InterruptedException, ApplicationTimeoutException, DeploymentException, NodeException {
		var port = 8032;
		var peer = Peers.of(URI.create("ws://localhost:" + port));

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode() throws NoSuchAlgorithmException, InterruptedException, NodeException, ApplicationTimeoutException {
				super(mkConfig(dir), nodeKey, app, false);
			}

			@Override
			public ChainInfo getChainInfo() {
				return ChainInfos.of(chainInfo.getLength(), Optional.of(new byte[] { 10, 11, 23, 34, 56, 7 }), chainInfo.getHeadHash(), chainInfo.getHeadStateId());
			}
		}

		try (var service = new PublicTestServer(info, port); var node = new MyLocalNode()) {
			PeerRejectedException e = assertThrows(PeerRejectedException.class, () -> node.add(peer));
			assertTrue(e.getMessage().startsWith("The peers have distinct genesis blocks"));
		}
	}
}
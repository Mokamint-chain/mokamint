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

package io.mokamint.node.tests;

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
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.LogManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.application.api.Application;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.tasks.AddPeerTask;
import io.mokamint.node.messages.GetChainInfoMessage;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.service.AbstractPublicNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

public class PeersTests {

	/**
	 * The configuration of the node used for testing.
	 */
	private static Config config;

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

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

		// TODO
		@Override
		protected void onGetChainInfo(GetChainInfoMessage message, Session session) {
			super.onGetChainInfo(message, session);
			sendObjectAsync(session, GetChainInfoResultMessages.of(ChainInfos.of(0, Optional.empty(), Optional.empty()), message.getId()));
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
	public void seedsAreUsedAsPeers() throws URISyntaxException, NoSuchAlgorithmException, InterruptedException, IOException, TimeoutException, DeploymentException {
		var port1 = 8032;
		var port2 = 8034;
		var uri1 = new URI("ws://localhost:" + port1);
		var uri2 = new URI("ws://localhost:" + port2);

		config = Config.Builder.defaults()
				.addSeed(uri1)
				.addSeed(uri2)
				.build();

		var semaphore = new Semaphore(0);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, IOException, URISyntaxException {
				super(config, app);
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof PeerAcceptedEvent) {
					var uri = ((PeerAcceptedEvent) event).peer.getURI();
					if (uri.equals(uri1) || uri.equals(uri2))
						semaphore.release();
				}
			};
		}

		try (var service1 = new PublicTestServer(port1); var service2 = new PublicTestServer(port2); var node = new MyLocalNode()) {
			semaphore.acquire(2);
			assertTrue(node.getPeers().count() == 2L);
			assertTrue(node.getPeers().map(Peer::getURI).anyMatch(uri1::equals));
			assertTrue(node.getPeers().map(Peer::getURI).anyMatch(uri2::equals));
		}
	}

	@Test
	@DisplayName("if a peer is added to a node, it is saved into the database and it is used at next start-up")
	@Timeout(10)
	public void addedPeerIsUsedAtNextStart() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException, TimeoutException, DeploymentException {
		var port1 = 8032;
		var port2 = 8034;
		var peer1 = Peers.of(new URI("ws://localhost:" + port1));
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));
		var allowAddPeers = new AtomicBoolean();

		class MyLocalNode extends LocalNodeImpl {
			private final Semaphore semaphore = new Semaphore(0);

			private MyLocalNode() throws NoSuchAlgorithmException, IOException, URISyntaxException {
				super(config, app);
			}

			@Override
			protected void onSchedule(Task task) {
				if (!allowAddPeers.get() && task instanceof AddPeerTask)
					fail();
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof PeerAcceptedEvent) {
					var peer = ((PeerAcceptedEvent) event).peer;
					if (peer.equals(peer1) || peer.equals(peer2))
						semaphore.release();
				}
			};
		}

		try (var service1 = new PublicTestServer(port1); var service2 = new PublicTestServer(port2)) {
			try (var node = new MyLocalNode()) {
				assertTrue(node.getPeers().count() == 0L);
				allowAddPeers.set(true);
				node.addPeer(peer1);
				node.addPeer(peer2);
				node.semaphore.acquire(2);
				assertTrue(node.getPeers().count() == 2L);
			}

			allowAddPeers.set(false);

			try (var node = new MyLocalNode()) {
				assertTrue(node.getPeers().count() == 2L);
				assertTrue(node.getPeers().anyMatch(peer1::equals));
				assertTrue(node.getPeers().anyMatch(peer2::equals));
			}
		}
	}

	@Test
	@DisplayName("if a peer is removed from a node, the database is updated and the seed is not used at the next start-up")
	@Timeout(10)
	public void removedPeerIsNotUsedAtNextStart() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException, TimeoutException, DeploymentException {
		var port1 = 8032;
		var port2 = 8034;
		var uri1 = new URI("ws://localhost:" + port1);
		var uri2 = new URI("ws://localhost:" + port2);

		config = Config.Builder.defaults()
				.addSeed(uri1)
				.addSeed(uri2)
				.build();

		var allowAddPeers = new AtomicBoolean(true);

		class MyLocalNode extends LocalNodeImpl {
			private final Semaphore semaphore = new Semaphore(0);

			private MyLocalNode() throws NoSuchAlgorithmException, IOException, URISyntaxException {
				super(config, app);
			}

			@Override
			protected void onSchedule(Task task) {
				if (!allowAddPeers.get() && task instanceof AddPeerTask)
					fail();
			}

			@Override
			protected void onComplete(Event event) {
				if (event instanceof PeerAcceptedEvent) {
					var uri = ((PeerAcceptedEvent) event).peer.getURI();
					if (uri.equals(uri1) || uri.equals(uri2))
						semaphore.release();
				}
			};
		}

		try (var service1 = new PublicTestServer(port1); var service2 = new PublicTestServer(port2)) {
			try (var node = new MyLocalNode()) {
				node.semaphore.acquire(2);
				assertTrue(node.getPeers().count() == 2L);
				node.removePeer(Peers.of(uri1));
				assertTrue(node.getPeers().count() == 1L);
			}

			config = Config.Builder.defaults()
					.build();

			allowAddPeers.set(false);

			try (var node = LocalNodes.of(config, app)) {
				assertTrue(node.getPeers().count() == 1L);
				assertTrue(node.getPeers().map(Peer::getURI).anyMatch(uri2::equals));
			}
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
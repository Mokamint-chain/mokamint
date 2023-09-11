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

package io.mokamint.node.integration.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.Peers;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.messages.AddPeerMessages;
import io.mokamint.node.messages.AddPeerResultMessages;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.OpenMinerResultMessages;
import io.mokamint.node.messages.RemovePeerResultMessages;
import io.mokamint.node.messages.api.AddPeerMessage;
import io.mokamint.node.messages.api.OpenMinerMessage;
import io.mokamint.node.messages.api.RemovePeerMessage;
import io.mokamint.node.remote.RemoteRestrictedNodes;
import io.mokamint.node.service.internal.RestrictedNodeServiceImpl;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

public class RemoteRestrictedNodeTests {
	private final static URI URI;
	private final static int PORT = 8031;

	static {
		try {
			URI = new URI("ws://localhost:" + PORT);
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private final static long TIME_OUT = 500L;

	/**
	 * Test server implementation.
	 */
	@ThreadSafe
	private static class RestrictedTestServer extends RestrictedNodeServiceImpl {

		/**
		 * Creates a new test server.
		 * 
		 * @throws DeploymentException if the service cannot be deployed
		 * @throws IOException if an I/O error occurs
		 */
		private RestrictedTestServer() throws DeploymentException, IOException {
			super(mock(), PORT);
		}
	}

	@Test
	@DisplayName("addPeer() works")
	public void addPeerWorks() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException, PeerRejectedException, DatabaseException, ClosedNodeException {
		var peers1 = Set.of(Peers.of(new URI("ws://my.machine:1024")), Peers.of(new URI("ws://your.machine:1025")));
		var peers2 = new HashSet<Peer>();

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeer(AddPeerMessage message, Session session) {
				peers2.add(message.getPeer());
				try {
					sendObjectAsync(session, AddPeerResultMessages.of(true, message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			for (var peer: peers1)
				remote.add(peer);

			assertEquals(peers1, peers2);
		}
	}

	@Test
	@DisplayName("addPeer() works in case of TimeoutException")
	public void addPeerWorksInCaseOfTimeoutException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));
		var exceptionMessage = "timeout";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeer(AddPeerMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new TimeoutException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(TimeoutException.class, () -> remote.add(peer));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("addPeer() works in case of InterruptedException")
	public void addPeerWorksInCaseOfInterruptedException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));
		var exceptionMessage = "interrupted";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeer(AddPeerMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new InterruptedException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(InterruptedException.class, () -> remote.add(peer));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("addPeer() works in case of IOException")
	public void addPeerWorksInCaseOfIOException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));
		var exceptionMessage = "I/O error";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeer(AddPeerMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new IOException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(IOException.class, () -> remote.add(peer));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("addPeer() works in case of IncompatiblePeerException")
	public void addPeerWorksInCaseOfIncompatiblePeerException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));
		var exceptionMessage = "incompatible peers";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeer(AddPeerMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new PeerRejectedException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(PeerRejectedException.class, () -> remote.add(peer));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("addPeer() works in case of DatabaseException")
	public void addPeerWorksInCaseOfDatabaseException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));
		var exceptionMessage = "database exception";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeer(AddPeerMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new DatabaseException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(DatabaseException.class, () -> remote.add(peer));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("if addPeer() is slow, it leads to a time-out")
	public void addPeerWorksInCaseOfTimeout() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeer(AddPeerMessage message, Session session) {
				try {
					Thread.sleep(TIME_OUT * 4); // <----
				}
				catch (InterruptedException e) {}

				try {
					sendObjectAsync(session, AddPeerMessages.of(peer, message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.add(peer));
		}
	}
	
	@Test
	@DisplayName("addPeer() works in case of unexpected exception")
	public void addPeerWorksInCaseOfUnxepectedException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));
		var exceptionMessage = "unexpected";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeer(AddPeerMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new IllegalArgumentException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.add(peer));
		}
	}

	@Test
	@DisplayName("removePeer() works")
	public void removePeerWorks() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException, DatabaseException, ClosedNodeException {
		var peers1 = Set.of(Peers.of(new URI("ws://my.machine:1024")), Peers.of(new URI("ws://your.machine:1025")));
		var peers2 = new HashSet<Peer>();

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onRemovePeer(RemovePeerMessage message, Session session) {
				peers2.add(message.getPeer());
				try {
					sendObjectAsync(session, RemovePeerResultMessages.of(true, message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			for (Peer peer: peers1)
				remote.remove(peer);

			assertEquals(peers1, peers2);
		}
	}

	@Test
	@DisplayName("openMiner() works")
	public void openMinerWorks() throws DeploymentException, IOException, TimeoutException, InterruptedException, ClosedNodeException {
		var ports1 = Set.of(8025,  8026, 8027);
		var ports2 = new HashSet<Integer>();

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onOpenMiner(OpenMinerMessage message, Session session) {
				ports2.add(message.getPort());
				try {
					sendObjectAsync(session, OpenMinerResultMessages.of(true, message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			for (int port: ports1)
				remote.openMiner(port);

			assertEquals(ports1, ports2);
		}
	}

	@Test
	@DisplayName("removePeer() works in case of DatabaseException")
	public void removePeerWorksInCaseOfDatabaseException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));
		var exceptionMessage = "database exception";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onRemovePeer(RemovePeerMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new DatabaseException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(DatabaseException.class, () -> remote.remove(peer));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("removePeer() works in case of IOException")
	public void removePeerWorksInCaseOfIOException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));
		var exceptionMessage = "I/O exception";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onRemovePeer(RemovePeerMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new IOException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(IOException.class, () -> remote.remove(peer));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("openMiner() works in case of IOException")
	public void openMinerWorksInCaseOfIOException() throws DeploymentException, IOException, TimeoutException, InterruptedException {
		var exceptionMessage = "I/O exception";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onOpenMiner(OpenMinerMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new IOException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(IOException.class, () -> remote.openMiner(8025));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("openMiner() works in case of TimeoutException")
	public void openMinerWorksInCaseOfTimeoutException() throws DeploymentException, IOException, TimeoutException, InterruptedException {
		var exceptionMessage = "timeout exception";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onOpenMiner(OpenMinerMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new TimeoutException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(TimeoutException.class, () -> remote.openMiner(8025));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = RemoteRestrictedNodeTests.class.getClassLoader().getResource("logging.properties");
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
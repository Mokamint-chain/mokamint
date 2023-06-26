package io.mokamint.node.integration.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.Peers;
import io.mokamint.node.api.IncompatiblePeerVersionException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.AddPeerMessage;
import io.mokamint.node.messages.AddPeerMessages;
import io.mokamint.node.messages.AddPeerResultMessages;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.RemovePeerMessage;
import io.mokamint.node.messages.RemovePeerResultMessages;
import io.mokamint.node.remote.RemoteRestrictedNodes;
import io.mokamint.node.service.AbstractRestrictedNodeService;
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
	private static class RestrictedTestServer extends AbstractRestrictedNodeService {

		/**
		 * Creates a new test server.
		 * 
		 * @throws DeploymentException if the service cannot be deployed
		 * @throws IOException if an I/O error occurs
		 */
		private RestrictedTestServer() throws DeploymentException, IOException {
			super(PORT);
			deploy();
		}
	}

	@Test
	@DisplayName("addPeers() works")
	public void addPeersWorks() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException, IncompatiblePeerVersionException {
		var peers1 = new HashSet<Peer>();
		peers1.add(Peers.of(new URI("ws://my.machine:1024")));
		peers1.add(Peers.of(new URI("ws://your.machine:1025")));
		var peers2 = new HashSet<Peer>();

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeers(AddPeerMessage message, Session session) {
				peers2.add(message.getPeer());
				sendObjectAsync(session, AddPeerResultMessages.of(message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			for (var peer: peers1)
				remote.addPeer(peer);

			assertEquals(peers1, peers2);
		}
	}

	@Test
	@DisplayName("addPeers() works in case of TimeoutException")
	public void addPeersWorksInCaseOfTimeoutException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));
		var exceptionMessage = "timeout";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeers(AddPeerMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new TimeoutException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(TimeoutException.class, () -> remote.addPeer(peer));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("addPeers() works in case of InterruptedException")
	public void addPeersWorksInCaseOfInterruptedException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));
		var exceptionMessage = "interrupted";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeers(AddPeerMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new InterruptedException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(InterruptedException.class, () -> remote.addPeer(peer));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("addPeers() works in case of IOException")
	public void addPeersWorksInCaseOfIOException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));
		var exceptionMessage = "I/O error";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeers(AddPeerMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new IOException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(IOException.class, () -> remote.addPeer(peer));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("addPeers() works in case of IncompatiblePeerVersionException")
	public void addPeersWorksInCaseOfIncompatiblePeerVersionException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));
		var exceptionMessage = "incompatible versions";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeers(AddPeerMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new IncompatiblePeerVersionException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(IncompatiblePeerVersionException.class, () -> remote.addPeer(peer));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("if addPeers() is slow, it leads to a time-out")
	public void addPeersWorksInCaseOfTimeout() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeers(AddPeerMessage message, Session session) {
				try {
					Thread.sleep(TIME_OUT * 4); // <----
				}
				catch (InterruptedException e) {}

				sendObjectAsync(session, AddPeerMessages.of(peer, message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.addPeer(peer));
		}
	}
	
	@Test
	@DisplayName("addPeers() works in case of unexpected exception")
	public void addPeersWorksInCaseOfUnxepectedException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peer = Peers.of(new URI("ws://my.machine:1024"));
		var exceptionMessage = "unexpected";

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddPeers(AddPeerMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new IllegalArgumentException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.addPeer(peer));
		}
	}

	@Test
	@DisplayName("removePeers() works")
	public void removePeersWorks() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peers1 = new HashSet<Peer>();
		peers1.add(Peers.of(new URI("ws://my.machine:1024")));
		peers1.add(Peers.of(new URI("ws://your.machine:1025")));
		var peers2 = new HashSet<Peer>();

		class MyServer extends RestrictedTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onRemovePeer(RemovePeerMessage message, Session session) {
				peers2.add(message.getPeer());
				sendObjectAsync(session, RemovePeerResultMessages.of(message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			for (Peer peer: peers1)
				remote.removePeer(peer);

			assertEquals(peers1, peers2);
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
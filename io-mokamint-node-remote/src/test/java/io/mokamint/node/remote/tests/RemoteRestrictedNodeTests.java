package io.mokamint.node.remote.tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.LogManager;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.AddPeersMessage;
import io.mokamint.node.messages.AddPeersMessages;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.RemovePeersMessage;
import io.mokamint.node.messages.VoidMessages;
import io.mokamint.node.remote.RemoteRestrictedNodes;
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

	@Test
	@DisplayName("addPeers() works")
	public void addPeersWorks() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peers1 = new Peer[] { Peers.of(new URI("ws://my.machine:1024")), Peers.of(new URI("ws://your.machine:1025")) };
		AtomicReference<Peer[]> peers2 = new AtomicReference<>();

		class MyServer extends RestrictedTestServer {

			public MyServer() throws DeploymentException, IOException {
				super(PORT);
			}

			@Override
			protected void onAddPeers(AddPeersMessage message, Session session) {
				peers2.set(message.getPeers().toArray(Peer[]::new));
				sendObjectAsync(session, VoidMessages.of(message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			remote.addPeers(Stream.of(peers1));
			assertArrayEquals(peers1, peers2.get());
		}
	}

	@Test
	@DisplayName("addPeers() works in case of TimeoutException")
	public void addPeersWorksInCaseOfTimeoutException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peers = new Peer[] { Peers.of(new URI("ws://my.machine:1024")), Peers.of(new URI("ws://your.machine:1025")) };
		var exceptionMessage = "timeout";

		class MyServer extends RestrictedTestServer {

			public MyServer() throws DeploymentException, IOException {
				super(PORT);
			}

			@Override
			protected void onAddPeers(AddPeersMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new TimeoutException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(TimeoutException.class, () -> remote.addPeers(Stream.of(peers)));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("addPeers() works in case of InterruptedException")
	public void addPeersWorksInCaseOfInterruptedException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peers = new Peer[] { Peers.of(new URI("ws://my.machine:1024")), Peers.of(new URI("ws://your.machine:1025")) };
		var exceptionMessage = "interrupted";

		class MyServer extends RestrictedTestServer {

			public MyServer() throws DeploymentException, IOException {
				super(PORT);
			}

			@Override
			protected void onAddPeers(AddPeersMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new InterruptedException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(InterruptedException.class, () -> remote.addPeers(Stream.of(peers)));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("if addPeers() is slow, it leads to a time-out")
	public void addPeersWorksInCaseOfTimeout() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peers = new Peer[] { Peers.of(new URI("ws://my.machine:1024")), Peers.of(new URI("ws://your.machine:1025")) };

		class MyServer extends RestrictedTestServer {

			public MyServer() throws DeploymentException, IOException {
				super(PORT);
			}

			@Override
			protected void onAddPeers(AddPeersMessage message, Session session) {
				try {
					Thread.sleep(TIME_OUT * 4); // <----
				}
				catch (InterruptedException e) {}

				sendObjectAsync(session, AddPeersMessages.of(Stream.of(peers), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.addPeers(Stream.of(peers)));
		}
	}
	
	@Test
	@DisplayName("addPeers() works in case of unexpected exception")
	public void addPeersWorksInCaseOfUnxepectedException() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peers = new Peer[] { Peers.of(new URI("ws://my.machine:1024")), Peers.of(new URI("ws://your.machine:1025")) };
		var exceptionMessage = "unexpected";

		class MyServer extends RestrictedTestServer {

			public MyServer() throws DeploymentException, IOException {
				super(PORT);
			}

			@Override
			protected void onAddPeers(AddPeersMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new IllegalArgumentException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.addPeers(Stream.of(peers)));
		}
	}

	@Test
	@DisplayName("removePeers() works")
	public void removePeersWorks() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peers1 = new Peer[] { Peers.of(new URI("ws://my.machine:1024")), Peers.of(new URI("ws://your.machine:1025")) };
		AtomicReference<Peer[]> peers2 = new AtomicReference<>();

		class MyServer extends RestrictedTestServer {

			public MyServer() throws DeploymentException, IOException {
				super(PORT);
			}

			@Override
			protected void onRemovePeers(RemovePeersMessage message, Session session) {
				peers2.set(message.getPeers().toArray(Peer[]::new));
				sendObjectAsync(session, VoidMessages.of(message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemoteRestrictedNodes.of(URI, TIME_OUT)) {
			remote.removePeers(Stream.of(peers1));
			assertArrayEquals(peers1, peers2.get());
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
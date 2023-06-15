package io.mokamint.node.remote.tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.LogManager;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.node.Blocks;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.ExceptionResultMessages;
import io.mokamint.node.messages.GetBlockMessage;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetPeersMessage;
import io.mokamint.node.messages.GetPeersResultMessages;
import io.mokamint.node.remote.RemotePublicNodes;
import io.mokamint.nonce.Deadlines;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

public class RemotePublicNodeTests {
	
	@Test
	@DisplayName("getPeers() works")
	public void getPeersWorks() throws DeploymentException, IOException, URISyntaxException {
		var peers1 = new Peer[] { Peers.of(new URI("ws://my.machine:1024")), Peers.of(new URI("ws://your.machine:1025")) };

		class MyServer extends TestServer {

			public MyServer() throws DeploymentException, IOException {
				super(8025);
			}

			@Override
			protected void onGetPeers(GetPeersMessage message, Session session) {
				sendObjectAsync(session, GetPeersResultMessages.of(Stream.of(peers1), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(new URI("ws://localhost:8025"))) {
			var peers2 = remote.getPeers();
			assertArrayEquals(peers1, peers2.toArray(Peer[]::new));
		}
	}

	@Test
	@DisplayName("getBlock() works if the block exists")
	public void getBlockWorksIfBlockExists() throws DeploymentException, IOException, NoSuchAlgorithmException, URISyntaxException {
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashing);
		var block1 = Blocks.of(13, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[] { 1, 2, 3, 4, 5, 6});
		var hash = new byte[] { 67, 56, 43 };

		class MyServer extends TestServer {

			public MyServer() throws DeploymentException, IOException {
				super(8025);
			}

			@Override
			protected void onGetBlock(GetBlockMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetBlockResultMessages.of(Optional.of(block1), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(new URI("ws://localhost:8025"))) {
			var block2 = remote.getBlock(hash);
			assertEquals(block1, block2.get());
		}
	}

	@Test
	@DisplayName("getBlock() works if the block is missing")
	public void getBlockWorksIfBlockMissing() throws DeploymentException, IOException, NoSuchAlgorithmException, URISyntaxException {
		var hash = new byte[] { 67, 56, 43 };

		class MyServer extends TestServer {

			public MyServer() throws DeploymentException, IOException {
				super(8025);
			}

			@Override
			protected void onGetBlock(GetBlockMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetBlockResultMessages.of(Optional.empty(), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(new URI("ws://localhost:8025"))) {
			var block = remote.getBlock(hash);
			assertTrue(block.isEmpty());
		}
	}

	@Test
	@DisplayName("getBlock() works if it throws NoSuchAlgorithmException")
	public void getBlockWorksInCaseOfNoSuchAlgorithmException() throws DeploymentException, IOException, NoSuchAlgorithmException, URISyntaxException {
		var hash = new byte[] { 67, 56, 43 };
		var exceptionMessage = "sha345";

		class MyServer extends TestServer {

			public MyServer() throws DeploymentException, IOException {
				super(8025);
			}

			@Override
			protected void onGetBlock(GetBlockMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, ExceptionResultMessages.of(new NoSuchAlgorithmException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(new URI("ws://localhost:8025"))) {
			var exception = assertThrows(NoSuchAlgorithmException.class, () -> remote.getBlock(hash));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = RemotePublicNodeTests.class.getClassLoader().getResource("logging.properties");
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
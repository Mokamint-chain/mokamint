package io.mokamint.node.integration.tests;

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
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.LogManager;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.ConsensusConfigs;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.Versions;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.GetBlockMessage;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoMessage;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetConfigMessage;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetInfoMessage;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.GetPeersMessage;
import io.mokamint.node.messages.GetPeersResultMessages;
import io.mokamint.node.messages.SuggestPeersMessage;
import io.mokamint.node.messages.SuggestPeersResultMessages;
import io.mokamint.node.remote.RemotePublicNodes;
import io.mokamint.node.service.AbstractPublicNodeService;
import io.mokamint.nonce.Deadlines;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

public class RemotePublicNodeTests {
	private final static URI URI;
	private final static int PORT = 8030;

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
	private static class PublicTestServer extends AbstractPublicNodeService {

		/**
		 * Creates a new test server.
		 * 
		 * @throws DeploymentException if the service cannot be deployed
		 * @throws IOException if an I/O error occurs
		 */
		private PublicTestServer() throws DeploymentException, IOException {
			super(PORT);
			deploy();
		}
	}

	@Test
	@DisplayName("getPeers() works")
	public void getPeersWorks() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peers1 = new Peer[] { Peers.of(new URI("ws://my.machine:1024")), Peers.of(new URI("ws://your.machine:1025")) };

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeers(GetPeersMessage message, Session session) {
				sendObjectAsync(session, GetPeersResultMessages.of(Stream.of(peers1), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var peers2 = remote.getPeers();
			assertArrayEquals(peers1, peers2.toArray(Peer[]::new));
		}
	}

	@Test
	@DisplayName("getPeers() works if it throws TimeoutException")
	public void getPeersWorksInCaseOfTimeoutException() throws DeploymentException, IOException, NoSuchAlgorithmException {
		var exceptionMessage = "time-out";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeers(GetPeersMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new TimeoutException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(TimeoutException.class, () -> remote.getPeers());
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getPeers() works if it throws InterruptedException")
	public void getPeersWorksInCaseOfInterruptedException() throws DeploymentException, IOException, NoSuchAlgorithmException {
		var exceptionMessage = "interrupted";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeers(GetPeersMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new InterruptedException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(InterruptedException.class, () -> remote.getPeers());
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("if getPeers() is slow, it leads to a time-out")
	public void getPeersWorksInCaseOfTimeout() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peers1 = new Peer[] { Peers.of(new URI("ws://my.machine:1024")), Peers.of(new URI("ws://your.machine:1025")) };

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeers(GetPeersMessage message, Session session) {
				try {
					Thread.sleep(TIME_OUT * 4); // <----
				}
				catch (InterruptedException e) {}

				sendObjectAsync(session, GetPeersResultMessages.of(Stream.of(peers1), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.getPeers());
		}
	}

	@Test
	@DisplayName("getPeers() ignores unexpected exceptions")
	public void getPeersWorksInCaseOfUnexpectedException() throws DeploymentException, IOException, NoSuchAlgorithmException {
		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeers(GetPeersMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new IllegalArgumentException(), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.getPeers());
		}
	}

	@Test
	@DisplayName("getPeers() ignores unexpected messages")
	public void getPeersWorksInCaseOfUnexpectedMessage() throws DeploymentException, IOException, NoSuchAlgorithmException {
		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeers(GetPeersMessage message, Session session) {
				sendObjectAsync(session, GetBlockResultMessages.of(Optional.empty(), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.getPeers());
		}
	}

	@Test
	@DisplayName("getBlock() works if the block exists")
	public void getBlockWorksIfBlockExists() throws DeploymentException, IOException, NoSuchAlgorithmException, TimeoutException, InterruptedException {
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashing);
		var block1 = Blocks.of(13, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[] { 1, 2, 3, 4, 5, 6});
		var hash = new byte[] { 67, 56, 43 };

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetBlock(GetBlockMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetBlockResultMessages.of(Optional.of(block1), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var block2 = remote.getBlock(hash);
			assertEquals(block1, block2.get());
		}
	}

	@Test
	@DisplayName("getBlock() works if the block is missing")
	public void getBlockWorksIfBlockMissing() throws DeploymentException, IOException, NoSuchAlgorithmException, TimeoutException, InterruptedException {
		var hash = new byte[] { 67, 56, 43 };

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetBlock(GetBlockMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetBlockResultMessages.of(Optional.empty(), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var block = remote.getBlock(hash);
			assertTrue(block.isEmpty());
		}
	}

	@Test
	@DisplayName("getBlock() works if it throws NoSuchAlgorithmException")
	public void getBlockWorksInCaseOfNoSuchAlgorithmException() throws DeploymentException, IOException, NoSuchAlgorithmException {
		var hash = new byte[] { 67, 56, 43 };
		var exceptionMessage = "sha345";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetBlock(GetBlockMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, ExceptionMessages.of(new NoSuchAlgorithmException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(NoSuchAlgorithmException.class, () -> remote.getBlock(hash));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getConfig() works")
	public void getConfigWorks() throws DeploymentException, IOException, NoSuchAlgorithmException, TimeoutException, InterruptedException {
		var config1 = ConsensusConfigs.defaults().build();

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetConfig(GetConfigMessage message, Session session) {
				sendObjectAsync(session, GetConfigResultMessages.of(config1, message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var config2 = remote.getConfig();
			assertEquals(config1, config2);
		}
	}

	@Test
	@DisplayName("getChainInfo() works")
	public void getChainInfoWorks() throws DeploymentException, IOException, NoSuchAlgorithmException, TimeoutException, InterruptedException {
		var info1 = ChainInfos.of(1973L, Optional.of(new byte[] { 1, 2, 3, 4 }), Optional.of(new byte[] { 17, 13, 19 }));

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetChainInfo(GetChainInfoMessage message, Session session) {
				sendObjectAsync(session, GetChainInfoResultMessages.of(info1, message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var info2 = remote.getChainInfo();
			assertEquals(info1, info2);
		}
	}

	@Test
	@DisplayName("getChainInfo() works in case of IOException")
	public void getChainInfoWorksInCaseOfIOException() throws DeploymentException, IOException, TimeoutException, InterruptedException {
		var exceptionMessage = "exception message";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetChainInfo(GetChainInfoMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new IOException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(IOException.class, () -> remote.getChainInfo());
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getChainInfo() works in case of NoSuchAlgorithmException")
	public void getChainInfoWorksInCaseOfNoSuchAlgorithmException() throws DeploymentException, IOException, TimeoutException, InterruptedException {
		var exceptionMessage = "exception message";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetChainInfo(GetChainInfoMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new NoSuchAlgorithmException(exceptionMessage), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(NoSuchAlgorithmException.class, () -> remote.getChainInfo());
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getInfo() works")
	public void getInfoWorks() throws DeploymentException, IOException, TimeoutException, InterruptedException {
		var info1 = NodeInfos.of(Versions.of(1, 2, 3));

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetInfo(GetInfoMessage message, Session session) {
				sendObjectAsync(session, GetInfoResultMessages.of(info1, message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var info2 = remote.getInfo();
			assertEquals(info1, info2);
		}
	}

	@Test
	@DisplayName("suggestPeer() works")
	public void suggestPeerWorks() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peers1 = new HashSet<Peer>();
		peers1.add(Peers.of(new URI("ws://my.machine:1024")));
		peers1.add(Peers.of(new URI("ws://your.machine:1025")));
		var peers2 = new HashSet<Peer>();

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onSuggestPeers(SuggestPeersMessage message, Session session) {
				message.getPeers().forEach(peers2::add);;
				sendObjectAsync(session, SuggestPeersResultMessages.of(message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			remote.suggestPeers(peers1.stream());
			assertEquals(peers1, peers2);
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
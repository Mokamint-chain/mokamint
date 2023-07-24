package io.mokamint.node.integration.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.ConsensusConfigs;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.Versions;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.GetPeerInfosResultMessages;
import io.mokamint.node.messages.WhisperPeersMessages;
import io.mokamint.node.messages.api.GetBlockMessage;
import io.mokamint.node.messages.api.GetChainInfoMessage;
import io.mokamint.node.messages.api.GetConfigMessage;
import io.mokamint.node.messages.api.GetInfoMessage;
import io.mokamint.node.messages.api.GetPeerInfosMessage;
import io.mokamint.node.messages.api.WhisperPeersMessage;
import io.mokamint.node.messages.api.Whisperer;
import io.mokamint.node.remote.RemotePublicNodes;
import io.mokamint.node.service.internal.PublicNodeServiceImpl;
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
	private static class PublicTestServer extends PublicNodeServiceImpl {

		/**
		 * Creates a new test server.
		 * 
		 * @throws DeploymentException if the service cannot be deployed
		 * @throws IOException if an I/O error occurs
		 */
		private PublicTestServer() throws DeploymentException, IOException {
			super(mock(), PORT, Optional.empty());
		}
	}

	@Test
	@DisplayName("getPeers() works")
	public void getPeersWorks() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException, ClosedNodeException {
		var peerInfos1 = Set.of(PeerInfos.of(Peers.of(new URI("ws://my.machine:1024")), 345, true), PeerInfos.of(Peers.of(new URI("ws://your.machine:1025")), 11, false));

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeerInfos(GetPeerInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, GetPeerInfosResultMessages.of(peerInfos1.stream(), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var peerInfos2 = remote.getPeerInfos();
			assertEquals(peerInfos1, peerInfos2.collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("getPeers() works if it throws TimeoutException")
	public void getPeersWorksInCaseOfTimeoutException() throws DeploymentException, IOException, NoSuchAlgorithmException, InterruptedException {
		var exceptionMessage = "time-out";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeerInfos(GetPeerInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new TimeoutException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(TimeoutException.class, () -> remote.getPeerInfos());
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getPeers() works if it throws ClosedNodeException")
	public void getPeersWorksInCaseOfClosedNodeException() throws DeploymentException, IOException, NoSuchAlgorithmException, InterruptedException {
		var exceptionMessage = "node is closed";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeerInfos(GetPeerInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new ClosedNodeException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(ClosedNodeException.class, () -> remote.getPeerInfos());
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getPeers() works if it throws InterruptedException")
	public void getPeersWorksInCaseOfInterruptedException() throws DeploymentException, IOException, NoSuchAlgorithmException, InterruptedException {
		var exceptionMessage = "interrupted";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeerInfos(GetPeerInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new InterruptedException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(InterruptedException.class, () -> remote.getPeerInfos());
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("if getPeers() is slow, it leads to a time-out")
	public void getPeersWorksInCaseOfTimeout() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException {
		var peerInfos1 = Set.of(PeerInfos.of(Peers.of(new URI("ws://my.machine:1024")), 345, true), PeerInfos.of(Peers.of(new URI("ws://your.machine:1025")), 11, false));

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeerInfos(GetPeerInfosMessage message, Session session) {
				try {
					Thread.sleep(TIME_OUT * 4); // <----
				}
				catch (InterruptedException e) {}

				try {
					sendObjectAsync(session, GetPeerInfosResultMessages.of(peerInfos1.stream(), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.getPeerInfos());
		}
	}

	@Test
	@DisplayName("getPeers() ignores unexpected exceptions")
	public void getPeersWorksInCaseOfUnexpectedException() throws DeploymentException, IOException, NoSuchAlgorithmException, InterruptedException {
		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeerInfos(GetPeerInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new IllegalArgumentException(), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.getPeerInfos());
		}
	}

	@Test
	@DisplayName("getPeers() ignores unexpected messages")
	public void getPeersWorksInCaseOfUnexpectedMessage() throws DeploymentException, IOException, NoSuchAlgorithmException, InterruptedException {
		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeerInfos(GetPeerInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, GetBlockResultMessages.of(Optional.empty(), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.getPeerInfos());
		}
	}

	@Test
	@DisplayName("getBlock() works if the block exists")
	public void getBlockWorksIfBlockExists() throws DeploymentException, IOException, DatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException {
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashing);
		var block1 = Blocks.of(13, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[] { 1, 2, 3, 4, 5, 6});
		var hash = new byte[] { 67, 56, 43 };

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetBlock(GetBlockMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					try {
						sendObjectAsync(session, GetBlockResultMessages.of(Optional.of(block1), message.getId()));
					}
					catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var block2 = remote.getBlock(hash);
			assertEquals(block1, block2.get());
		}
	}

	@Test
	@DisplayName("getBlock() works if the block is missing")
	public void getBlockWorksIfBlockMissing() throws DeploymentException, IOException, DatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException {
		var hash = new byte[] { 67, 56, 43 };

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetBlock(GetBlockMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					try {
						sendObjectAsync(session, GetBlockResultMessages.of(Optional.empty(), message.getId()));
					}
					catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var block = remote.getBlock(hash);
			assertTrue(block.isEmpty());
		}
	}

	@Test
	@DisplayName("getBlock() works if it throws NoSuchAlgorithmException")
	public void getBlockWorksInCaseOfNoSuchAlgorithmException() throws DeploymentException, IOException, NoSuchAlgorithmException, InterruptedException {
		var hash = new byte[] { 67, 56, 43 };
		var exceptionMessage = "sha345";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetBlock(GetBlockMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					try {
						sendObjectAsync(session, ExceptionMessages.of(new NoSuchAlgorithmException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(NoSuchAlgorithmException.class, () -> remote.getBlock(hash));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getBlock() works if it throws DatabaseException")
	public void getBlockWorksInCaseOfDatabaseException() throws DeploymentException, IOException, NoSuchAlgorithmException, InterruptedException {
		var hash = new byte[] { 67, 56, 43 };
		var exceptionMessage = "corrupted database";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetBlock(GetBlockMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					try {
						sendObjectAsync(session, ExceptionMessages.of(new DatabaseException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(DatabaseException.class, () -> remote.getBlock(hash));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getConfig() works")
	public void getConfigWorks() throws DeploymentException, IOException, NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException {
		var config1 = ConsensusConfigs.defaults().build();

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetConfig(GetConfigMessage message, Session session) {
				try {
					sendObjectAsync(session, GetConfigResultMessages.of(config1, message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var config2 = remote.getConfig();
			assertEquals(config1, config2);
		}
	}

	@Test
	@DisplayName("getChainInfo() works")
	public void getChainInfoWorks() throws DeploymentException, IOException, DatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException {
		var info1 = ChainInfos.of(1973L, Optional.of(new byte[] { 1, 2, 3, 4 }), Optional.of(new byte[] { 17, 13, 19 }));

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetChainInfo(GetChainInfoMessage message, Session session) {
				try {
					sendObjectAsync(session, GetChainInfoResultMessages.of(info1, message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var info2 = remote.getChainInfo();
			assertEquals(info1, info2);
		}
	}

	@Test
	@DisplayName("getChainInfo() works in case of DatabaseException")
	public void getChainInfoWorksInCaseOfDatabaseException() throws DeploymentException, IOException, TimeoutException, InterruptedException {
		var exceptionMessage = "exception message";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetChainInfo(GetChainInfoMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new DatabaseException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(DatabaseException.class, () -> remote.getChainInfo());
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
				try {
					sendObjectAsync(session, ExceptionMessages.of(new NoSuchAlgorithmException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(NoSuchAlgorithmException.class, () -> remote.getChainInfo());
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getInfo() works")
	public void getInfoWorks() throws DeploymentException, IOException, TimeoutException, InterruptedException, ClosedNodeException {
		var info1 = NodeInfos.of(Versions.of(1, 2, 3), UUID.randomUUID());
	
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetInfo(GetInfoMessage message, Session session) {
				try {
					sendObjectAsync(session, GetInfoResultMessages.of(info1, message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var info2 = remote.getInfo();
			assertEquals(info1, info2);
		}
	}

	@Test
	@DisplayName("if a service suggests some peers, a remote will inform its bound whisperers")
	public void ifServiceSuggestsPeersTheRemoteInformsBoundWhisperers() throws DeploymentException, IOException, TimeoutException, InterruptedException, URISyntaxException {
		var peers = Set.of(Peers.of(new URI("ws://my.machine:8032")), Peers.of(new URI("ws://her.machine:8033")));
		var semaphore = new Semaphore(0);

		var whisperer = new Whisperer() {

			@Override
			public void whisper(WhisperPeersMessage message, Predicate<Whisperer> seen) {
				if (message.getPeers().collect(Collectors.toSet()).containsAll(peers))
					semaphore.release();
			}
		};

		try (var service = new PublicTestServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			remote.bindWhisperer(whisperer);
			service.whisper(WhisperPeersMessages.of(peers.stream(), UUID.randomUUID().toString()), _whisperer -> false);
			semaphore.acquire();
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
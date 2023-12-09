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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.stubbing.OngoingStubbing;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.ChainPortions;
import io.mokamint.node.ConsensusConfigBuilders;
import io.mokamint.node.MempoolInfos;
import io.mokamint.node.MempoolPortions;
import io.mokamint.node.MinerInfos;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.SanitizedStrings;
import io.mokamint.node.TaskInfos;
import io.mokamint.node.MempoolEntries;
import io.mokamint.node.Transactions;
import io.mokamint.node.Versions;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.Whisperer;
import io.mokamint.node.messages.AddTransactionResultMessages;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.GetBlockDescriptionResultMessages;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetChainPortionResultMessages;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.GetMempoolInfoResultMessages;
import io.mokamint.node.messages.GetMempoolPortionResultMessages;
import io.mokamint.node.messages.GetMinerInfosResultMessages;
import io.mokamint.node.messages.GetPeerInfosResultMessages;
import io.mokamint.node.messages.GetTaskInfosResultMessages;
import io.mokamint.node.messages.WhisperBlockMessages;
import io.mokamint.node.messages.WhisperPeersMessages;
import io.mokamint.node.messages.api.AddTransactionMessage;
import io.mokamint.node.messages.api.GetBlockDescriptionMessage;
import io.mokamint.node.messages.api.GetBlockMessage;
import io.mokamint.node.messages.api.GetChainInfoMessage;
import io.mokamint.node.messages.api.GetChainPortionMessage;
import io.mokamint.node.messages.api.GetConfigMessage;
import io.mokamint.node.messages.api.GetInfoMessage;
import io.mokamint.node.messages.api.GetMempoolInfoMessage;
import io.mokamint.node.messages.api.GetMempoolPortionMessage;
import io.mokamint.node.messages.api.GetMinerInfosMessage;
import io.mokamint.node.messages.api.GetPeerInfosMessage;
import io.mokamint.node.messages.api.GetTaskInfosMessage;
import io.mokamint.node.messages.api.WhisperBlockMessage;
import io.mokamint.node.messages.api.WhisperPeersMessage;
import io.mokamint.node.remote.RemotePublicNodes;
import io.mokamint.node.service.internal.PublicNodeServiceImpl;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

public class RemotePublicNodeTests extends AbstractLoggedTests {
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
			super(mockedNode(), PORT, 180000L, 1000, Optional.empty());
		}

		private static PublicNode mockedNode() throws IOException {
			PublicNode result = mock();
			
			try {
				var config = ConsensusConfigBuilders.defaults().build();
				// compilation fails if the following is not split in two...
				OngoingStubbing<ConsensusConfig<?,?>> x = when(result.getConfig());
				x.thenReturn(config);
				//when(result.getConfig()).thenReturn(config);
				return result;
			}
			catch (InterruptedException | NoSuchAlgorithmException | TimeoutException | ClosedNodeException e) {
				throw new IOException(e);
			}
		}
	}

	@Test
	@DisplayName("getPeerInfos() works")
	public void getPeerInfosWorks() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException, ClosedNodeException {
		var peerInfos1 = Set.of(PeerInfos.of(Peers.of(new URI("ws://my.machine:1024")), 345, true),
				PeerInfos.of(Peers.of(new URI("ws://your.machine:1025")), 11, false));

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
	@DisplayName("getPeerInfos() works if it throws TimeoutException")
	public void getPeerInfosWorksInCaseOfTimeoutException() throws DeploymentException, IOException, InterruptedException {
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
			var exception = assertThrows(TimeoutException.class, remote::getPeerInfos);
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getPeerInfos() works if it throws ClosedNodeException")
	public void getPeerInfosWorksInCaseOfClosedNodeException() throws DeploymentException, IOException, InterruptedException {
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
			var exception = assertThrows(ClosedNodeException.class, remote::getPeerInfos);
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getPeerInfos() works if it throws InterruptedException")
	public void getPeerInfosWorksInCaseOfInterruptedException() throws DeploymentException, IOException, InterruptedException {
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
			var exception = assertThrows(InterruptedException.class, remote::getPeerInfos);
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("if getPeerInfos() is slow, it leads to a time-out")
	public void getPeerInfosWorksInCaseOfTimeout() throws DeploymentException, IOException, URISyntaxException, InterruptedException {
		var peerInfos1 = Set.of(PeerInfos.of(Peers.of(new URI("ws://my.machine:1024")), 345, true),
				PeerInfos.of(Peers.of(new URI("ws://your.machine:1025")), 11, false));

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
			assertThrows(TimeoutException.class, remote::getPeerInfos);
		}
	}

	@Test
	@DisplayName("getPeerInfos() ignores unexpected exceptions")
	public void getPeerInfosWorksInCaseOfUnexpectedException() throws DeploymentException, IOException, InterruptedException {
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
			assertThrows(TimeoutException.class, remote::getPeerInfos);
		}
	}

	@Test
	@DisplayName("getPeerInfos() ignores unexpected messages")
	public void getPeerInfosWorksInCaseOfUnexpectedMessage() throws DeploymentException, IOException, InterruptedException {
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
			assertThrows(TimeoutException.class, remote::getPeerInfos);
		}
	}

	@Test
	@DisplayName("getMinerInfos() works")
	public void getMinerInfosWorks() throws DeploymentException, IOException, TimeoutException, InterruptedException, ClosedNodeException {
		var minerInfos1 = Set.of(MinerInfos.of(UUID.randomUUID(), 345L, "a miner"),
			MinerInfos.of(UUID.randomUUID(), 11L, "a special miner"));
	
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetMinerInfos(GetMinerInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, GetMinerInfosResultMessages.of(minerInfos1.stream(), message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var minerInfos2 = remote.getMinerInfos();
			assertEquals(minerInfos1, minerInfos2.collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("getMinerInfos() works if it throws TimeoutException")
	public void getMinerInfosWorksInCaseOfTimeoutException() throws DeploymentException, IOException, InterruptedException {
		var exceptionMessage = "time-out";
	
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetMinerInfos(GetMinerInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new TimeoutException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(TimeoutException.class, remote::getMinerInfos);
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getMinerInfos() works if it throws ClosedNodeException")
	public void getMinerInfosWorksInCaseOfClosedNodeException() throws DeploymentException, IOException, InterruptedException {
		var exceptionMessage = "node is closed";
	
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetMinerInfos(GetMinerInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new ClosedNodeException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(ClosedNodeException.class, remote::getMinerInfos);
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getMinerInfos() works if it throws InterruptedException")
	public void getMinerInfosWorksInCaseOfInterruptedException() throws DeploymentException, IOException, InterruptedException {
		var exceptionMessage = "interrupted";
	
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetMinerInfos(GetMinerInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new InterruptedException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(InterruptedException.class, remote::getMinerInfos);
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("if getMinerInfos() is slow, it leads to a time-out")
	public void getMinerInfosWorksInCaseOfTimeout() throws DeploymentException, IOException, InterruptedException {
		var minerInfos1 = Set.of(MinerInfos.of(UUID.randomUUID(), 345L, "a miner"),
				MinerInfos.of(UUID.randomUUID(), 11L, "a special miner"));
	
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetMinerInfos(GetMinerInfosMessage message, Session session) {
				try {
					Thread.sleep(TIME_OUT * 4); // <----
				}
				catch (InterruptedException e) {}
	
				try {
					sendObjectAsync(session, GetMinerInfosResultMessages.of(minerInfos1.stream(), message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getMinerInfos);
		}
	}

	@Test
	@DisplayName("getMinerInfos() ignores unexpected exceptions")
	public void getMinerInfosWorksInCaseOfUnexpectedException() throws DeploymentException, IOException, InterruptedException {
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetMinerInfos(GetMinerInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new IllegalArgumentException(), message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getMinerInfos);
		}
	}

	@Test
	@DisplayName("getMinerInfos() ignores unexpected messages")
	public void getMinerInfosWorksInCaseOfUnexpectedMessage() throws DeploymentException, IOException, InterruptedException {
		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetMinerInfos(GetMinerInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, GetBlockResultMessages.of(Optional.empty(), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getMinerInfos);
		}
	}

	@Test
	@DisplayName("getTaskInfos() works")
	public void getTaskInfosWorks() throws DeploymentException, IOException, TimeoutException, InterruptedException, ClosedNodeException {
		var taskInfos1 = Set.of(TaskInfos.of("a wonderful task"), TaskInfos.of("a special task"));
	
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetTaskInfos(GetTaskInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, GetTaskInfosResultMessages.of(taskInfos1.stream(), message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var taskInfos2 = remote.getTaskInfos();
			assertEquals(taskInfos1, taskInfos2.collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("getTaskInfos() works if it throws TimeoutException")
	public void getTaskInfosWorksInCaseOfTimeoutException() throws DeploymentException, IOException, InterruptedException {
		var exceptionMessage = "time-out";
	
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetTaskInfos(GetTaskInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new TimeoutException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(TimeoutException.class, remote::getTaskInfos);
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getTaskInfos() works if it throws ClosedNodeException")
	public void getTaskInfosWorksInCaseOfClosedNodeException() throws DeploymentException, IOException, InterruptedException {
		var exceptionMessage = "node is closed";
	
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetTaskInfos(GetTaskInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new ClosedNodeException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(ClosedNodeException.class, remote::getTaskInfos);
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getTaskInfos() works if it throws InterruptedException")
	public void getTaskInfosWorksInCaseOfInterruptedException() throws DeploymentException, IOException, InterruptedException {
		var exceptionMessage = "interrupted";
	
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetTaskInfos(GetTaskInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new InterruptedException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(InterruptedException.class, remote::getTaskInfos);
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("if getTaskInfos() is slow, it leads to a time-out")
	public void getTaskInfosWorksInCaseOfTimeout() throws DeploymentException, IOException, InterruptedException {
		var taskInfos1 = Set.of(TaskInfos.of("a task"), TaskInfos.of("a special task"));
	
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetTaskInfos(GetTaskInfosMessage message, Session session) {
				try {
					Thread.sleep(TIME_OUT * 4); // <----
				}
				catch (InterruptedException e) {}
	
				try {
					sendObjectAsync(session, GetTaskInfosResultMessages.of(taskInfos1.stream(), message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getTaskInfos);
		}
	}

	@Test
	@DisplayName("getTaskInfos() ignores unexpected exceptions")
	public void getTaskInfosWorksInCaseOfUnexpectedException() throws DeploymentException, IOException, InterruptedException {
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetTaskInfos(GetTaskInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new IllegalArgumentException(), message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getTaskInfos);
		}
	}

	@Test
	@DisplayName("getTaskInfos() ignores unexpected messages")
	public void getTaskInfosWorksInCaseOfUnexpectedMessage() throws DeploymentException, IOException, InterruptedException {
		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetTaskInfos(GetTaskInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, GetBlockResultMessages.of(Optional.empty(), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getTaskInfos);
		}
	}

	@Test
	@DisplayName("getBlock() works if the block exists")
	public void getBlockWorksIfBlockExists() throws DeploymentException, IOException, DatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException, InvalidKeyException, SignatureException {
		var hashing = HashingAlgorithms.shabal256();
		var value = new byte[hashing.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, 11, new byte[] { 90, 91, 92 }, hashing, plotKeyPair.getPrivate());
		var transaction1 = Transactions.of(new byte[] { 13, 17, 23, 31 });
		var transaction2 = Transactions.of(new byte[] { 5, 6, 7 });
		var transaction3 = Transactions.of(new byte[] {});
		var block1 = Blocks.of(BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[] { 1, 2, 3, 4, 5, 6}),
			Stream.of(transaction1, transaction2, transaction3),
			new byte[0], nodeKeyPair.getPrivate());
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
	@DisplayName("getBlockDescription() works if the block exists")
	public void getBlockDescriptionWorksIfBlockExists() throws DeploymentException, IOException, DatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException, InvalidKeyException, SignatureException {
		var hashing = HashingAlgorithms.shabal256();
		var value = new byte[hashing.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, 11, new byte[] { 90, 91, 92 }, hashing, plotKeyPair.getPrivate());
		var description1 = BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[] { 1, 2, 3, 4, 5, 6});
		var hash = new byte[] { 67, 56, 43 };

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetBlockDescription(GetBlockDescriptionMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					try {
						sendObjectAsync(session, GetBlockDescriptionResultMessages.of(Optional.of(description1), message.getId()));
					}
					catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var description2 = remote.getBlockDescription(hash);
			assertEquals(description1, description2.get());
		}
	}

	@Test
	@DisplayName("getBlockDescription() works if the block is missing")
	public void getBlockDescriptionWorksIfBlockMissing() throws DeploymentException, IOException, DatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException {
		var hash = new byte[] { 67, 56, 43 };

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetBlockDescription(GetBlockDescriptionMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					try {
						sendObjectAsync(session, GetBlockDescriptionResultMessages.of(Optional.empty(), message.getId()));
					}
					catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var description = remote.getBlockDescription(hash);
			assertTrue(description.isEmpty());
		}
	}

	@Test
	@DisplayName("getBlockDescription() works if it throws NoSuchAlgorithmException")
	public void getBlockDescriptionWorksInCaseOfNoSuchAlgorithmException() throws DeploymentException, IOException, NoSuchAlgorithmException, InterruptedException {
		var hash = new byte[] { 67, 56, 43 };
		var exceptionMessage = "sha345";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetBlockDescription(GetBlockDescriptionMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					try {
						sendObjectAsync(session, ExceptionMessages.of(new NoSuchAlgorithmException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(NoSuchAlgorithmException.class, () -> remote.getBlockDescription(hash));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getBlockDescription() works if it throws DatabaseException")
	public void getBlockDescriptionWorksInCaseOfDatabaseException() throws DeploymentException, IOException, NoSuchAlgorithmException, InterruptedException {
		var hash = new byte[] { 67, 56, 43 };
		var exceptionMessage = "corrupted database";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetBlockDescription(GetBlockDescriptionMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					try {
						sendObjectAsync(session, ExceptionMessages.of(new DatabaseException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(DatabaseException.class, () -> remote.getBlockDescription(hash));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getConfig() works")
	public void getConfigWorks() throws DeploymentException, IOException, NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException {
		var config1 = ConsensusConfigBuilders.defaults().build();

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
	public void getChainInfoWorks() throws DeploymentException, IOException, DatabaseException, TimeoutException, InterruptedException, ClosedNodeException {
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
	@DisplayName("getChainPortion() works")
	public void getChainPortionWorks() throws DeploymentException, IOException, DatabaseException, TimeoutException, InterruptedException, ClosedNodeException {
		var chain1 = ChainPortions.of(Stream.of(new byte[] { 1, 2, 3, 4 }, new byte[] { 17, 13, 19 }));

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetChainPortion(GetChainPortionMessage message, Session session) {
				try {
					sendObjectAsync(session, GetChainPortionResultMessages.of(chain1, message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var chain2 = remote.getChainPortion(10, 20);
			assertEquals(chain1, chain2);
		}
	}

	@Test
	@DisplayName("getChainPortion() works in case of DatabaseException")
	public void getChainPortionWorksInCaseOfDatabaseException() throws DeploymentException, IOException, TimeoutException, InterruptedException {
		var exceptionMessage = "exception message";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetChainPortion(GetChainPortionMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new DatabaseException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(DatabaseException.class, () -> remote.getChainPortion(10, 20));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getInfo() works")
	public void getInfoWorks() throws DeploymentException, IOException, TimeoutException, InterruptedException, ClosedNodeException {
		var info1 = NodeInfos.of(Versions.of(1, 2, 3), UUID.randomUUID(), LocalDateTime.now(ZoneId.of("UTC")));
	
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
	@DisplayName("getMempoolInfo() works")
	public void getMempoolInfoWorks() throws DeploymentException, IOException, TimeoutException, InterruptedException, ClosedNodeException {
		var info1 = MempoolInfos.of(17L);
	
		class MyServer extends PublicTestServer {
	
			private MyServer() throws DeploymentException, IOException {}
	
			@Override
			protected void onGetMempoolInfo(GetMempoolInfoMessage message, Session session) {
				try {
					sendObjectAsync(session, GetMempoolInfoResultMessages.of(info1, message.getId()));
				}
				catch (IOException e) {}
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var info2 = remote.getMempoolInfo();
			assertEquals(info1, info2);
		}
	}

	@Test
	@DisplayName("add(Transaction) works")
	public void addTransactionWorks() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException, DatabaseException, ClosedNodeException, RejectedTransactionException, NoSuchAlgorithmException {
		var transaction1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
		var transaction2 = new AtomicReference<Transaction>();

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddTransaction(AddTransactionMessage message, Session session) {
				transaction2.set(message.getTransaction());
				try {
					sendObjectAsync(session, AddTransactionResultMessages.of(MempoolEntries.of(new byte[] { 1 , 2 }, 13L), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			remote.add(transaction1);
			assertEquals(transaction1, transaction2.get());
		}
	}

	@Test
	@DisplayName("add(Transaction) works in case of RejectedTransactionException")
	public void addTransactionWorksInCaseOfRejectedTransactionException() throws DeploymentException, IOException, TimeoutException, InterruptedException {
		var exceptionMessage = "exception message";
		var transaction = Transactions.of(new byte[] { 1, 2, 3, 4 });

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddTransaction(AddTransactionMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new RejectedTransactionException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(RejectedTransactionException.class, () -> remote.add(transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("add(Transaction) works in case of NoSuchAlgorithmException")
	public void addTransactionWorksInCaseOfNoSuchAlgorithmException() throws DeploymentException, IOException, TimeoutException, InterruptedException {
		var exceptionMessage = "exception message";
		var transaction = Transactions.of(new byte[] { 1, 2, 3, 4 });

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddTransaction(AddTransactionMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new NoSuchAlgorithmException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(NoSuchAlgorithmException.class, () -> remote.add(transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("add(Transaction) works in case of DatabaseException")
	public void addTransactionWorksInCaseOfDatabaseException() throws DeploymentException, IOException, TimeoutException, InterruptedException {
		var exceptionMessage = "exception message";
		var transaction = Transactions.of(new byte[] { 1, 2, 3, 4 });

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAddTransaction(AddTransactionMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new DatabaseException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(DatabaseException.class, () -> remote.add(transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getMempoolPortion() works")
	public void getMempoolPortionWorks() throws DeploymentException, IOException, DatabaseException, TimeoutException, InterruptedException, ClosedNodeException {
		var mempool1 = MempoolPortions.of(Stream.of(
			MempoolEntries.of(new byte[] { 1, 2, 3, 4 }, 11L),
			MempoolEntries.of(new byte[] { 17, 13, 19 }, 17L)
		));

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetMempoolPortion(GetMempoolPortionMessage message, Session session) {
				try {
					sendObjectAsync(session, GetMempoolPortionResultMessages.of(mempool1, message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var mempool2 = remote.getMempoolPortion(10, 20);
			assertEquals(mempool1, mempool2);
		}
	}

	@Test
	@Timeout(10)
	@DisplayName("if a service whispers some peers, a remote will inform its bound whisperers")
	public void ifServiceWhispersPeersTheRemoteInformsBoundWhisperers() throws DeploymentException, IOException, TimeoutException, InterruptedException, URISyntaxException {
		var peers = Set.of(Peers.of(new URI("ws://my.machine:8032")), Peers.of(new URI("ws://her.machine:8033")));
		var semaphore = new Semaphore(0);
		var whisperer = mock(Whisperer.class);
		
		doAnswer(invocation -> {
			WhisperPeersMessage message = invocation.getArgument(0);

			if (message.getPeers().collect(Collectors.toSet()).containsAll(peers))
				semaphore.release();

			return null;
	    })
		.when(whisperer).whisper(any(WhisperPeersMessage.class), any(), any());

		try (var service = new PublicTestServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			remote.bindWhisperer(whisperer);
			service.whisper(WhisperPeersMessages.of(peers.stream(), UUID.randomUUID().toString()), _whisperer -> false, "peers " + SanitizedStrings.of(peers.stream()));
			semaphore.acquire();
		}
	}

	@Test
	@Timeout(10)
	@DisplayName("if a service whispers a block, a remote will inform its bound whisperers")
	public void ifServiceWhispersBlockTheRemoteInformsBoundWhisperers() throws DeploymentException, IOException, TimeoutException, InterruptedException, URISyntaxException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		var hashing = HashingAlgorithms.shabal256();
		var value = new byte[hashing.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, 11, new byte[] { 90, 91, 92 }, hashing, plotKeyPair.getPrivate());
		var transaction1 = Transactions.of(new byte[] { 13, 17, 23, 31 });
		var transaction2 = Transactions.of(new byte[] { 5, 6, 7 });
		var transaction3 = Transactions.of(new byte[] {});
		var block = Blocks.of(BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[] { 1, 2, 3, 4, 5, 6}),
			Stream.of(transaction1, transaction2, transaction3), new byte[0], nodeKeyPair.getPrivate());
		var semaphore = new Semaphore(0);

		var whisperer = mock(Whisperer.class);
		doAnswer(invocation -> {
			WhisperBlockMessage message = invocation.getArgument(0);

			if (message.getBlock().equals(block))
				semaphore.release();

			return null;
	    })
		.when(whisperer).whisper(any(WhisperBlockMessage.class), any(), any());

		try (var service = new PublicTestServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			remote.bindWhisperer(whisperer);
			service.whisper(WhisperBlockMessages.of(block, UUID.randomUUID().toString()), _whisperer -> false, "block " + block.getHexHash(HashingAlgorithms.sha256()));
			semaphore.acquire();
		}
	}
}
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

import static io.hotmoka.crypto.HashingAlgorithms.sha256;
import static io.hotmoka.crypto.HashingAlgorithms.shabal256;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.mokamint.node.BasicConsensusConfigBuilders;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.ChainPortions;
import io.mokamint.node.MempoolEntries;
import io.mokamint.node.MempoolInfos;
import io.mokamint.node.MempoolPortions;
import io.mokamint.node.MinerInfos;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.TaskInfos;
import io.mokamint.node.TransactionAddresses;
import io.mokamint.node.Transactions;
import io.mokamint.node.Versions;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionRejectedException;
import io.mokamint.node.api.Whisperer;
import io.mokamint.node.messages.AddTransactionResultMessages;
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
import io.mokamint.node.messages.GetTransactionAddressResultMessages;
import io.mokamint.node.messages.GetTransactionRepresentationResultMessages;
import io.mokamint.node.messages.GetTransactionResultMessages;
import io.mokamint.node.messages.WhisperBlockMessages;
import io.mokamint.node.messages.WhisperPeerMessages;
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
import io.mokamint.node.messages.api.GetTransactionAddressMessage;
import io.mokamint.node.messages.api.GetTransactionMessage;
import io.mokamint.node.messages.api.GetTransactionRepresentationMessage;
import io.mokamint.node.messages.api.WhisperBlockMessage;
import io.mokamint.node.messages.api.WhisperPeerMessage;
import io.mokamint.node.remote.RemotePublicNodes;
import io.mokamint.node.service.internal.PublicNodeServiceImpl;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import jakarta.websocket.Session;

public class RemotePublicNodeTests extends AbstractLoggedTests {
	private final static int PORT = 8030;
	private final static URI URI = java.net.URI.create("ws://localhost:" + PORT);
	private final static int TIME_OUT = 500;

	/**
	 * Test server implementation.
	 */
	@ThreadSafe
	private static class PublicTestServer extends PublicNodeServiceImpl {

		private PublicTestServer() throws NoSuchAlgorithmException, InterruptedException, TimeoutException, FailedDeploymentException {
			super(mockedNode(), PORT, 180000, 1000, Optional.of(URI));
		}

		private static PublicNode mockedNode() throws InterruptedException, TimeoutException, NoSuchAlgorithmException {
			PublicNode result = mock();
			
			try {
				var config = BasicConsensusConfigBuilders.defaults().build();
				// compilation fails if the following is not split in two...
				OngoingStubbing<ConsensusConfig<?,?>> x = when(result.getConfig());
				x.thenReturn(config);
				//when(result.getConfig()).thenReturn(config);
				return result;
			}
			catch (ClosedNodeException e) {
				throw new RuntimeException(e); // the stubbed node is not closed
			}
		}
	}

	@Test
	@DisplayName("getPeerInfos() works")
	public void getPeerInfosWorks() throws Exception {
		var peerInfos1 = Set.of(PeerInfos.of(Peers.of(java.net.URI.create("ws://my.machine:1024")), 345, true),
				PeerInfos.of(Peers.of(java.net.URI.create("ws://your.machine:1025")), 11, false));

		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetPeerInfos(GetPeerInfosMessage message, Session session) {
				sendObjectAsync(session, GetPeerInfosResultMessages.of(peerInfos1.stream(), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var peerInfos2 = remote.getPeerInfos();
			assertEquals(peerInfos1, peerInfos2.collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("if getPeerInfos() is slow, it leads to a time-out")
	public void getPeerInfosWorksInCaseOfTimeout() throws Exception {
		var peerInfos1 = Set.of(PeerInfos.of(Peers.of(java.net.URI.create("ws://my.machine:1024")), 345, true),
				PeerInfos.of(Peers.of(java.net.URI.create("ws://your.machine:1025")), 11, false));
		var finished = new Semaphore(0);

		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetPeerInfos(GetPeerInfosMessage message, Session session) {
				try {
					Thread.sleep(TIME_OUT * 4); // <----
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}

				sendObjectAsync(session, GetPeerInfosResultMessages.of(peerInfos1.stream(), message.getId()), RuntimeException::new);
				finished.release();
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getPeerInfos);
			// we wait, in order to avoid shutting down the server before the handler completes
			finished.tryAcquire(1, TIME_OUT * 10, TimeUnit.MILLISECONDS);
		}
	}

	@Test
	@DisplayName("getPeerInfos() ignores unexpected exceptions")
	public void getPeerInfosWorksInCaseOfUnexpectedException() throws Exception {
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetPeerInfos(GetPeerInfosMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new IllegalArgumentException(), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getPeerInfos);
		}
	}

	@Test
	@DisplayName("getPeerInfos() ignores unexpected messages")
	public void getPeerInfosWorksInCaseOfUnexpectedMessage() throws Exception {
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetPeerInfos(GetPeerInfosMessage message, Session session) {
				sendObjectAsync(session, GetBlockResultMessages.of(Optional.empty(), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getPeerInfos);
		}
	}

	@Test
	@DisplayName("getMinerInfos() works")
	public void getMinerInfosWorks() throws Exception {
		var minerInfos1 = Set.of(MinerInfos.of(UUID.randomUUID(), 345L, "a miner"),
			MinerInfos.of(UUID.randomUUID(), 11L, "a special miner"));
	
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetMinerInfos(GetMinerInfosMessage message, Session session) {
				sendObjectAsync(session, GetMinerInfosResultMessages.of(minerInfos1.stream(), message.getId()), RuntimeException::new);
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var minerInfos2 = remote.getMinerInfos();
			assertEquals(minerInfos1, minerInfos2.collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("if getMinerInfos() is slow, it leads to a time-out")
	public void getMinerInfosWorksInCaseOfTimeout() throws Exception {
		var minerInfos1 = Set.of(MinerInfos.of(UUID.randomUUID(), 345L, "a miner"),
				MinerInfos.of(UUID.randomUUID(), 11L, "a special miner"));
		var finished = new Semaphore(0);
	
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetMinerInfos(GetMinerInfosMessage message, Session session) {
				try {
					Thread.sleep(TIME_OUT * 4); // <----
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
	
				sendObjectAsync(session, GetMinerInfosResultMessages.of(minerInfos1.stream(), message.getId()), RuntimeException::new);
				finished.release();
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getMinerInfos);
			// we wait, in order to avoid shutting down the server before the handler completes
			finished.tryAcquire(1, TIME_OUT * 10, TimeUnit.MILLISECONDS);
		}
	}

	@Test
	@DisplayName("getMinerInfos() ignores unexpected exceptions")
	public void getMinerInfosWorksInCaseOfUnexpectedException() throws Exception {
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetMinerInfos(GetMinerInfosMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new IllegalArgumentException(), message.getId()), RuntimeException::new);
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getMinerInfos);
		}
	}

	@Test
	@DisplayName("getMinerInfos() ignores unexpected messages")
	public void getMinerInfosWorksInCaseOfUnexpectedMessage() throws Exception {
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetMinerInfos(GetMinerInfosMessage message, Session session) {
				sendObjectAsync(session, GetBlockResultMessages.of(Optional.empty(), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getMinerInfos);
		}
	}

	@Test
	@DisplayName("getTaskInfos() works")
	public void getTaskInfosWorks() throws Exception {
		var taskInfos1 = Set.of(TaskInfos.of("a wonderful task"), TaskInfos.of("a special task"));
	
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetTaskInfos(GetTaskInfosMessage message, Session session) {
				sendObjectAsync(session, GetTaskInfosResultMessages.of(taskInfos1.stream(), message.getId()), RuntimeException::new);
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var taskInfos2 = remote.getTaskInfos();
			assertEquals(taskInfos1, taskInfos2.collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("if getTaskInfos() is slow, it leads to a time-out")
	public void getTaskInfosWorksInCaseOfTimeout() throws Exception {
		var taskInfos1 = Set.of(TaskInfos.of("a task"), TaskInfos.of("a special task"));
		var finished = new Semaphore(0);
	
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetTaskInfos(GetTaskInfosMessage message, Session session) {
				try {
					Thread.sleep(TIME_OUT * 4); // <----
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
	
				sendObjectAsync(session, GetTaskInfosResultMessages.of(taskInfos1.stream(), message.getId()), RuntimeException::new);
				finished.release();
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getTaskInfos);
			// we wait, in order to avoid shutting down the server before the handler completes
			finished.tryAcquire(1, TIME_OUT * 10, TimeUnit.MILLISECONDS);
		}
	}

	@Test
	@DisplayName("getTaskInfos() ignores unexpected exceptions")
	public void getTaskInfosWorksInCaseOfUnexpectedException() throws Exception {
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetTaskInfos(GetTaskInfosMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new IllegalArgumentException(), message.getId()), RuntimeException::new);
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getTaskInfos);
		}
	}

	@Test
	@DisplayName("getTaskInfos() ignores unexpected messages")
	public void getTaskInfosWorksInCaseOfUnexpectedMessage() throws Exception {
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetTaskInfos(GetTaskInfosMessage message, Session session) {
				sendObjectAsync(session, GetBlockResultMessages.of(Optional.empty(), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, remote::getTaskInfos);
		}
	}

	@Test
	@DisplayName("getBlock() works if the block exists")
	public void getBlockWorksIfBlockExists() throws Exception {
		var hashingForDeadlines = shabal256();
		var hashingForBlocks = sha256();
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var hashingForGenerations = sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var hashingOfPreviousBlock = new byte[hashingForBlocks.length()];
		for (int pos = 0; pos < hashingOfPreviousBlock.length; pos++)
			hashingOfPreviousBlock[pos] = (byte) (17 + pos);
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations), plotKeyPair.getPrivate());
		var transaction1 = Transactions.of(new byte[] { 13, 17, 23, 31 });
		var transaction2 = Transactions.of(new byte[] { 5, 6, 7 });
		var transaction3 = Transactions.of(new byte[] {});
		var block1 = Blocks.of(BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, hashingOfPreviousBlock, 4000, 20000, hashingForBlocks, HashingAlgorithms.sha256()),
			Stream.of(transaction1, transaction2, transaction3),
			new byte[0], nodeKeyPair.getPrivate());
		var hash = new byte[] { 67, 56, 43 };

		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetBlock(GetBlockMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetBlockResultMessages.of(Optional.of(block1), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var block2 = remote.getBlock(hash);
			assertEquals(block1, block2.get());
		}
	}

	@Test
	@DisplayName("getBlock() works if the block is missing")
	public void getBlockWorksIfBlockMissing() throws Exception {
		var hash = new byte[] { 67, 56, 43 };

		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetBlock(GetBlockMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetBlockResultMessages.of(Optional.empty(), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var block = remote.getBlock(hash);
			assertTrue(block.isEmpty());
		}
	}

	@Test
	@DisplayName("getBlockDescription() works if the block exists")
	public void getBlockDescriptionWorksIfBlockExists() throws Exception {
		var hashingForDeadlines = shabal256();
		var hashingForGenerations = sha256();
		var hashingForBlocks = sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var hashingOfPreviousBlock = new byte[hashingForBlocks.length()];
		for (int pos = 0; pos < hashingOfPreviousBlock.length; pos++)
			hashingOfPreviousBlock[pos] = (byte) (17 + pos);
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations), plotKeyPair.getPrivate());
		var description1 = BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, hashingOfPreviousBlock, 4000, 20000, hashingForBlocks, HashingAlgorithms.sha256());
		var hash = new byte[] { 67, 56, 43 };

		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetBlockDescription(GetBlockDescriptionMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetBlockDescriptionResultMessages.of(Optional.of(description1), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var description2 = remote.getBlockDescription(hash);
			assertEquals(description1, description2.get());
		}
	}

	@Test
	@DisplayName("getBlockDescription() works if the block is missing")
	public void getBlockDescriptionWorksIfBlockMissing() throws Exception {
		byte[] hash = { 67, 56, 43 };

		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetBlockDescription(GetBlockDescriptionMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetBlockDescriptionResultMessages.of(Optional.empty(), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var description = remote.getBlockDescription(hash);
			assertTrue(description.isEmpty());
		}
	}

	@Test
	@DisplayName("getConfig() works")
	public void getConfigWorks() throws Exception {
		var config1 = BasicConsensusConfigBuilders.defaults().build();

		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetConfig(GetConfigMessage message, Session session) {
				sendObjectAsync(session, GetConfigResultMessages.of(config1, message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var config2 = remote.getConfig();
			assertEquals(config1, config2);
		}
	}

	@Test
	@DisplayName("getChainInfo() works")
	public void getChainInfoWorks() throws Exception {
		var info1 = ChainInfos.of(1973L, Optional.of(new byte[] { 1, 2, 3, 4 }), Optional.of(new byte[] { 17, 13, 19 }), Optional.of(new byte[] { 13, 17, 19 }));

		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetChainInfo(GetChainInfoMessage message, Session session) {
				sendObjectAsync(session, GetChainInfoResultMessages.of(info1, message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var info2 = remote.getChainInfo();
			assertEquals(info1, info2);
		}
	}

	@Test
	@DisplayName("getChainPortion() works")
	public void getChainPortionWorks() throws Exception {
		var chain1 = ChainPortions.of(Stream.of(new byte[] { 1, 2, 3, 4 }, new byte[] { 17, 13, 19 }));

		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetChainPortion(GetChainPortionMessage message, Session session) {
				sendObjectAsync(session, GetChainPortionResultMessages.of(chain1, message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var chain2 = remote.getChainPortion(10, 20);
			assertEquals(chain1, chain2);
		}
	}

	@Test
	@DisplayName("getInfo() works")
	public void getInfoWorks() throws Exception {
		var info1 = NodeInfos.of(Versions.of(1, 2, 3), UUID.randomUUID(), LocalDateTime.now(ZoneId.of("UTC")));
	
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetInfo(GetInfoMessage message, Session session) {
				sendObjectAsync(session, GetInfoResultMessages.of(info1, message.getId()), RuntimeException::new);
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var info2 = remote.getInfo();
			assertEquals(info1, info2);
		}
	}

	@Test
	@DisplayName("getMempoolInfo() works")
	public void getMempoolInfoWorks() throws Exception {
		var info1 = MempoolInfos.of(17L);
	
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetMempoolInfo(GetMempoolInfoMessage message, Session session) {
				sendObjectAsync(session, GetMempoolInfoResultMessages.of(info1, message.getId()), RuntimeException::new);
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var info2 = remote.getMempoolInfo();
			assertEquals(info1, info2);
		}
	}

	@Test
	@DisplayName("add(Transaction) works")
	public void addTransactionWorks() throws Exception {
		var transaction1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
		var transaction2 = new AtomicReference<Transaction>();

		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onAddTransaction(AddTransactionMessage message, Session session) {
				transaction2.set(message.getTransaction());
				sendObjectAsync(session, AddTransactionResultMessages.of(MempoolEntries.of(new byte[] { 1 , 2 }, 13L), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			remote.add(transaction1);
			assertEquals(transaction1, transaction2.get());
		}
	}

	@Test
	@DisplayName("add(Transaction) works in case of RejectedTransactionException")
	public void addTransactionWorksInCaseOfRejectedTransactionException() throws Exception {
		var exceptionMessage = "exception message";
		var transaction = Transactions.of(new byte[] { 1, 2, 3, 4 });

		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onAddTransaction(AddTransactionMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new TransactionRejectedException(exceptionMessage), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(TransactionRejectedException.class, () -> remote.add(transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getMempoolPortion() works")
	public void getMempoolPortionWorks() throws Exception {
		var mempool1 = MempoolPortions.of(Stream.of(
			MempoolEntries.of(new byte[] { 1, 2, 3, 4 }, 11L),
			MempoolEntries.of(new byte[] { 17, 13, 19 }, 17L)
		));

		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}

			@Override
			protected void onGetMempoolPortion(GetMempoolPortionMessage message, Session session) {
				sendObjectAsync(session, GetMempoolPortionResultMessages.of(mempool1, message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var mempool2 = remote.getMempoolPortion(10, 20);
			assertEquals(mempool1, mempool2);
		}
	}

	@Test
	@Timeout(10)
	@DisplayName("if a service whispers a peer, a remote will inform its bound whisperers")
	public void ifServiceWhispersPeersTheRemoteInformsBoundWhisperers() throws Exception {
		var peer = Peers.of(java.net.URI.create("ws://my.machine:8032"));
		var semaphore = new Semaphore(0);
		var whisperer = mock(Whisperer.class);
		
		doAnswer(invocation -> {
			WhisperPeerMessage message = invocation.getArgument(0);

			if (message.getWhispered().equals(peer))
				semaphore.release();

			return null;
	    })
		.when(whisperer).whisper(any(WhisperPeerMessage.class), any(), any());

		try (var service = new PublicTestServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			remote.bindWhisperer(whisperer);
			service.whisper(WhisperPeerMessages.of(peer, UUID.randomUUID().toString()), _whisperer -> false, "peer " + peer);
			semaphore.acquire();
		}
	}

	@Test
	@Timeout(10)
	@DisplayName("if a service whispers a block, a remote will inform its bound whisperers")
	public void ifServiceWhispersBlockTheRemoteInformsBoundWhisperers() throws Exception {
		var hashingForDeadlines = shabal256();
		var hashingForGenerations = sha256();
		var hashingForBlocks = sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var hashingOfPreviousBlock = new byte[hashingForBlocks.length()];
		for (int pos = 0; pos < hashingOfPreviousBlock.length; pos++)
			hashingOfPreviousBlock[pos] = (byte) (17 + pos);
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations), plotKeyPair.getPrivate());
		var transaction1 = Transactions.of(new byte[] { 13, 17, 23, 31 });
		var transaction2 = Transactions.of(new byte[] { 5, 6, 7 });
		var transaction3 = Transactions.of(new byte[] {});
		var block = Blocks.of(BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, hashingOfPreviousBlock, 4000, 20000, hashingForBlocks, HashingAlgorithms.sha256()),
			Stream.of(transaction1, transaction2, transaction3), new byte[0], nodeKeyPair.getPrivate());
		var semaphore = new Semaphore(0);

		var whisperer = mock(Whisperer.class);
		doAnswer(invocation -> {
			WhisperBlockMessage message = invocation.getArgument(0);

			if (message.getWhispered().equals(block))
				semaphore.release();

			return null;
	    })
		.when(whisperer).whisper(any(WhisperBlockMessage.class), any(), any());

		try (var service = new PublicTestServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			remote.bindWhisperer(whisperer);
			service.whisper(WhisperBlockMessages.of(block, UUID.randomUUID().toString()), _whisperer -> false, "block " + block.getHexHash());
			semaphore.acquire();
		}
	}

	@Test
	@DisplayName("getTransaction() works if the transaction exists")
	public void getTransactionWorksIfTransactionExists() throws Exception {
		var tx1 = Transactions.of(new byte[] { 13, 1, 19, 73 });
		byte[] hash = { 67, 56, 43 };
	
		class MyServer extends PublicTestServer {
	
			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetTransaction(GetTransactionMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetTransactionResultMessages.of(Optional.of(tx1), message.getId()), RuntimeException::new);
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var tx2 = remote.getTransaction(hash);
			assertEquals(tx1, tx2.get());
		}
	}

	@Test
	@DisplayName("getTransaction() works if the transaction is missing")
	public void getTransactionWorksIfTransactionMissing() throws Exception {
		byte[] hash = { 67, 56, 43 };
	
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetTransaction(GetTransactionMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetTransactionResultMessages.of(Optional.empty(), message.getId()), RuntimeException::new);
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var tx = remote.getTransaction(hash);
			assertTrue(tx.isEmpty());
		}
	}

	@Test
	@DisplayName("getTransactionRepresentation() works if the transaction exists")
	public void getTransactionRepresentationWorksIfTransactionExists() throws Exception {
		var representation1 = "hello";
		byte[] hash = { 67, 56, 43 };
	
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetTransactionRepresentation(GetTransactionRepresentationMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetTransactionRepresentationResultMessages.of(Optional.of(representation1), message.getId()), RuntimeException::new);
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var representation2 = remote.getTransactionRepresentation(hash);
			assertEquals(representation1, representation2.get());
		}
	}

	@Test
	@DisplayName("getTransactionRepresentation() works if the transaction is missing")
	public void getTransactionRepresentationWorksIfTransactionMissing() throws Exception {
		byte[] hash = { 67, 56, 43 };
	
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetTransactionRepresentation(GetTransactionRepresentationMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetTransactionRepresentationResultMessages.of(Optional.empty(), message.getId()), RuntimeException::new);
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var representation = remote.getTransactionRepresentation(hash);
			assertTrue(representation.isEmpty());
		}
	}

	@Test
	@DisplayName("getTransactionRepresentation() works if it throws RejectedTransactionException")
	public void getTransactionRepresentationWorksInCaseOfRejectedTransactionException() throws Exception {
		byte[] hash = { 67, 56, 43 };
		var exceptionMessage = "rejected";
	
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetTransactionRepresentation(GetTransactionRepresentationMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, ExceptionMessages.of(new TransactionRejectedException(exceptionMessage), message.getId()), RuntimeException::new);
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var exception = assertThrows(TransactionRejectedException.class, () -> remote.getTransactionRepresentation(hash));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getTransactionAddress() works if the transaction exists")
	public void getTransactionAddressWorksIfTransactionExists() throws Exception {
		var address1 = TransactionAddresses.of(new byte[] { 13, 1, 19, 73 }, 42);
		byte[] hash = { 67, 56, 43 };
	
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetTransactionAddress(GetTransactionAddressMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetTransactionAddressResultMessages.of(Optional.of(address1), message.getId()), RuntimeException::new);
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var address2 = remote.getTransactionAddress(hash);
			assertEquals(address1, address2.get());
		}
	}

	@Test
	@DisplayName("getTransactionAddress() works if the transaction is missing")
	public void getTransactionAddressWorksIfTransactionMissing() throws Exception {
		byte[] hash = { 67, 56, 43 };
	
		class MyServer extends PublicTestServer {

			private MyServer() throws NoSuchAlgorithmException, FailedDeploymentException, InterruptedException, TimeoutException {}
	
			@Override
			protected void onGetTransactionAddress(GetTransactionAddressMessage message, Session session) {
				if (Arrays.equals(message.getHash(), hash))
					sendObjectAsync(session, GetTransactionAddressResultMessages.of(Optional.empty(), message.getId()), RuntimeException::new);
			}
		};
	
		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var address = remote.getTransactionAddress(hash);
			assertTrue(address.isEmpty());
		}
	}
}
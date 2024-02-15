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

import static io.hotmoka.crypto.HashingAlgorithms.shabal256;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;

import io.hotmoka.closeables.api.CloseHandler;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.ChainPortions;
import io.mokamint.node.ConsensusConfigBuilders;
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
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.api.MempoolPortion;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.TaskInfo;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.messages.WhisperPeerMessages;
import io.mokamint.node.messages.api.ExceptionMessage;
import io.mokamint.node.remote.internal.RemotePublicNodeImpl;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.node.service.internal.PublicNodeServiceImpl;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import jakarta.websocket.DeploymentException;

public class PublicNodeServiceTests extends AbstractLoggedTests {
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

	private PublicNode mkNode() throws NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException {
		var node = mock(PublicNode.class);
		// compilation fails if the following is not split in two...
		OngoingStubbing<ConsensusConfig<?,?>> w = when(node.getConfig());
		var config = ConsensusConfigBuilders.defaults().build();
		w.thenReturn(config);
		
		return node;
	}

	@Test
	@DisplayName("if a getPeerInfos() request reaches the service, it sends back the peers of the node")
	public void serviceGetPeerInfosWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException, NodeException {
		var semaphore = new Semaphore(0);
		var peerInfo1 = PeerInfos.of(Peers.of(new URI("ws://my.machine:8032")), 345, true);
		var peerInfo2 = PeerInfos.of(Peers.of(new URI("ws://her.machine:8033")), 11, false);
		var node = mkNode();
		when(node.getPeerInfos()).thenReturn(Stream.of(peerInfo1, peerInfo2));

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetPeerInfosResult(Stream<PeerInfo> received) {
				var peerInfos = received.collect(Collectors.toList());
				if (peerInfos.size() == 2 && peerInfos.contains(peerInfo1) && peerInfos.contains(peerInfo2))
					semaphore.release();
			}

			private void sendGetPeerInfos() throws ClosedNodeException {
				sendGetPeerInfos("id");
			}
		}

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetPeerInfos();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getMinerInfos() request reaches the service, it sends back the miners of the node")
	public void serviceGetMinerInfosWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException, NodeException {
		var semaphore = new Semaphore(0);
		var minerInfo1 = MinerInfos.of(UUID.randomUUID(), 345L, "a miner");
		var minerInfo2 = MinerInfos.of(UUID.randomUUID(), 11L, "a special miner");
		var node = mkNode();
		when(node.getMinerInfos()).thenReturn(Stream.of(minerInfo1, minerInfo2));

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetMinerInfosResult(Stream<MinerInfo> received) {
				var minerInfos = received.collect(Collectors.toList());
				if (minerInfos.size() == 2 && minerInfos.contains(minerInfo1) && minerInfos.contains(minerInfo2))
					semaphore.release();
			}

			private void sendGetMinerInfos() throws ClosedNodeException {
				sendGetMinerInfos("id");
			}
		}

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetMinerInfos();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getTaskInfos() request reaches the service, it sends back the tasks of the node")
	public void serviceGetTaskInfosWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException, NodeException {
		var semaphore = new Semaphore(0);
		var taskInfo1 = TaskInfos.of("a great task");
		var taskInfo2 = TaskInfos.of("a greater task");
		var node = mkNode();
		when(node.getTaskInfos()).thenReturn(Stream.of(taskInfo1, taskInfo2));

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetTaskInfosResult(Stream<TaskInfo> received) {
				var taskInfos = received.collect(Collectors.toList());
				if (taskInfos.size() == 2 && taskInfos.contains(taskInfo1) && taskInfos.contains(taskInfo2))
					semaphore.release();
			}

			private void sendGetTaskInfos() throws ClosedNodeException {
				sendGetTaskInfos("id");
			}
		}

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetTaskInfos();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getBlock() request reaches the service and there is no block with the requested hash, it sends back an empty optional")
	public void serviceGetBlockEmptyWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, NodeException {
		var semaphore = new Semaphore(0);

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetBlockResult(Optional<Block> received) {
				if (received.isEmpty())
					semaphore.release();
			}

			private void sendGetBlock(byte[] hash) throws ClosedNodeException {
				sendGetBlock(hash, "id");
			}
		}

		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getBlock(hash)).thenReturn(Optional.empty());

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetBlock(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getBlock() request reaches the service and there is a block with the requested hash, it sends back that block")
	public void serviceGetBlockNonEmptyWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, InvalidKeyException, SignatureException, NodeException {
		var semaphore = new Semaphore(0);
		HashingAlgorithm shabal256 = shabal256();
		var data = new byte[] { 1, 2, 3, 4, 5, 6 };
		var value = new byte[shabal256.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		int scoopNumber = 42;
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 43L, value, scoopNumber, data, shabal256, plotKeyPair.getPrivate());
		var transaction1 = Transactions.of(new byte[] { 13, 17, 23, 31 });
		var transaction2 = Transactions.of(new byte[] { 5, 6, 7 });
		var transaction3 = Transactions.of(new byte[] {});
		var block = Blocks.of(BlockDescriptions.of(13L, BigInteger.TEN, 134L, 11L, BigInteger.valueOf(123), deadline, new byte[] { 5, 6, 7, 8 }),
			Stream.of(transaction1, transaction2, transaction3),
			new byte[0], nodeKeyPair.getPrivate());

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetBlockResult(Optional<Block> received) {
				if (block.equals(received.get()))
					semaphore.release();
			}

			private void sendGetBlock(byte[] hash) throws ClosedNodeException {
				sendGetBlock(hash, "id");
			}
		}

		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getBlock(hash)).thenReturn(Optional.of(block));

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetBlock(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getBlock() request reaches the service and there is a block with the requested hash, but with an unknown hashing algorithm, it sends back an exception")
	public void serviceGetBlockUnknownHashingWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, NodeException {
		var semaphore = new Semaphore(0);
	
		class MyTestClient extends RemotePublicNodeImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (NoSuchAlgorithmException.class.isAssignableFrom(message.getExceptionClass()))
					semaphore.release();
			}
	
			private void sendGetBlock(byte[] hash) throws ClosedNodeException {
				sendGetBlock(hash, "id");
			}
		}
	
		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getBlock(hash)).thenThrow(NoSuchAlgorithmException.class);
	
		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetBlock(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getBlockDescription() request reaches the service and there is no block with the requested hash, it sends back an empty optional")
	public void serviceGetBlockDescriptionEmptyWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, NodeException {
		var semaphore = new Semaphore(0);

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetBlockDescriptionResult(Optional<BlockDescription> received) {
				if (received.isEmpty())
					semaphore.release();
			}

			private void sendGetBlockDescription(byte[] hash) throws ClosedNodeException {
				sendGetBlockDescription(hash, "id");
			}
		}

		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getBlockDescription(hash)).thenReturn(Optional.empty());

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetBlockDescription(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getBlockDescription() request reaches the service and there is a block with the requested hash, it sends back the description of that block")
	public void serviceGetBlockDescriptionNonEmptyWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, InvalidKeyException, SignatureException, NodeException {
		var semaphore = new Semaphore(0);
		HashingAlgorithm shabal256 = shabal256();
		var data = new byte[] { 1, 2, 3, 4, 5, 6 };
		var value = new byte[shabal256.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		int scoopNumber = 42;
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 43L, value, scoopNumber, data, shabal256, plotKeyPair.getPrivate());
		var description = BlockDescriptions.of(13L, BigInteger.TEN, 134L, 11L, BigInteger.valueOf(123), deadline, new byte[] { 5, 6, 7, 8 });

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetBlockDescriptionResult(Optional<BlockDescription> received) {
				if (description.equals(received.get()))
					semaphore.release();
			}

			private void sendGetBlockDescription(byte[] hash) throws ClosedNodeException {
				sendGetBlockDescription(hash, "id");
			}
		}

		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getBlockDescription(hash)).thenReturn(Optional.of(description));

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetBlockDescription(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getBlockDescription() request reaches the service and there is a block with the requested hash, but with an unknown hashing algorithm, it sends back an exception")
	public void serviceGetBlockDescriptionUnknownHashingWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, NodeException {
		var semaphore = new Semaphore(0);
	
		class MyTestClient extends RemotePublicNodeImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (NoSuchAlgorithmException.class.isAssignableFrom(message.getExceptionClass()))
					semaphore.release();
			}
	
			private void sendGetBlockDescription(byte[] hash) throws ClosedNodeException {
				sendGetBlockDescription(hash, "id");
			}
		}
	
		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getBlockDescription(hash)).thenThrow(NoSuchAlgorithmException.class);
	
		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetBlockDescription(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getConfig() request reaches the service, it sends back its consensus configuration")
	public void serviceGetConfigWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, NodeException {
		var semaphore = new Semaphore(0);
		var config = ConsensusConfigBuilders.defaults().build();

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetConfigResult(ConsensusConfig<?,?> received) {
				if (config.equals(received))
					semaphore.release();
			}

			private void sendGetConfig() throws ClosedNodeException {
				sendGetConfig("id");
			}
		}

		var node = mkNode();

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetConfig();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getChainInfo() request reaches the service, it sends back its chain information")
	public void serviceGetChainInfoWorks() throws DeploymentException, IOException, DatabaseException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException, NodeException {
		var semaphore = new Semaphore(0);
		var info = ChainInfos.of(1973L, Optional.of(new byte[] { 1, 2, 3, 4 }), Optional.of(new byte[] { 13, 17, 19 }));

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetChainInfoResult(ChainInfo received) {
				if (info.equals(received))
					semaphore.release();
			}

			private void sendGetChainInfo() throws ClosedNodeException {
				sendGetChainInfo("id");
			}
		}

		var node = mkNode();
		when(node.getChainInfo()).thenReturn(info);

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetChainInfo();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getChainPortion() request reaches the service, it sends back its chain hashes")
	public void serviceGetChainPortionWorks() throws DeploymentException, IOException, DatabaseException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException, NodeException {
		var semaphore = new Semaphore(0);
		var chain = ChainPortions.of(Stream.of(new byte[] { 1, 2, 3, 4 }, new byte[] { 13, 17, 19 }));

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetChainPortionResult(ChainPortion received) {
				if (chain.equals(received))
					semaphore.release();
			}

			private void sendGetChainPortion() throws ClosedNodeException {
				sendGetChainPortion(5, 10, "id");
			}
		}

		var node = mkNode();
		when(node.getChainPortion(5, 10)).thenReturn(chain);

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetChainPortion();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if an add(Transaction) request reaches the service, it adds the transaction and sends back a result")
	public void serviceAddTransactionWorks() throws DeploymentException, IOException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException, RejectedTransactionException, DatabaseException, NodeException {
		var semaphore = new Semaphore(0);
		var transaction = Transactions.of(new byte[] { 1, 2, 3, 4 });
		
		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onAddTransactionResult(MempoolEntry info) {
				semaphore.release();
			}

			private void addTransaction() throws ClosedNodeException {
				sendAddTransaction(transaction, "id");
			}
		}

		var node = mkNode();
		when(node.add(eq(transaction))).thenReturn(MempoolEntries.of(new byte[] { 1, 2, 3 }, 1000L));

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.addTransaction();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getMempoolInfo() request reaches the service, it sends back its mempool information")
	public void serviceGetMempoolInfoWorks() throws DeploymentException, IOException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException, NodeException {
		var semaphore = new Semaphore(0);
		var info = MempoolInfos.of(17L);

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetMempoolInfoResult(MempoolInfo received) {
				if (info.equals(received))
					semaphore.release();
			}

			private void sendGetMempoolInfo() throws ClosedNodeException {
				sendGetMempoolInfo("id");
			}
		}

		var node = mkNode();
		when(node.getMempoolInfo()).thenReturn(info);

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetMempoolInfo();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getMempoolPortion() request reaches the service, it sends back its mempool")
	public void serviceGetMempoolPortionWorks() throws DeploymentException, IOException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException, NodeException {
		var semaphore = new Semaphore(0);
		var mempool = MempoolPortions.of(Stream.of(
			MempoolEntries.of(new byte[] { 1, 2, 3, 4 }, 11L),
			MempoolEntries.of(new byte[] { 13, 17, 19 }, 13L)
		));

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetMempoolPortionResult(MempoolPortion received) {
				if (mempool.equals(received))
					semaphore.release();
			}

			private void sendGetMempoolPortion() throws ClosedNodeException {
				sendGetMempoolPortion(5, 10, "id");
			}
		}

		var node = mkNode();
		when(node.getMempoolPortion(5, 10)).thenReturn(mempool);

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetMempoolPortion();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getInfo() request reaches the service, it sends back its node information")
	public void serviceGetInfoWorks() throws DeploymentException, IOException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException, NodeException {
		var semaphore = new Semaphore(0);
		var info = NodeInfos.of(Versions.of(1, 2, 3), UUID.randomUUID(), LocalDateTime.now(ZoneId.of("UTC")));

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetInfoResult(NodeInfo received) {
				if (info.equals(received))
					semaphore.release();
			}

			private void sendGetInfo() throws ClosedNodeException {
				sendGetInfo("id");
			}
		}

		var node = mkNode();
		when(node.getInfo()).thenReturn(info);

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetInfo();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a service receives whispered peers from its node, they get whispered to the connected clients")
	public void serviceSendsWhisperedPeersToClients() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException, NodeException {
		var semaphore = new Semaphore(0);
		var peer1 = Peers.of(new URI("ws://my.machine:8032"));
		var peer2 = Peers.of(new URI("ws://her.machine:8033"));
		var allPeers = new HashSet<Peer>();
		allPeers.add(peer1);
		allPeers.add(peer2);
		var node = mkNode();

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onWhispered(Peer peer) {
				allPeers.remove(peer);
				if (allPeers.isEmpty())
					semaphore.release();
			}
		}

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			service.whisper(WhisperPeerMessages.of(peer1, UUID.randomUUID().toString()), _whisperer -> false, "peer " + peer1);
			service.whisper(WhisperPeerMessages.of(peer2, UUID.randomUUID().toString()), _whisperer -> false, "peer " + peer2);
			assertTrue(semaphore.tryAcquire(1, 5, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a public service gets closed, any remote using that service gets closed and its methods throw ClosedNodeException")
	public void ifServiceClosedThenRemoteClosedAndNotUsable() throws IOException, InterruptedException, DeploymentException, URISyntaxException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException, NodeException {
		var node = mkNode();
		var semaphore = new Semaphore(0);
		
		class MyRemotePublicNode extends RemotePublicNodeImpl {
			MyRemotePublicNode() throws DeploymentException, IOException, URISyntaxException {
				super(new URI("ws://localhost:8030"), 2000L, 240000L, 1000);
			}

			@Override
			public void close() throws NodeException, InterruptedException {
				super.close();
				semaphore.release();
			}
		}

		try (var service = PublicNodeServices.open(node, 8030); var remote = new MyRemotePublicNode()) {
			service.close(); // by closing the service, the remote is not usable anymore
			semaphore.tryAcquire(1, 1, TimeUnit.SECONDS);
			assertThrows(ClosedNodeException.class, () -> remote.getBlock(new byte[] { 1, 2, 3, 4 }));
		}
	}

	@Test
	@DisplayName("if the node of a service gets closed, the service receives a close call")
	public void serviceGetsClosedIfNodeGetsClosed() throws Exception {
		var semaphore = new Semaphore(0);

		var listenerForClose = new AtomicReference<CloseHandler>();
		var node = mkNode();
		doAnswer(listener -> {
			listenerForClose.set(listener.getArgument(0));
			return null;
		}).
		when(node).addCloseHandler(any());

		class MyPublicNodeService extends PublicNodeServiceImpl {
			private MyPublicNodeService() throws DeploymentException, IOException {
				super(node, PORT, 180000L, 1000, Optional.empty());
			}

			@Override
			public void close() throws InterruptedException {
				super.close();
				semaphore.release();
			}
		}

		try (var service = new MyPublicNodeService()) {
			listenerForClose.get().close();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getTransaction() request reaches the service and there is no transaction with the requested hash, it sends back an empty optional")
	public void serviceGetTransactionEmptyWorks() throws DeploymentException, IOException, DatabaseException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, NodeException {
		var semaphore = new Semaphore(0);
	
		class MyTestClient extends RemotePublicNodeImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}
	
			@Override
			protected void onGetTransactionResult(Optional<Transaction> received) {
				if (received.isEmpty())
					semaphore.release();
			}
	
			private void sendGetTransaction(byte[] hash) throws ClosedNodeException {
				sendGetTransaction(hash, "id");
			}
		}
	
		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getTransaction(hash)).thenReturn(Optional.empty());
	
		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetTransaction(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getTransaction() request reaches the service and there is a transaction with the requested hash, it sends back that transaction")
	public void serviceGetTransactionNonEmptyWorks() throws NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException, DatabaseException, DeploymentException, IOException, NodeException {
		var semaphore = new Semaphore(0);
		var tx = Transactions.of(new byte[] { 13, 1, 19, 73 });
	
		class MyTestClient extends RemotePublicNodeImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}
	
			@Override
			protected void onGetTransactionResult(Optional<Transaction> received) {
				if (tx.equals(received.get()))
					semaphore.release();
			}
	
			private void sendGetTransaction(byte[] hash) throws ClosedNodeException {
				sendGetTransaction(hash, "id");
			}
		}
	
		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getTransaction(hash)).thenReturn(Optional.of(tx));
	
		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetTransaction(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getTransaction() request reaches the service and there is a transaction with the requested hash, but the database is corrupted, it sends back an exception")
	public void serviceGetTransactionDatabaseExceptionWorks() throws DeploymentException, IOException, DatabaseException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, NodeException {
		var semaphore = new Semaphore(0);
	
		class MyTestClient extends RemotePublicNodeImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (DatabaseException.class.isAssignableFrom(message.getExceptionClass()))
					semaphore.release();
			}

			private void sendGetTransaction(byte[] hash) throws ClosedNodeException {
				sendGetTransaction(hash, "id");
			}
		}
	
		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getTransaction(hash)).thenThrow(DatabaseException.class);
	
		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetTransaction(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getTransactionRepresentation() request reaches the service and there is no transaction with the requested hash, it sends back an empty optional")
	public void serviceGetTransactionRepresentationEmptyWorks() throws DeploymentException, IOException, DatabaseException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, RejectedTransactionException, NodeException {
		var semaphore = new Semaphore(0);
	
		class MyTestClient extends RemotePublicNodeImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}
	
			@Override
			protected void onGetTransactionRepresentationResult(Optional<String> received) {
				if (received.isEmpty())
					semaphore.release();
			}
	
			private void sendGetTransactionRepresentation(byte[] hash) throws ClosedNodeException {
				sendGetTransactionRepresentation(hash, "id");
			}
		}
	
		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getTransactionRepresentation(hash)).thenReturn(Optional.empty());
	
		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetTransactionRepresentation(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getTransactionRepresentation() request reaches the service and there is a transaction with the requested hash, it sends back the representation of that transaction")
	public void serviceGetTransactionRepresentationNonEmptyWorks() throws NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException, RejectedTransactionException, DatabaseException, DeploymentException, IOException, NodeException {
		var semaphore = new Semaphore(0);
		var representation = "hello";
	
		class MyTestClient extends RemotePublicNodeImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}
	
			@Override
			protected void onGetTransactionRepresentationResult(Optional<String> received) {
				if (representation.equals(received.get()))
					semaphore.release();
			}
	
			private void sendGetTransactionRepresentation(byte[] hash) throws ClosedNodeException {
				sendGetTransactionRepresentation(hash, "id");
			}
		}
	
		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getTransactionRepresentation(hash)).thenReturn(Optional.of(representation));
	
		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetTransactionRepresentation(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getTransactionRepresentation() request reaches the service and there is a transaction with the requested hash, but its representation cannot be computed, it sends back an exception")
	public void serviceGetTransactionRepresentationRejectedTransactionWorks() throws DeploymentException, IOException, DatabaseException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, RejectedTransactionException, NodeException {
		var semaphore = new Semaphore(0);
	
		class MyTestClient extends RemotePublicNodeImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (RejectedTransactionException.class.isAssignableFrom(message.getExceptionClass()))
					semaphore.release();
			}
	
			private void sendGetTransactionRepresentation(byte[] hash) throws ClosedNodeException {
				sendGetTransactionRepresentation(hash, "id");
			}
		}
	
		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getTransactionRepresentation(hash)).thenThrow(RejectedTransactionException.class);
	
		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetTransactionRepresentation(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getTransactionAddress() request reaches the service and there is no transaction with the requested hash, it sends back an empty optional")
	public void serviceGetTransactionAddressEmptyWorks() throws NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException, DatabaseException, IOException, DeploymentException, NodeException  {
		var semaphore = new Semaphore(0);
	
		class MyTestClient extends RemotePublicNodeImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}
	
			@Override
			protected void onGetTransactionAddressResult(Optional<TransactionAddress> received) {
				if (received.isEmpty())
					semaphore.release();
			}
	
			private void sendGetTransactionAddress(byte[] hash) throws ClosedNodeException {
				sendGetTransactionAddress(hash, "id");
			}
		}
	
		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getTransactionAddress(hash)).thenReturn(Optional.empty());
	
		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetTransactionAddress(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getTransactionAddress() request reaches the service and there is a transaction with the requested hash, it sends back that transaction's address")
	public void serviceGetTransactionAddressNonEmptyWorks() throws NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException, DatabaseException, DeploymentException, IOException, NodeException {
		var semaphore = new Semaphore(0);
		var address = TransactionAddresses.of(new byte[] { 13, 1, 19, 73 }, 42);
	
		class MyTestClient extends RemotePublicNodeImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}
	
			@Override
			protected void onGetTransactionAddressResult(Optional<TransactionAddress> received) {
				if (address.equals(received.get()))
					semaphore.release();
			}
	
			private void sendGetTransactionAddress(byte[] hash) throws ClosedNodeException {
				sendGetTransactionAddress(hash, "id");
			}
		}
	
		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getTransactionAddress(hash)).thenReturn(Optional.of(address));
	
		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetTransactionAddress(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getTransactionAddress() request reaches the service and there is a transaction with the requested hash, but the database is corrupted, it sends back an exception")
	public void serviceGetTransactionAddressDatabaseExceptionWorks() throws DeploymentException, IOException, DatabaseException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, NodeException {
		var semaphore = new Semaphore(0);
	
		class MyTestClient extends RemotePublicNodeImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (DatabaseException.class.isAssignableFrom(message.getExceptionClass()))
					semaphore.release();
			}
	
			private void sendGetTransactionAddress(byte[] hash) throws ClosedNodeException {
				sendGetTransactionAddress(hash, "id");
			}
		}
	
		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mkNode();
		when(node.getTransactionAddress(hash)).thenThrow(DatabaseException.class);
	
		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetTransactionAddress(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}
}
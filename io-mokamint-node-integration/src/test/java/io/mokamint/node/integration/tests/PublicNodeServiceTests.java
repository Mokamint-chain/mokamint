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
import org.mockito.stubbing.OngoingStubbing;

import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.Chains;
import io.mokamint.node.ConsensusConfigBuilders;
import io.mokamint.node.MinerInfos;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.TaskInfos;
import io.mokamint.node.Transactions;
import io.mokamint.node.Versions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.Chain;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.Node.CloseHandler;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.api.TaskInfo;
import io.mokamint.node.messages.WhisperPeersMessages;
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
	public void serviceGetPeerInfosWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);
		var peerInfo1 = PeerInfos.of(Peers.of(new URI("ws://my.machine:8032")), 345, true);
		var peerInfo2 = PeerInfos.of(Peers.of(new URI("ws://her.machine:8033")), 11, false);
		var node = mkNode();
		when(node.getPeerInfos()).thenReturn(Stream.of(peerInfo1, peerInfo2));

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
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
	public void serviceGetMinerInfosWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);
		var minerInfo1 = MinerInfos.of(UUID.randomUUID(), 345L, "a miner");
		var minerInfo2 = MinerInfos.of(UUID.randomUUID(), 11L, "a special miner");
		var node = mkNode();
		when(node.getMinerInfos()).thenReturn(Stream.of(minerInfo1, minerInfo2));

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
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
	public void serviceGetTaskInfosWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);
		var taskInfo1 = TaskInfos.of("a great task");
		var taskInfo2 = TaskInfos.of("a greater task");
		var node = mkNode();
		when(node.getTaskInfos()).thenReturn(Stream.of(taskInfo1, taskInfo2));

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
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
	public void serviceGetBlockEmptyWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException {
		var semaphore = new Semaphore(0);

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
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
	public void serviceGetBlockNonEmptyWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, InvalidKeyException, SignatureException {
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
		var block = Blocks.of(13L, BigInteger.TEN, 134L, 11L, BigInteger.valueOf(123), deadline, new byte[] { 5, 6, 7, 8 }, nodeKeyPair.getPrivate());

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
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
	public void serviceGetBlockUnknownHashingWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException {
		var semaphore = new Semaphore(0);
	
		class MyTestClient extends RemotePublicNodeImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
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
	public void serviceGetBlockDescriptionEmptyWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException {
		var semaphore = new Semaphore(0);

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
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
	public void serviceGetBlockDescriptionNonEmptyWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException, InvalidKeyException, SignatureException {
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
				super(URI, 2000L, 1000);
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
	public void serviceGetBlockDescriptionUnknownHashingWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException {
		var semaphore = new Semaphore(0);
	
		class MyTestClient extends RemotePublicNodeImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
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
	public void serviceGetConfigWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException {
		var semaphore = new Semaphore(0);
		var config = ConsensusConfigBuilders.defaults().build();

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
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
	public void serviceGetChainInfoWorks() throws DeploymentException, IOException, DatabaseException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);
		var info = ChainInfos.of(1973L, Optional.of(new byte[] { 1, 2, 3, 4 }), Optional.of(new byte[] { 13, 17, 19 }));

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
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
	@DisplayName("if a getChain() request reaches the service, it sends back its chain hashes")
	public void serviceGetChainWorks() throws DeploymentException, IOException, DatabaseException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);
		var chain = Chains.of(Stream.of(new byte[] { 1, 2, 3, 4 }, new byte[] { 13, 17, 19 }));

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
			}

			@Override
			protected void onGetChainResult(Chain received) {
				if (chain.equals(received))
					semaphore.release();
			}

			private void sendGetChain() throws ClosedNodeException {
				sendGetChain(5, 10, "id");
			}
		}

		var node = mkNode();
		when(node.getChain(5, 10)).thenReturn(chain);

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetChain();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a postTransaction() request reaches the service, it post the transaction and sends back a result")
	public void servicePostTransactionWorks() throws DeploymentException, IOException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);
		var transaction = Transactions.of(new byte[] { 1, 2, 3, 4 });
		
		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
			}

			@Override
			protected void onPostTransactionResult(boolean success) {
				if (success)
					semaphore.release();
			}

			private void sendPost() throws ClosedNodeException {
				sendPost(transaction, "id");
			}
		}

		var node = mkNode();
		when(node.post(eq(transaction))).thenReturn(true);

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendPost();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getInfo() request reaches the service, it sends back its node information")
	public void serviceGetInfoWorks() throws DeploymentException, IOException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);
		var info = NodeInfos.of(Versions.of(1, 2, 3), UUID.randomUUID(), LocalDateTime.now(ZoneId.of("UTC")));

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
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
	public void serviceSendsWhisperedPeersToClients() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);
		var peer1 = Peers.of(new URI("ws://my.machine:8032"));
		var peer2 = Peers.of(new URI("ws://her.machine:8033"));
		var allPeers = Set.of(peer1, peer2);
		var node = mkNode();

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
			}

			@Override
			protected void onWhisperPeers(Stream<Peer> peers) {
				// we must use containsAll since the suggested peers might include
				// the public URI of the machine where the test is running
				if (peers.collect(Collectors.toSet()).containsAll(allPeers))
					semaphore.release();
			}
		}

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			service.whisper(WhisperPeersMessages.of(allPeers.stream(), UUID.randomUUID().toString()), _whisperer -> false);
			assertTrue(semaphore.tryAcquire(1, 5, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a public service gets closed, any remote using that service gets closed and its methods throw ClosedNodeException")
	public void ifServiceClosedThenRemoteClosedAndNotUsable() throws IOException, DatabaseException, InterruptedException, DeploymentException, URISyntaxException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException {
		var node = mkNode();
		var semaphore = new Semaphore(0);
		
		class MyRemotePublicNode extends RemotePublicNodeImpl {
			MyRemotePublicNode() throws DeploymentException, IOException, URISyntaxException {
				super(new URI("ws://localhost:8030"), 2000L, 1000);
			}

			@Override
			public void close() throws IOException, InterruptedException {
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
	public void serviceGetsClosedIfNodeGetsClosed() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, NoSuchAlgorithmException, ClosedNodeException {
		var semaphore = new Semaphore(0);

		var listenerForClose = new AtomicReference<CloseHandler>();
		var node = mkNode();
		doAnswer(listener -> {
			listenerForClose.set(listener.getArgument(0));
			return null;
		}).
		when(node).addOnClosedHandler(any());

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
}
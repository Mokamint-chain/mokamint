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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.LogManager;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.ConsensusConfigs;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.NodeInternals.CloseHandler;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.PublicNodeInternals;
import io.mokamint.node.Versions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.messages.WhisperPeersMessages;
import io.mokamint.node.messages.api.ExceptionMessage;
import io.mokamint.node.messages.api.WhisperPeersMessage;
import io.mokamint.node.remote.internal.RemotePublicNodeImpl;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.node.service.internal.PublicNodeServiceImpl;
import io.mokamint.nonce.Deadlines;
import jakarta.websocket.DeploymentException;

public class PublicNodeServiceTests {
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

	@Test
	@DisplayName("if a getPeers() request reaches the service, it sends back the peers of the node")
	public void serviceGetPeersWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, ClosedNodeException {
		var semaphore = new Semaphore(0);
		var peerInfo1 = PeerInfos.of(Peers.of(new URI("ws://my.machine:8032")), 345, true);
		var peerInfo2 = PeerInfos.of(Peers.of(new URI("ws://her.machine:8033")), 11, false);
		var node = mock(PublicNodeInternals.class);
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

			private void sendGetPeers() throws ClosedNodeException {
				sendGetPeerInfos("id");
			}
		}

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetPeers();
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
		var node = mock(PublicNodeInternals.class);
		when(node.getBlock(hash)).thenReturn(Optional.empty());

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetBlock(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getBlock() request reaches the service and there is a block with the requested hash, it sends back that block")
	public void serviceGetBlockNonEmptyWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256(Function.identity());
		var data = new byte[] { 1, 2, 3, 4, 5, 6 };
		var value = new byte[] { 1, 2, 3, 4, 5, 6 };
		int scoopNumber = 42;
		var deadline = Deadlines.of(new byte[] { 13, 44, 17, 19 }, 43L, value, scoopNumber, data, shabal256);
		var block = Blocks.of(13L, 11L, 134L, BigInteger.valueOf(123), deadline, new byte[] { 5, 6, 7, 8 });

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
		var node = mock(PublicNodeInternals.class);
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
		var node = mock(PublicNodeInternals.class);
		when(node.getBlock(hash)).thenThrow(NoSuchAlgorithmException.class);

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetBlock(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getConfig() request reaches the service, it sends back its consensus configuration")
	public void serviceGetConfigWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException {
		var semaphore = new Semaphore(0);
		var config = ConsensusConfigs.defaults().build();

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
			}

			@Override
			protected void onGetConfigResult(ConsensusConfig received) {
				if (config.equals(received))
					semaphore.release();
			}

			private void sendGetConfig() throws ClosedNodeException {
				sendGetConfig("id");
			}
		}

		var node = mock(PublicNodeInternals.class);
		when(node.getConfig()).thenReturn(config);

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetConfig();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getChainInfo() request reaches the service, it sends back its chain information")
	public void serviceGetChainInfoWorks() throws DeploymentException, IOException, DatabaseException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, TimeoutException, ClosedNodeException {
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

		var node = mock(PublicNodeInternals.class);
		when(node.getChainInfo()).thenReturn(info);

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetChainInfo();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getInfo() request reaches the service, it sends back its node information")
	public void serviceGetInfoWorks() throws DeploymentException, IOException, InterruptedException, TimeoutException, ClosedNodeException {
		var semaphore = new Semaphore(0);
		var info = NodeInfos.of(Versions.of(1, 2, 3), UUID.randomUUID());

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

		var node = mock(PublicNodeInternals.class);
		when(node.getInfo()).thenReturn(info);

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetInfo();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a service receives whispered peers from its node, they get whispered to the connected clients")
	public void serviceSendsWhisperedPeersToClients() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException {
		var semaphore = new Semaphore(0);
		var peer1 = Peers.of(new URI("ws://my.machine:8032"));
		var peer2 = Peers.of(new URI("ws://her.machine:8033"));
		var allPeers = Set.of(peer1, peer2);
		var node = mock(PublicNodeInternals.class);

		class MyTestClient extends RemotePublicNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 1000);
			}

			@Override
			protected void onWhisperPeers(WhisperPeersMessage message) {
				// we must use containsAll since the suggested peers might include
				// the public URI of the machine where the test is running
				if (message.getPeers().collect(Collectors.toSet()).containsAll(allPeers))
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
	public void ifServiceClosedThenRemoteClosedAndNotUsable() throws IOException, DatabaseException, InterruptedException, DeploymentException, URISyntaxException {
		var node = mock(PublicNodeInternals.class);
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
	public void serviceGetsClosedIfNodeGetsClosed() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException {
		var semaphore = new Semaphore(0);

		var listenerForClose = new AtomicReference<CloseHandler>();
		var node = mock(PublicNodeInternals.class);
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

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = PublicNodeServiceTests.class.getClassLoader().getResource("logging.properties");
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
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

package io.mokamint.node.service.tests;

import static io.hotmoka.crypto.HashingAlgorithms.shabal256;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.LogManager;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.Blocks;
import io.mokamint.node.Peers;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.ExceptionResultMessage;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.nonce.Deadlines;
import jakarta.websocket.DeploymentException;

public class NodeServiceTests {

	@Test
	@DisplayName("if a getPeers() request reaches the service, it sends back the peers of the node")
	public void serviceGetPeersWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException {
		var semaphore = new Semaphore(0);
		var peer1 = Peers.of(new URI("ws://my.machine:8032"));
		var peer2 = Peers.of(new URI("ws://her.machine:8033"));

		Consumer<Stream<Peer>> onGetPeersResult = received -> {
			var peers = received.collect(Collectors.toList());
			if (peers.size() == 2 && peers.contains(peer1) && peers.contains(peer2))
				semaphore.release();
		};

		var node = mock(PublicNode.class);
		when(node.getPeers()).thenReturn(Stream.of(peer1, peer2));

		try (var service = PublicNodeServices.open(node, 8025);
			 var client = new TestClient(new URI("ws://localhost:8025"), onGetPeersResult, null, null)) {

			client.sendGetPeers();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getBlock() request reaches the service and there is no block with the requested hash, it sends back an empty optional")
	public void serviceGetBlockEmptyWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);

		Consumer<Optional<Block>> onGetBlockResult = received -> {
			if (received.isEmpty())
				semaphore.release();
		};

		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mock(PublicNode.class);
		when(node.getBlock(hash)).thenReturn(Optional.empty());

		try (var service = PublicNodeServices.open(node, 8025);
			 var client = new TestClient(new URI("ws://localhost:8025"), null, onGetBlockResult, e -> {})) {

			client.sendGetBlock(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getBlock() request reaches the service and there is a block with the requested hash, it sends back that block")
	public void serviceGetBlockNonEmptyWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256(Function.identity());
		byte[] data = new byte[] { 1, 2, 3, 4, 5, 6 };
		byte[] value = new byte[] { 1, 2, 3, 4, 5, 6 };
		int scoopNumber = 42;
		var deadline = Deadlines.of(new byte[] { 13, 44, 17, 19 }, 43L, value, scoopNumber, data, shabal256);
		var block = Blocks.of(13L, 11L, 134L, BigInteger.valueOf(123), deadline, new byte[] { 5, 6, 7, 8 });

		Consumer<Optional<Block>> onGetBlockResult = received -> {
			if (block.equals(received.get()))
				semaphore.release();
		};

		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mock(PublicNode.class);
		when(node.getBlock(hash)).thenReturn(Optional.of(block));

		try (var service = PublicNodeServices.open(node, 8025);
			 var client = new TestClient(new URI("ws://localhost:8025"), null, onGetBlockResult, e -> {})) {

			client.sendGetBlock(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getBlock() request reaches the service and there is a block with the requested hash, but with an unknown hashing algorithm, it sends back an exception")
	public void serviceGetBlockUnknownHashingWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);

		Consumer<ExceptionResultMessage> onException = _received -> semaphore.release();

		var hash = new byte[] { 34, 32, 76, 11 };
		var node = mock(PublicNode.class);
		when(node.getBlock(hash)).thenThrow(NoSuchAlgorithmException.class);

		try (var service = PublicNodeServices.open(node, 8025);
			 var client = new TestClient(new URI("ws://localhost:8025"), null, block -> {}, onException)) {

			client.sendGetBlock(hash);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = NodeServiceTests.class.getClassLoader().getResource("logging.properties");
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
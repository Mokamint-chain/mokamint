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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.RestrictedNode;
import io.mokamint.node.service.RestrictedNodeServices;
import jakarta.websocket.DeploymentException;

public class RestrictedNodeServiceTests {
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

	@Test
	@DisplayName("if an addPeers() request reaches the service, it adds the peers to the node and it sends back a void result")
	public void serviceAddPeersWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException {
		var semaphore = new Semaphore(0);
		var peer1 = Peers.of(new URI("ws://my.machine:8032"));
		var peer2 = Peers.of(new URI("ws://her.machine:8033"));

		var node = new RestrictedNode() {

			@Override
			public void addPeers(Stream<Peer> received) throws TimeoutException, InterruptedException {
				var peers = received.collect(Collectors.toList());
				if (peers.size() == 2 && peers.contains(peer1) && peers.contains(peer2))
					semaphore.release();
			}

			@Override
			public void removePeers(Stream<Peer> peers) throws TimeoutException, InterruptedException {}

			@Override
			public void close() throws IOException, InterruptedException {}
		};

		class MyTestClient extends RestrictedTestClient {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI);
			}

			@Override
			protected void onAddPeersResult() {
				semaphore.release();
			}
		}

		try (var service = RestrictedNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendAddPeers(Stream.of(peer1, peer2));
			assertTrue(semaphore.tryAcquire(2, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a removePeers() request reaches the service, it removes the peers from the node and it sends back a void result")
	public void serviceRemovePeersWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException {
		var semaphore = new Semaphore(0);
		var peer1 = Peers.of(new URI("ws://my.machine:8032"));
		var peer2 = Peers.of(new URI("ws://her.machine:8033"));

		var node = new RestrictedNode() {

			@Override
			public void removePeers(Stream<Peer> received) throws TimeoutException, InterruptedException {
				var peers = received.collect(Collectors.toList());
				if (peers.size() == 2 && peers.contains(peer1) && peers.contains(peer2))
					semaphore.release();
			}

			@Override
			public void addPeers(Stream<Peer> peers) throws TimeoutException, InterruptedException {}

			@Override
			public void close() throws IOException, InterruptedException {}
		};

		class MyTestClient extends RestrictedTestClient {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI);
			}

			@Override
			protected void onRemovePeersResult() {
				semaphore.release();
			}
		}

		try (var service = RestrictedNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendRemovePeers(Stream.of(peer1, peer2));
			assertTrue(semaphore.tryAcquire(2, 1, TimeUnit.SECONDS));
		}
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = RestrictedNodeServiceTests.class.getClassLoader().getResource("logging.properties");
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
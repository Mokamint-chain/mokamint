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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.Peers;
import io.mokamint.node.RestrictedNodeInternals;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.remote.internal.RemoteRestrictedNodeImpl;
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

	private static class MyRestrictedNode implements RestrictedNodeInternals {
		private final Set<Peer> allPeers;
		private final Semaphore semaphore;

		private MyRestrictedNode(Set<Peer> allPeers, Semaphore semaphore) {
			this.allPeers = allPeers;
			this.semaphore = semaphore;
		}

		@Override
		public void addPeer(Peer received) throws TimeoutException, InterruptedException {
			if (allPeers.remove(received))
				semaphore.release();
		}

		@Override
		public void removePeer(Peer peer) {}

		@Override
		public void addOnClosedHandler(CloseHandler handler) {}

		@Override
		public void removeOnCloseHandler(CloseHandler handler) {}

		@Override
		public void close() {}
	}

	@Test
	@DisplayName("if an addPeers() request reaches the service, it adds the peers to the node and it sends back a void result")
	public void serviceAddPeersWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, ClosedNodeException {
		var semaphore = new Semaphore(0);
		var allPeers = new HashSet<Peer>();
		allPeers.add(Peers.of(new URI("ws://my.machine:8032")));
		allPeers.add(Peers.of(new URI("ws://her.machine:8033")));

		var node = new MyRestrictedNode(allPeers, semaphore);

		class MyTestClient extends RemoteRestrictedNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L);
			}

			@Override
			protected void onAddPeerResult() {
				semaphore.release();
			}

			private void sendAddPeer(Peer peer) throws ClosedNodeException {
				sendAddPeer(peer, "id");
			}
		}

		try (var service = RestrictedNodeServices.open(node, PORT); var client = new MyTestClient()) {
			for (var peer: allPeers)
				client.sendAddPeer(peer);

			assertTrue(semaphore.tryAcquire(2, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a removePeers() request reaches the service, it removes the peers from the node and it sends back a void result")
	public void serviceRemovePeersWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, ClosedNodeException {
		var semaphore = new Semaphore(0);
		var peer1 = Peers.of(new URI("ws://my.machine:8032"));
		var peer2 = Peers.of(new URI("ws://her.machine:8033"));
		Set<Peer> allPeers = new HashSet<>();
		allPeers.add(peer1);
		allPeers.add(peer2);

		var node = new MyRestrictedNode(allPeers, semaphore);

		class MyTestClient extends RemoteRestrictedNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L);
			}

			@Override
			protected void onRemovePeerResult() {
				semaphore.release();
			}

			private void sendRemovePeer(Peer peer) throws ClosedNodeException {
				sendRemovePeer(peer, "id");
			}
		}

		try (var service = RestrictedNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendRemovePeer(peer1);
			client.sendRemovePeer(peer2);
			assertTrue(semaphore.tryAcquire(2, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a restricted service gets closed, any remote using that service gets closed and its methods throw ClosedNodeException")
	public void ifServiceClosedThenRemoteClosedAndNotUsable() throws IOException, DatabaseException, InterruptedException, DeploymentException, URISyntaxException {
		var node = mock(RestrictedNodeInternals.class);
		var semaphore = new Semaphore(0);
		
		class MyRemoteRestrictedNode extends RemoteRestrictedNodeImpl {
			private MyRemoteRestrictedNode() throws DeploymentException, IOException, URISyntaxException {
				super(new URI("ws://localhost:8031"), 2000);
			}

			@Override
			public void close() throws IOException, InterruptedException {
				super.close();
				semaphore.release();
			}
		}

		try (var service = RestrictedNodeServices.open(node, 8031); var remote = new MyRemoteRestrictedNode()) {
			service.close(); // by closing the service, the remote is not usable anymore
			semaphore.tryAcquire(1, 1, TimeUnit.SECONDS);
			assertThrows(ClosedNodeException.class, () -> remote.addPeer(Peers.of(new URI("ws://www.mokamint.io:8031"))));
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
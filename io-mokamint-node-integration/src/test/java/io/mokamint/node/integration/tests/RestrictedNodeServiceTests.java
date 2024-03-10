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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.node.DatabaseException;
import io.mokamint.node.Peers;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.api.RestrictedNode;
import io.mokamint.node.remote.internal.RemoteRestrictedNodeImpl;
import io.mokamint.node.service.RestrictedNodeServices;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;

public class RestrictedNodeServiceTests extends AbstractLoggedTests {
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
	@DisplayName("if an add(Peer) request reaches the service, it adds the peers to the node and it sends back a result")
	public void serviceAddPeerWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, PeerRejectedException, DatabaseException, NodeException {
		var semaphore = new Semaphore(0);
		var allPeers = new HashSet<Peer>();
		allPeers.add(Peers.of(new URI("ws://my.machine:8032")));
		allPeers.add(Peers.of(new URI("ws://her.machine:8033")));

		var node = mock(RestrictedNode.class);
		when(node.add(any())).then(invocation -> {
			if (allPeers.remove(invocation.getArguments()[0]))
				semaphore.release();

			return true;
		});

		class MyTestClient extends RemoteRestrictedNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L);
			}

			private void sendAddPeer(Peer peer) throws NodeException {
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
	@DisplayName("if a remove(Peer) request reaches the service, it removes the peers from the node and it sends back a result")
	public void serviceRemovePeerWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, DatabaseException, NodeException {
		var semaphore = new Semaphore(0);
		var peer1 = Peers.of(new URI("ws://my.machine:8032"));
		var peer2 = Peers.of(new URI("ws://her.machine:8033"));
		Set<Peer> allPeers = new HashSet<>();
		allPeers.add(peer1);
		allPeers.add(peer2);

		var node = mock(RestrictedNode.class);
		when(node.remove(any())).then(invocation -> {
			if (allPeers.remove(invocation.getArguments()[0]))
				semaphore.release();

			return true;
		});

		class MyTestClient extends RemoteRestrictedNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L);
			}

			private void sendRemovePeer(Peer peer) throws NodeException {
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
	@DisplayName("if an openMiner() request reaches the service, it opens the miner and it sends back a result")
	public void serviceOpenMinerWorks() throws DeploymentException, IOException, InterruptedException, TimeoutException, NodeException {
		var semaphore = new Semaphore(0);
		var port1 = 8025;
		var port2 = 8028;
		Set<Integer> allPorts = new HashSet<>();
		allPorts.add(port1);
		allPorts.add(port2);

		var node = mock(RestrictedNode.class);
		when(node.openMiner(anyInt())).then(invocation -> {
			if (allPorts.remove(invocation.getArguments()[0]))
				semaphore.release();

			return true;
		});
		
		class MyTestClient extends RemoteRestrictedNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L);
			}

			private void sendOpenMiner(int port) throws NodeException {
				sendOpenMiner(port, "id");
			}
		}

		try (var service = RestrictedNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendOpenMiner(port1);
			client.sendOpenMiner(port2);
			assertTrue(semaphore.tryAcquire(2, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a closeMiner() request reaches the service, it closes the miner and it sends back a result")
	public void serviceCloseMinerWorks() throws DeploymentException, IOException, InterruptedException, TimeoutException, NodeException {
		var semaphore = new Semaphore(0);
		Set<UUID> allUUIDs = new HashSet<>();
		var uuid1 = UUID.randomUUID();
		var uuid2 = UUID.randomUUID();
		allUUIDs.add(uuid1);
		allUUIDs.add(uuid2);

		var node = mock(RestrictedNode.class);
		when(node.removeMiner(any())).then(invocation -> {
			if (allUUIDs.remove(invocation.getArguments()[0]))
				semaphore.release();

			return true;
		});
		
		class MyTestClient extends RemoteRestrictedNodeImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L);
			}

			private void sendCloseMiner(UUID uuid) throws NodeException {
				sendRemoveMiner(uuid, "id");
			}
		}

		try (var service = RestrictedNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendCloseMiner(uuid1);
			client.sendCloseMiner(uuid2);
			assertTrue(semaphore.tryAcquire(2, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a restricted service gets closed, any remote using that service gets closed and its methods throw ClosedNodeException")
	public void ifServiceClosedThenRemoteClosedAndNotUsable() throws IOException, DatabaseException, InterruptedException, DeploymentException, URISyntaxException, NodeException {
		var node = mock(RestrictedNode.class);
		var semaphore = new Semaphore(0);
		
		class MyRemoteRestrictedNode extends RemoteRestrictedNodeImpl {
			private MyRemoteRestrictedNode() throws DeploymentException, IOException, URISyntaxException {
				super(new URI("ws://localhost:8031"), 2000);
			}

			@Override
			protected void closeResources(CloseReason reason) throws NodeException, InterruptedException {
				super.closeResources(reason);
				semaphore.release();
			}
		}

		try (var service = RestrictedNodeServices.open(node, 8031); var remote = new MyRemoteRestrictedNode()) {
			service.close(); // by closing the service, the remote is not usable anymore
			semaphore.tryAcquire(1, 1, TimeUnit.SECONDS);
			assertThrows(NodeException.class, () -> remote.add(Peers.of(new URI("ws://www.mokamint.io:8031"))));
		}
	}
}
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

package io.mokamint.node.local.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.Infos;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.node.Peers;
import io.mokamint.node.Requests;
import io.mokamint.node.api.ApplicationTimeoutException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.Request;
import io.mokamint.node.local.AbstractLocalNode;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.service.PublicNodeServices;

/**
 * Tests about the propagation of the requests in a network of nodes.
 */
public class RequestsPropagationTests extends AbstractLoggedTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	/**
	 * The key of the node.
	 */
	private static KeyPair nodeKey;

	@BeforeAll
	public static void beforeAll() throws Exception {
		app = mock(Application.class);
		when(app.checkDeadline(any())).thenReturn(true);
		doNothing().when(app).checkRequest(any());
		when(app.getPriority(any())).thenReturn(42L);
		nodeKey = SignatureAlgorithms.ed25519().getKeyPair();
		var info = Infos.of("name", "description");
		when(app.getInfo()).thenReturn(info);
	}

	@Test
	@DisplayName("if a peer adds another peer, then requests flow from one to the other")
	public void ifPeerAddsPeerThenRequestsFlowBetweenThem(@TempDir Path chain1, @TempDir Path chain2) throws Exception {
		var port1 = 8032;
		var port2 = 8034;
		var uri1 = URI.create("ws://localhost:" + port1);
		var uri2 = URI.create("ws://localhost:" + port2);
		var peer1 = Peers.of(uri1);
		var peer2 = Peers.of(uri2);
		var config1 = LocalNodeConfigBuilders.defaults().setDir(chain1).build();
		var config2 = LocalNodeConfigBuilders.defaults().setDir(chain2).build();
		var peersSemaphore = new Semaphore(0);
		var requestsSemaphore = new Semaphore(0);
		var req1 = Requests.of(new byte[] { 1, 2, 3, 4 });
		var req2 = Requests.of(new byte[] { 5, 6, 7, 8, 9 });

		class MyLocalNode extends AbstractLocalNode {
			private final Peer expectedPeer;
			private final Request expectedRequest;

			private MyLocalNode(LocalNodeConfig config, Peer expectedPeer, Request expectedRequest) throws InterruptedException, ClosedApplicationException, ApplicationTimeoutException {
				super(config, nodeKey, app, false);
				
				this.expectedPeer = expectedPeer;
				this.expectedRequest = expectedRequest;
			}

			@Override
			protected void onAdded(Peer peer) {
				super.onAdded(peer);
				if (expectedPeer.equals(peer))
					peersSemaphore.release();
			}

			@Override
			protected void onAdded(Request request) {
				super.onAdded(request);
				if (expectedRequest.equals(request))
					requestsSemaphore.release();
			}
		}

		try (var node1 = new MyLocalNode(config1, peer2, req2); var node2 = new MyLocalNode(config2, peer1, req1);
			 var service1 = PublicNodeServices.open(node1, port1, 100, 1000, Optional.of(uri1));
			 var service2 = PublicNodeServices.open(node2, port2, 100, 1000, Optional.of(uri2))) {

			node1.add(peer2);

			// we wait until the two peers know each other
			assertTrue(peersSemaphore.tryAcquire(2, 4, TimeUnit.SECONDS));

			// we send a first request to peer1
			node1.add(req1);

			// we send the second request to peer2
			node2.add(req2);

			// we wait until both requests are propagated to the other peer
			assertTrue(requestsSemaphore.tryAcquire(2, 3, TimeUnit.SECONDS));
		}
	}
}
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
import io.mokamint.application.api.Application;
import io.mokamint.node.Peers;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.local.AbstractLocalNode;
import io.mokamint.node.local.ApplicationTimeoutException;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.service.PublicNodeServices;

/**
 * Tests about the propagation of the transactions in a network of nodes.
 */
public class TransactionsPropagationTests extends AbstractLoggedTests {

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
		when(app.checkPrologExtra(any())).thenReturn(true);
		doNothing().when(app).checkTransaction(any());
		when(app.getPriority(any())).thenReturn(42L);
		nodeKey = SignatureAlgorithms.ed25519().getKeyPair();
	}

	@Test
	@DisplayName("if a peer adds another peer, then transactions flow from one to the other")
	public void ifPeerAddsPeerThenTransactionsFlowBetweenThem(@TempDir Path chain1, @TempDir Path chain2) throws Exception {
		var port1 = 8032;
		var port2 = 8034;
		var uri1 = URI.create("ws://localhost:" + port1);
		var uri2 = URI.create("ws://localhost:" + port2);
		var peer1 = Peers.of(uri1);
		var peer2 = Peers.of(uri2);
		var config1 = LocalNodeConfigBuilders.defaults().setDir(chain1).build();
		var config2 = LocalNodeConfigBuilders.defaults().setDir(chain2).build();
		var peersSemaphore = new Semaphore(0);
		var transactionsSemaphore = new Semaphore(0);
		var transaction1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
		var transaction2 = Transactions.of(new byte[] { 5, 6, 7, 8, 9 });

		class MyLocalNode extends AbstractLocalNode {
			private final Peer expectedPeer;
			private final Transaction expectedTransaction;

			private MyLocalNode(LocalNodeConfig config, Peer expectedPeer, Transaction expectedTransaction) throws InterruptedException, NodeException, ApplicationTimeoutException {
				super(config, nodeKey, app, false);
				
				this.expectedPeer = expectedPeer;
				this.expectedTransaction = expectedTransaction;
			}

			@Override
			protected void onAdded(Peer peer) {
				super.onAdded(peer);
				if (expectedPeer.equals(peer))
					peersSemaphore.release();
			}

			@Override
			protected void onAdded(Transaction transaction) {
				super.onAdded(transaction);
				if (expectedTransaction.equals(transaction))
					transactionsSemaphore.release();
			}
		}

		try (var node1 = new MyLocalNode(config1, peer2, transaction2); var node2 = new MyLocalNode(config2, peer1, transaction1);
			 var service1 = PublicNodeServices.open(node1, port1, 100, 1000, Optional.of(uri1));
			 var service2 = PublicNodeServices.open(node2, port2, 100, 1000, Optional.of(uri2))) {

			node1.add(peer2);

			// we wait until the two peers know each other
			assertTrue(peersSemaphore.tryAcquire(2, 4, TimeUnit.SECONDS));

			// we send a first transaction to peer1
			node1.add(transaction1);

			// we send the second transaction to peer2
			node2.add(transaction2);

			// we wait until both transactions are propagated to the other peer
			assertTrue(transactionsSemaphore.tryAcquire(2, 3, TimeUnit.SECONDS));
		}
	}
}
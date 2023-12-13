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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.Application;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.nonce.Prologs;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.PlotsAndKeyPairs;
import io.mokamint.plotter.api.Plot;

/**
 * Tests about the inclusion of transactions in blockchain.
 */
public class TransactionsInclusionTests extends AbstractLoggedTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	/**
	 * The plot used by the mining node.
	 */
	private static Plot plot;

	/**
	 * The keys if the node.
	 */
	private static KeyPair nodeKeys;

	/**
	 * The keys of the plot file.
	 */
	private static KeyPair plotKeys;

	@BeforeAll
	public static void beforeAll(@TempDir Path plotDir) throws IOException, NoSuchAlgorithmException, InvalidKeyException, RejectedTransactionException {
		app = mock(Application.class);
		when(app.checkPrologExtra(any())).thenReturn(true);
		when(app.getInitialStateHash()).thenReturn(new byte[] { 1, 2, 3 });
		when(app.checkTransaction(any())).thenReturn(true);
		when(app.deliverTransaction(any(), anyInt(), any())).thenReturn(new byte[] { 13, 17, 42 });
		var ed25519 = SignatureAlgorithms.ed25519();
		nodeKeys = ed25519.getKeyPair();
		plotKeys = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeys.getPublic(), ed25519, plotKeys.getPublic(), new byte[0]);
		long start = 65536L;
		long length = 50L;

		plot = Plots.create(plotDir.resolve("plot.plot"), prolog, start, length, HashingAlgorithms.shabal256(), __ -> {});
	}

	@AfterAll
	public static void afterAll() throws IOException, InterruptedException {
		plot.close();
	}

	private LocalNodeConfig mkConfig(Path chainDir) throws NoSuchAlgorithmException {
		return LocalNodeConfigBuilders.defaults()
			.setDir(chainDir)
			.setChainId("octopus")
			.setTargetBlockCreationTime(300L)
			.setInitialAcceleration(1000000000000000000L)
			.build();
	}

	@Test
	@Timeout(5000)
	@DisplayName("transactions added to the mempool get eventually added to the blockchain")
	public void transactionsAddedToMempoolEventuallyReachBlockchain(@TempDir Path chain) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, InterruptedException, DatabaseException, IOException, AlreadyInitializedException, RejectedTransactionException, ClosedNodeException {
		var allTransactions = new HashSet<Transaction>();
		var random = new Random();
		while (allTransactions.size() < 100) {
			int length = random.nextInt(10, 1000);
			byte[] bytes = new byte[length];
			random.nextBytes(bytes);
			var tx = Transactions.of(bytes);
			allTransactions.add(tx);
		}
		var allIncluded = new Semaphore(0);
		var config = mkConfig(chain);

		class MiningNode extends LocalNodeImpl {

			private MiningNode(LocalNodeConfig config) throws IOException, DatabaseException, InterruptedException, AlreadyInitializedException, InvalidKeyException, SignatureException {
				super(config, nodeKeys, app, true);

				try {
					add(LocalMiners.of(PlotsAndKeyPairs.of(plot, plotKeys)));
				}
				catch (ClosedNodeException e) {
					// impossible, the node is not closed at this stage!
				}
			}

			@Override
			protected void onBlockAdded(Block block) {
				super.onBlockAdded(block);
				block.getTransactions().forEach(allTransactions::remove);
				if (allTransactions.isEmpty())
					allIncluded.release();
			}
		}

		var copy = new ArrayList<>(allTransactions);
		try (var miningNode = new MiningNode(config)) {
			for (var tx: copy) {
				miningNode.add(tx); // allTransactions has no repetitions => no rejection
				Thread.sleep(10);
			}

			allIncluded.acquire();
		}
	}
}